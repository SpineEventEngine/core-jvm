/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package io.spine.server.integration

import io.grpc.stub.StreamObserver
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.optional.shouldBePresent
import io.spine.base.Time.currentTime
import io.spine.client.Filters.gt
import io.spine.client.Subscription
import io.spine.client.SubscriptionUpdate
import io.spine.client.Topic
import io.spine.core.actorContext
import io.spine.core.tenantId
import io.spine.grpc.MemoizingObserver
import io.spine.grpc.StreamObservers.memoizingObserver
import io.spine.protobuf.AnyPacker.unpack
import io.spine.server.Application
import io.spine.server.BoundedContextBuilder
import io.spine.server.SubscriptionService
import io.spine.server.application
import io.spine.server.integration.given.DocumentRepository
import io.spine.server.multiTenantContext
import io.spine.server.serverEnvironment
import io.spine.server.singleTenantContext
import io.spine.server.tenant.TenantAwareRunner
import io.spine.testing.client.TestActorRequestFactory
import io.spine.testing.core.given.GivenUserId
import io.spine.testing.logging.mute.MuteLogging
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`ThirdPartyContext` should pass the events it emits to client subscriptions")
internal class ThirdPartyContextSubscriptionSpec {

    private val user = GivenUserId.newUuid()
    private val withoutTenant = TestActorRequestFactory(javaClass)
    private val acme = tenantId { value = "Acme" }
    private val cyberdyne = tenantId { value = "Cyberdyne" }

    /**
     * The applications built by the current test, closed after it.
     */
    private val built = mutableListOf<Application>()

    @AfterEach
    fun closeApplications() {
        built.forEach(Application::closeIfOpen)
        built.clear()
    }

    @Test
    fun `once, while another context reacts to them`() {
        val documents = DocumentRepository()
        val imports = ThirdPartyContext.singleTenant(IMPORTS)
        val app = newApplication(imports, singleTenantContext(DOCUMENTS) { add(documents) })
        val updates = activate(SubscriptionService.of(app), uploads(withoutTenant))
        val event = uploaded()

        imports.emittedEvent(event, user)

        documents.find(event.id).shouldBePresent()
        updates.error.shouldBeNull()
        updates.uploads() shouldContainExactly listOf(event)
    }

    @Test
    fun `when no other context takes them`() {
        val imports = ThirdPartyContext.singleTenant(IMPORTS)
        val app = newApplication(imports, singleTenantContext(DOCUMENTS) {})
        val updates = activate(SubscriptionService.of(app), uploads(withoutTenant))
        val event = uploaded()

        imports.emittedEvent(event, user)

        updates.error.shouldBeNull()
        updates.uploads() shouldContainExactly listOf(event)
    }

    @Test
    fun `of the tenant of each event, if multi-tenant`() {
        val documents = DocumentRepository()
        val imports = ThirdPartyContext.multiTenant(IMPORTS)
        val app = newApplication(imports, multiTenantContext(DOCUMENTS) { add(documents) })
        app.tenantIndex().keep(acme)
        app.tenantIndex().keep(cyberdyne)
        val service = SubscriptionService.of(app)
        val ofAcme = activate(service, uploads(TestActorRequestFactory(javaClass, acme)))
        val ofCyberdyne = activate(service, uploads(TestActorRequestFactory(javaClass, cyberdyne)))
        val event = uploaded()

        imports.emittedEvent(event, actorContext {
            actor = user
            tenantId = acme
            timestamp = currentTime()
        })

        TenantAwareRunner.with(acme)
            .evaluate { documents.find(event.id) }
            .shouldBePresent()
        ofAcme.error.shouldBeNull()
        ofAcme.uploads() shouldContainExactly listOf(event)
        ofCyberdyne.error.shouldBeNull()
        ofCyberdyne.responses().shouldBeEmpty()
    }

    @Test
    @MuteLogging
    fun `without failing the emission if a subscriber fails`() {
        val documents = DocumentRepository()
        val imports = ThirdPartyContext.singleTenant(IMPORTS)
        val app = newApplication(imports, singleTenantContext(DOCUMENTS) { add(documents) })
        val service = SubscriptionService.of(app)
        service.activate(subscribe(service, uploads(withoutTenant)), FailingSubscriber())
        val event = uploaded()

        shouldNotThrowAny {
            imports.emittedEvent(event, user)
        }
        documents.find(event.id).shouldBePresent()
    }

    @Test
    @MuteLogging
    fun `without failing the emission if a filter of a subscription fails`() {
        val documents = DocumentRepository()
        val imports = ThirdPartyContext.singleTenant(IMPORTS)
        val app = newApplication(imports, singleTenantContext(DOCUMENTS) { add(documents) })
        val service = SubscriptionService.of(app)
        val unmatchable = withoutTenant.topic()
            .select(OpenOfficeDocumentUploaded::class.java)
            .where(gt("text", 42))
            .build()
        service.activate(subscribe(service, unmatchable), memoizingObserver())
        val updates = activate(service, uploads(withoutTenant))
        val event = uploaded()

        shouldNotThrowAny {
            imports.emittedEvent(event, user)
        }
        documents.find(event.id).shouldBePresent()
        updates.uploads() shouldContainExactly listOf(event)
    }

    /**
     * Builds an application of the given third-party context and the context of the given
     * builder. The application is closed after the test.
     */
    private fun newApplication(
        imports: ThirdPartyContext,
        context: BoundedContextBuilder
    ): Application =
        application {
            serverEnvironment = serverEnvironment {}
            add(context)
            add(imports)
        }.also(built::add)

    private fun subscribe(service: SubscriptionService, topic: Topic): Subscription {
        val subscribed = memoizingObserver<Subscription>()
        service.subscribe(topic, subscribed)
        subscribed.error.shouldBeNull()
        return subscribed.firstResponse()
    }

    /**
     * Subscribes to the given topic and activates the subscription.
     *
     * @return The observer of the subscription updates.
     */
    private fun activate(
        service: SubscriptionService,
        topic: Topic
    ): MemoizingObserver<SubscriptionUpdate> {
        val updates = memoizingObserver<SubscriptionUpdate>()
        service.activate(subscribe(service, topic), updates)
        return updates
    }

    /**
     * A subscriber that fails on each update.
     */
    private class FailingSubscriber : StreamObserver<SubscriptionUpdate> {

        override fun onNext(value: SubscriptionUpdate): Unit =
            error("The subscriber fails to take an update.")

        override fun onError(t: Throwable): Unit = Unit

        override fun onCompleted(): Unit = Unit
    }

    private companion object {

        const val IMPORTS = "ThirdPartySubscriptionImports"
        const val DOCUMENTS = "ThirdPartySubscriptionDocuments"

        fun uploads(requests: TestActorRequestFactory): Topic =
            requests.topic().allOf(OpenOfficeDocumentUploaded::class.java)

        fun uploaded(): OpenOfficeDocumentUploaded =
            openOfficeDocumentUploaded {
                id = DocumentId.generate()
                text = "A document uploaded to a third-party system"
            }

        /**
         * Obtains the uploads delivered to the subscriber.
         */
        fun MemoizingObserver<SubscriptionUpdate>.uploads(): List<OpenOfficeDocumentUploaded> =
            responses()
                .flatMap { it.eventUpdates.eventList }
                .map { unpack(it.message, OpenOfficeDocumentUploaded::class.java) }
    }
}
