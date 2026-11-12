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
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.spine.base.Identifier.newUuid
import io.spine.client.Subscription
import io.spine.client.SubscriptionUpdate
import io.spine.client.Topic
import io.spine.client.copy
import io.spine.core.ErrorCase.UNSUPPORTED_REQUEST
import io.spine.core.Event
import io.spine.core.Response
import io.spine.core.tenantId
import io.spine.grpc.MemoizingObserver
import io.spine.grpc.StreamObservers.memoizingObserver
import io.spine.server.entity.Repository
import io.spine.server.given.errorCase
import io.spine.server.tenant.TenantAwareRunner
import io.spine.test.client.tasks.cTaskId
import io.spine.test.client.tasks.event.CTaskCreated
import io.spine.test.client.tasks.event.cTaskCreated
import io.spine.testing.client.TestActorRequestFactory
import io.spine.testing.logging.mute.MuteLogging
import io.spine.testing.server.TestEventFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Supplements `SubscriptionServiceTest` with the specs of a subscription to a topic whose
 * target type no Bounded Context serves.
 *
 * The service creates such a subscription in each context it knows. A context added to
 * several services may therefore have a subscription that the other contexts of a service
 * do not have.
 */
@MuteLogging
@DisplayName("`SubscriptionService`, for a topic that no context serves, should")
internal class SubscriptionServiceUnservedTopicSpec {

    /**
     * The contexts built by the current test.
     *
     * They are closed after each test, so that no context left open serves their entity types
     * in the tests that run later.
     */
    private val built = mutableListOf<BoundedContext>()

    @AfterEach
    fun closeContexts() {
        built.forEach(BoundedContext::closeIfOpen)
        built.clear()
    }

    @Nested inner class
    `with single-tenant contexts` {

        private val requests = TestActorRequestFactory(javaClass)

        /**
         * The context shared by two services.
         */
        private lateinit var shared: BoundedContext

        /**
         * The service with the shared context and another one.
         */
        private lateinit var both: SubscriptionService

        /**
         * The subscription created through the service with only the shared context.
         */
        private lateinit var subscription: Subscription

        @BeforeEach
        fun subscribeInSharedContextOnly() {
            shared = newContext(Given.ProjectAggregateRepository())
            both = SubscriptionService.newBuilder()
                .add(shared)
                .add(newContext(Given.CustomerAggregateRepository()))
                .build()
            val sharedOnly = SubscriptionService.withSingle(shared)
            subscription = subscribe(sharedOnly, taskEvents(requests))
        }

        @Test
        fun `activate a subscription in the contexts that have it`() {
            val updates = activate(both, subscription)
            val event = taskCreated(requests)

            shared.eventBus().post(event)

            updates.error shouldBe null
            updates.isCompleted shouldBe false
            updates.events() shouldContainExactly listOf(event)
        }

        @Test
        fun `cancel a subscription in the contexts that have it`() {
            val cancellation = cancel(both, subscription)

            cancellation.shouldBeAcknowledged()
            shared.stand().hasSubscription(subscription) shouldBe false
        }
    }

    @Nested inner class
    `with a multitenant context` {

        private val tenant = tenantId { value = "Tenant of the topic" }
        private val requests = TestActorRequestFactory(javaClass, tenant)
        private lateinit var context: BoundedContext
        private lateinit var service: SubscriptionService
        private lateinit var subscription: Subscription

        @BeforeEach
        fun createSubscription() {
            context = newContext(Given.ProjectAggregateRepository(), multitenant = true)
            service = SubscriptionService.withSingle(context)
            subscription = subscribe(service, taskEvents(requests))
        }

        @Test
        fun `activate a subscription for the tenant of its topic`() {
            val updates = activate(service, subscription)
            val event = taskCreated(requests)
            val post = Runnable { context.eventBus().post(event) }

            TenantAwareRunner.with(tenant).run(post)

            updates.error shouldBe null
            updates.isCompleted shouldBe false
            updates.events() shouldContainExactly listOf(event)
        }

        @Test
        fun `cancel a subscription for the tenant of its topic`() {
            val cancellation = cancel(service, subscription)

            cancellation.shouldBeAcknowledged()
            context.stand().hasSubscription(subscription) shouldBe false
        }

        @Test
        fun `refuse a subscription that only another tenant has`() {
            val another = TestActorRequestFactory(javaClass, tenantId { value = "Another tenant" })
            val ofAnotherTenant = subscription.copy { topic = taskEvents(another) }

            val updates = activate(service, ofAnotherTenant)

            updates.responses().shouldBeEmpty()
            updates.error.shouldNotBeNull().errorCase() shouldBe UNSUPPORTED_REQUEST
        }
    }

    /**
     * Creates a context with the given repository.
     *
     * The service knows only the contexts that expose a type, so each context has
     * a repository of its own.
     */
    private fun newContext(
        repository: Repository<*, *>,
        multitenant: Boolean = false
    ): BoundedContext =
        BoundedContextBuilder.assumingTests(multitenant)
            .add(repository)
            .build()
            .also(built::add)

    private fun taskEvents(requests: TestActorRequestFactory): Topic =
        requests.topic().allOf(CTaskCreated::class.java)

    private fun taskCreated(requests: TestActorRequestFactory): Event =
        TestEventFactory.newInstance(requests).createEvent(
            cTaskCreated {
                id = cTaskId { uuid = newUuid() }
                name = "A task of no context"
            }
        )

    private fun subscribe(service: SubscriptionService, topic: Topic): Subscription {
        val observer = memoizingObserver<Subscription>()
        service.subscribe(topic, observer)
        observer.error shouldBe null
        return observer.firstResponse()
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
        error shouldBe null
        responses().shouldBeSingleton { it.status.hasOk() shouldBe true }
        isCompleted shouldBe true
    }

    /**
     * Obtains the events delivered to the subscriber.
     */
    private fun MemoizingObserver<SubscriptionUpdate>.events(): List<Event> =
        responses().flatMap { it.eventUpdates.eventList }
}
