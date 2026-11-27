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

package io.spine.server

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.spine.base.CommandMessage
import io.spine.client.Subscription
import io.spine.client.SubscriptionUpdate
import io.spine.client.Subscriptions
import io.spine.client.Topic
import io.spine.core.Ack
import io.spine.core.ErrorCase.TENANT_ERROR
import io.spine.core.Event
import io.spine.core.Response
import io.spine.core.tenantId
import io.spine.grpc.MemoizingObserver
import io.spine.grpc.StreamObservers.memoizingObserver
import io.spine.protobuf.AnyPacker.unpack
import io.spine.server.aggregate.given.repo.RejectingRepository
import io.spine.server.given.ReportOnProjectCreated
import io.spine.server.given.RequestEventMonitor
import io.spine.server.given.UndeclaredRejector
import io.spine.server.given.errorCase
import io.spine.server.tenant.TenantAwareRunner
import io.spine.system.server.event.SubscriptionTenantNotExpected
import io.spine.system.server.event.TopicTenantNotExpected
import io.spine.system.server.event.TopicTenantNotSpecified
import io.spine.test.aggregate.ProjectId
import io.spine.test.aggregate.command.aggStartProjectWithChildren
import io.spine.test.aggregate.rejection.Rejections.AggCannotStartArchivedProject
import io.spine.test.client.tasks.event.CTaskCreated
import io.spine.test.subscriptionservice.ReportId
import io.spine.test.subscriptionservice.command.sendReport
import io.spine.test.subscriptionservice.event.ReportSent
import io.spine.test.subscriptionservice.event.reportSent
import io.spine.testing.client.TestActorRequestFactory
import io.spine.testing.logging.mute.MuteLogging
import io.spine.testing.server.TestEventFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Supplements `SubscriptionServiceTest` with the specs of a subscription to an event type
 * that several Bounded Contexts post.
 *
 * Each context that fits the tenancy of the topic holds the subscription, whichever contexts
 * declare the event type in their receptors.
 */
@MuteLogging
@DisplayName("`SubscriptionService`, for an event type that several contexts post, should")
internal class SubscriptionServiceEventFanOutSpec {

    private val tenant = tenantId { value = "Tenant of the fan-out topic" }
    private val withoutTenant = TestActorRequestFactory(javaClass)
    private val withTenant = TestActorRequestFactory(javaClass, tenant)

    /**
     * The applications built by the current test, closed after it.
     */
    private val built = mutableListOf<Application>()

    @AfterEach
    fun closeApplications() {
        built.forEach(Application::closeIfOpen)
        built.clear()
    }

    @Nested inner class
    `posted by two single-tenant contexts` {

        private lateinit var sending: BoundedContext
        private lateinit var reacting: BoundedContext
        private lateinit var service: SubscriptionService
        private lateinit var subscription: Subscription

        @BeforeEach
        fun createSubscription() {
            val app = newApplication(
                singleTenantContext(SENDING) { addAssignee(Given.ReportSender()) },
                singleTenantContext(REACTING) { addEventDispatcher(ReportOnProjectCreated()) }
            )
            sending = app.context(SENDING)
            reacting = app.context(REACTING)
            service = SubscriptionService.of(app)
            subscription = subscribe(service, reports(withoutTenant))
        }

        @Test
        fun `hold the subscription in both contexts`() {
            sending.stand().hasSubscription(subscription) shouldBe true
            reacting.stand().hasSubscription(subscription) shouldBe true
        }

        @Test
        fun `deliver the events of both contexts to one subscriber`() {
            val updates = activate(service, subscription)
            val fromSending = reportEvent(withoutTenant)
            val fromReacting = reportEvent(withoutTenant)

            sending.eventBus().post(fromSending)
            reacting.eventBus().post(fromReacting)

            updates.error.shouldBeNull()
            updates.isCompleted shouldBe false
            updates.events() shouldContainExactly listOf(fromSending, fromReacting)
        }

        @Test
        fun `cancel the subscription in both contexts`() {
            val cancellation = cancel(service, subscription)

            cancellation.shouldBeAcknowledged()
            sending.stand().hasSubscription(subscription) shouldBe false
            reacting.stand().hasSubscription(subscription) shouldBe false
        }
    }

    @Nested inner class
    `posted by a multi-tenant and a single-tenant context` {

        private lateinit var multiTenant: BoundedContext
        private lateinit var singleTenant: BoundedContext
        private lateinit var service: SubscriptionService

        /**
         * Builds the application, adding the single-tenant context last.
         *
         * Both contexts declare the event type, so the order of adding them must not decide
         * which of them serve a topic.
         */
        @BeforeEach
        fun createService() {
            val app = newApplication(
                multiTenantContext(SENDING) { addAssignee(Given.ReportSender()) },
                singleTenantContext(REACTING) { addEventDispatcher(ReportOnProjectCreated()) }
            )
            app.tenantIndex().keep(tenant)
            multiTenant = app.context(SENDING)
            singleTenant = app.context(REACTING)
            service = SubscriptionService.of(app)
        }

        @Test
        fun `serve a topic with a registered tenant in the multi-tenant context`() {
            val subscription = subscribe(service, reports(withTenant))
            val updates = activate(service, subscription)
            val ofTenant = reportEvent(withTenant)

            postForTenant(multiTenant, ofTenant)
            singleTenant.eventBus().post(reportEvent(withoutTenant))

            multiTenant.stand().hasSubscription(subscription) shouldBe true
            singleTenant.stand().hasSubscription(subscription) shouldBe false
            updates.error.shouldBeNull()
            updates.events() shouldContainExactly listOf(ofTenant)
        }

        @Test
        fun `serve a topic without a tenant in the single-tenant context`() {
            val subscription = subscribe(service, reports(withoutTenant))
            val updates = activate(service, subscription)
            val ofNoTenant = reportEvent(withoutTenant)

            postForTenant(multiTenant, reportEvent(withTenant))
            singleTenant.eventBus().post(ofNoTenant)

            multiTenant.stand().hasSubscription(subscription) shouldBe false
            singleTenant.stand().hasSubscription(subscription) shouldBe true
            updates.error.shouldBeNull()
            updates.events() shouldContainExactly listOf(ofNoTenant)
        }
    }

    @Test
    fun `deliver a rejection that one context declares, and another throws undeclared`() {
        val app = newApplication(
            singleTenantContext(DECLARING) { add(RejectingRepository()) },
            singleTenantContext(UNDECLARING) { addAssignee(UndeclaredRejector()) }
        )
        val service = SubscriptionService.of(app)
        val topic = withoutTenant.topic().allOf(AggCannotStartArchivedProject::class.java)
        val updates = activate(service, subscribe(service, topic))
        val project = ProjectId.generate()
        val report = ReportId.generate()

        post(app.context(DECLARING), aggStartProjectWithChildren { projectId = project })
        post(app.context(UNDECLARING), sendReport { id = report })

        updates.error.shouldBeNull()
        val rejected = updates.events()
            .map { unpack(it.message, AggCannotStartArchivedProject::class.java).projectId }
        rejected shouldContainExactlyInAnyOrder listOf(
            project, UndeclaredRejector.projectOf(report)
        )
    }

    @Nested inner class
    `refuse a topic that fits no context` {

        private val monitor = RequestEventMonitor()

        @Test
        fun `with a tenant, if every context is single-tenant`() {
            val app = newApplication(singleTenantContext(SENDING) { addEventDispatcher(monitor) })
            val topic = tasks(withTenant)

            subscribeFailing(SubscriptionService.of(app), topic).errorCase() shouldBe TENANT_ERROR

            val misfit = monitor.single<TopicTenantNotExpected>()
            misfit.id shouldBe topic.id
            misfit.tenant shouldBe tenant
        }

        @Test
        fun `without a tenant, if every context is multi-tenant`() {
            val app = newApplication(multiTenantContext(SENDING) { addEventDispatcher(monitor) })
            val topic = tasks(withoutTenant)

            subscribeFailing(SubscriptionService.of(app), topic).errorCase() shouldBe TENANT_ERROR

            monitor.single<TopicTenantNotSpecified>().id shouldBe topic.id
        }
    }

    @Nested inner class
    `refuse a subscription that fits no context` {

        private val monitor = RequestEventMonitor()
        private lateinit var service: SubscriptionService
        private lateinit var subscription: Subscription

        @BeforeEach
        fun createService() {
            val app = newApplication(singleTenantContext(SENDING) { addEventDispatcher(monitor) })
            service = SubscriptionService.of(app)
            subscription = Subscriptions.from(tasks(withTenant))
        }

        @Test
        fun `when activating`() {
            val updates = activate(service, subscription)

            updates.responses().shouldBeEmpty()
            updates.error.shouldNotBeNull().errorCase() shouldBe TENANT_ERROR
            monitor.single<SubscriptionTenantNotExpected>().id shouldBe subscription.id
        }

        @Test
        fun `when cancelling`() {
            val cancellation = cancel(service, subscription)

            cancellation.responses().shouldBeEmpty()
            cancellation.error.shouldNotBeNull().errorCase() shouldBe TENANT_ERROR
            monitor.single<SubscriptionTenantNotExpected>().id shouldBe subscription.id
        }
    }

    /**
     * Builds an application of the given contexts, which is closed after the test.
     */
    private fun newApplication(vararg builders: BoundedContextBuilder): Application =
        application {
            serverEnvironment = serverEnvironment {}
            builders.forEach { add(it) }
        }.also(built::add)

    private fun reports(requests: TestActorRequestFactory): Topic =
        requests.topic().allOf(ReportSent::class.java)

    /**
     * Creates a topic of an event type that no context declares.
     */
    private fun tasks(requests: TestActorRequestFactory): Topic =
        requests.topic().allOf(CTaskCreated::class.java)

    /**
     * Creates the event of a sent report, as posted by the actor of the given requests.
     */
    private fun reportEvent(requests: TestActorRequestFactory): Event =
        TestEventFactory.newInstance(requests)
            .createEvent(reportSent { id = ReportId.generate() })

    /**
     * Posts the event to the given multi-tenant context, for the tenant of the topics.
     */
    private fun postForTenant(context: BoundedContext, event: Event) {
        val posting = Runnable { context.eventBus().post(event) }
        TenantAwareRunner.with(tenant).run(posting)
    }

    /**
     * Posts the command to the given context, checking that the context accepts it.
     */
    private fun post(context: BoundedContext, message: CommandMessage) {
        val observer = memoizingObserver<Ack>()
        context.commandBus().post(withoutTenant.createCommand(message), observer)
        observer.firstResponse().status.hasOk() shouldBe true
    }

    private fun subscribe(service: SubscriptionService, topic: Topic): Subscription {
        val observer = memoizingObserver<Subscription>()
        service.subscribe(topic, observer)
        observer.error.shouldBeNull()
        return observer.firstResponse()
    }

    /**
     * Subscribes to the given topic, expecting the call to fail.
     *
     * @return The error of the call.
     */
    private fun subscribeFailing(service: SubscriptionService, topic: Topic): Throwable {
        val observer = memoizingObserver<Subscription>()
        service.subscribe(topic, observer)
        observer.responses().shouldBeEmpty()
        return observer.error.shouldNotBeNull()
    }

    private fun activate(
        service: SubscriptionService,
        subscription: Subscription
    ): MemoizingObserver<SubscriptionUpdate> {
        val observer = memoizingObserver<SubscriptionUpdate>()
        service.activate(subscription, observer)
        return observer
    }

    private fun cancel(
        service: SubscriptionService,
        subscription: Subscription
    ): MemoizingObserver<Response> {
        val observer = memoizingObserver<Response>()
        service.cancel(subscription, observer)
        return observer
    }

    /**
     * Asserts that the call was acknowledged once, and completed.
     */
    private fun MemoizingObserver<Response>.shouldBeAcknowledged() {
        error.shouldBeNull()
        responses().shouldBeSingleton { it.status.hasOk() shouldBe true }
        isCompleted shouldBe true
    }

    /**
     * Obtains the events delivered to the subscriber.
     */
    private fun MemoizingObserver<SubscriptionUpdate>.events(): List<Event> =
        responses().flatMap { it.eventUpdates.eventList }

    private companion object {

        const val SENDING = "EventFanOutSending"
        const val REACTING = "EventFanOutReacting"
        const val DECLARING = "EventFanOutDeclaring"
        const val UNDECLARING = "EventFanOutUndeclaring"
    }
}

/**
 * Obtains the context of the application with the given name.
 */
private fun Application.context(name: String): BoundedContext =
    contexts().single { it.name().value == name }
