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
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.spine.base.CommandMessage
import io.spine.client.Subscription
import io.spine.client.SubscriptionUpdate
import io.spine.client.Target
import io.spine.client.Targets.allOf
import io.spine.client.Targets.someOf
import io.spine.client.Topic
import io.spine.core.Ack
import io.spine.core.Command
import io.spine.core.ErrorCase.INVALID_REQUEST
import io.spine.core.ErrorCase.TENANT_ERROR
import io.spine.core.ErrorCase.UNSUPPORTED_REQUEST
import io.spine.core.Response
import io.spine.grpc.MemoizingObserver
import io.spine.grpc.StreamObservers.memoizingObserver
import io.spine.grpc.StreamObservers.noOpObserver
import io.spine.protobuf.AnyPacker.unpack
import io.spine.server.bc.given.SecretProjectRepository
import io.spine.server.given.RequestEventMonitor
import io.spine.server.given.errorCase
import io.spine.system.server.HandlerFailedUnexpectedly
import io.spine.system.server.event.UnknownSubscriptionActivationRequested
import io.spine.system.server.event.UnsupportedTopicReceived
import io.spine.test.aggregate.AggProject
import io.spine.test.aggregate.ProjectId
import io.spine.test.aggregate.event.AggProjectCreated
import io.spine.test.bc.SecretProject
import io.spine.test.client.tasks.CTask
import io.spine.test.client.tasks.event.CTaskCreated
import io.spine.test.commandservice.customer.Customer
import io.spine.test.commandservice.customer.command.CreateCustomer
import io.spine.testing.client.TestActorRequestFactory
import io.spine.testing.logging.mute.MuteLogging
import io.spine.testing.server.testApplication
import io.spine.type.TypeUrl
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Supplements `SubscriptionServiceTest` with the specs of a subscription to a topic with
 * several targets, which may be served by different Bounded Contexts.
 */
@MuteLogging
@DisplayName("`SubscriptionService`, for a topic with several targets, should")
internal class SubscriptionServiceMultiTargetSpec {

    private val requests = TestActorRequestFactory(javaClass)
    private val projectsMonitor = RequestEventMonitor()
    private val customersMonitor = RequestEventMonitor()
    private lateinit var application: Application

    /**
     * The context that serves the events of projects.
     */
    private lateinit var projects: BoundedContext

    /**
     * The context that serves the states of customers, and does not expose secret projects.
     */
    private lateinit var customers: BoundedContext
    private lateinit var service: SubscriptionService

    @BeforeEach
    fun createService() {
        application = testApplication {
            add(
                BoundedContextBuilder.assumingTests()
                    .add(Given.ProjectAggregateRepository())
                    .addEventDispatcher(projectsMonitor)
            )
            add(
                BoundedContextBuilder.assumingTests()
                    .add(Given.CustomerAggregateRepository())
                    .add(SecretProjectRepository())
                    .addEventDispatcher(customersMonitor)
            )
        }
        val contexts = application.contexts()
        projects = contexts.single { it.hasEntitiesWithState(PROJECT_STATE) }
        customers = contexts.single { it.hasEntitiesWithState(CUSTOMER_STATE) }
        service = SubscriptionService.of(application)
    }

    @AfterEach
    fun closeApplication() {
        application.close()
    }

    @Nested inner class
    `served by different contexts` {

        private lateinit var subscription: Subscription

        @BeforeEach
        fun subscribe() {
            subscription = subscribed(topicOf(allOf(PROJECT_CREATED), allOf(CUSTOMER)))
        }

        @Test
        fun `create one subscription in each of the contexts`() {
            projects.stand().hasSubscription(subscription) shouldBe true
            customers.stand().hasSubscription(subscription) shouldBe true
        }

        @Test
        fun `stream the updates of all the targets in one activation`() {
            val updates = activate(subscription)

            post(projects, createProject())
            post(customers, createCustomer())

            updates.error shouldBe null
            updates.isCompleted shouldBe false
            updates.responses().map { it.targetIndex } shouldContainExactlyInAnyOrder listOf(0, 1)
            updates.forTarget(0).shouldBeSingleton {
                it.eventUpdates.eventCount shouldBe 1
            }
            updates.forTarget(1).shouldBeSingleton {
                it.entityUpdates.getUpdate(0).state.typeUrl shouldBe CUSTOMER_STATE.value()
            }
        }

        @Test
        fun `cancel the subscription in all the contexts at once`() {
            val cancellation = cancel(subscription)

            cancellation.error shouldBe null
            cancellation.responses().shouldBeSingleton { it.status.hasOk() shouldBe true }
            cancellation.isCompleted shouldBe true
            projects.stand().hasSubscription(subscription) shouldBe false
            customers.stand().hasSubscription(subscription) shouldBe false
        }

        @Test
        fun `refuse to activate a subscription that a serving context lost, recording it there`() {
            customers.stand().cancel(subscription, noOpObserver())

            val updates = activate(subscription)

            updates.responses().shouldBeEmpty()
            updates.error.shouldNotBeNull().errorCase() shouldBe UNSUPPORTED_REQUEST
            customersMonitor.single<UnknownSubscriptionActivationRequested>()
                .id shouldBe subscription.id
            projectsMonitor.events.shouldBeEmpty()
        }
    }

    @Test
    fun `deliver a message that matches two targets of one type once for each of them`() {
        val command = createCustomer()
        val customerId = (unpack(command.message) as CreateCustomer).customerId
        val subscription = subscribed(
            topicOf(allOf(CUSTOMER), someOf(CUSTOMER, setOf(customerId)))
        )
        val updates = activate(subscription)

        post(customers, command)
        post(customers, createCustomer())

        updates.error shouldBe null
        updates.forTarget(0).size shouldBe 2
        updates.forTarget(1).shouldBeSingleton {
            unpack(it.entityUpdates.getUpdate(0).state, Customer::class.java)
                .id shouldBe customerId
        }
    }

    @Test
    fun `hold a subscription to an event type that no context serves in every context`() {
        val subscription = subscribed(topicOf(allOf(CTaskCreated::class.java), allOf(CUSTOMER)))

        projects.stand().hasSubscription(subscription) shouldBe true
        customers.stand().hasSubscription(subscription) shouldBe true
    }

    @Nested inner class
    `refuse a topic` {

        @Test
        fun `that a serving context does not allow, recording the type there`() {
            val topic = topicOf(allOf(PROJECT_CREATED), allOf(SecretProject::class.java))

            subscribe(topic).errorCase() shouldBe UNSUPPORTED_REQUEST

            val unsupported = customersMonitor.single<UnsupportedTopicReceived>()
            unsupported.id shouldBe topic.id
            unsupported.typeUrl shouldBe TypeUrl.of(SecretProject::class.java).value()
            projectsMonitor.events.shouldBeEmpty()
        }

        @Test
        fun `with no targets, recording nothing`() {
            val topic = topicOf(allOf(CUSTOMER)).toBuilder()
                .clearTarget()
                .buildPartial()

            subscribe(topic).errorCase() shouldBe INVALID_REQUEST

            nothingRecorded()
        }

        @Test
        fun `with an internal type in one target, recording nothing`() {
            val topic = topicOf(allOf(HandlerFailedUnexpectedly::class.java), allOf(CUSTOMER))

            subscribe(topic).errorCase() shouldBe UNSUPPORTED_REQUEST

            nothingRecorded()
        }

        @Test
        fun `with an entity type that no context serves, recording nothing`() {
            val topic = topicOf(allOf(CTask::class.java), allOf(CUSTOMER))

            subscribe(topic).errorCase() shouldBe UNSUPPORTED_REQUEST

            nothingRecorded()
        }
    }

    @Test
    fun `refuse a topic spanning a single-tenant and a multi-tenant context`() {
        application.close()
        application = testApplication {
            add(BoundedContextBuilder.assumingTests().add(Given.ProjectAggregateRepository()))
            add(
                BoundedContextBuilder.assumingTests(/* multitenant = */ true)
                    .add(Given.CustomerAggregateRepository())
            )
        }
        service = SubscriptionService.of(application)

        val topic = topicOf(allOf(PROJECT_CREATED), allOf(CUSTOMER))

        subscribe(topic).errorCase() shouldBe TENANT_ERROR
    }

    private fun topicOf(vararg targets: Target): Topic =
        requests.topic().forTargets(targets.toList())

    private fun subscribed(topic: Topic): Subscription {
        val observer = memoizingObserver<Subscription>()
        service.subscribe(topic, observer)
        observer.error shouldBe null
        return observer.firstResponse()
    }

    /**
     * Subscribes to the given topic, expecting the call to fail.
     *
     * @return The error of the call.
     */
    private fun subscribe(topic: Topic): Throwable {
        val observer = memoizingObserver<Subscription>()
        service.subscribe(topic, observer)
        observer.responses().shouldBeEmpty()
        return observer.error.shouldNotBeNull()
    }

    private fun activate(subscription: Subscription): MemoizingObserver<SubscriptionUpdate> {
        val observer = memoizingObserver<SubscriptionUpdate>()
        service.activate(subscription, observer)
        return observer
    }

    private fun cancel(subscription: Subscription): MemoizingObserver<Response> {
        val observer = memoizingObserver<Response>()
        service.cancel(subscription, observer)
        return observer
    }

    private fun createProject(): Command =
        requests.createCommand(Given.CommandMessage.createProject(ProjectId.generate()))

    /**
     * Creates a command to create a customer, taking the message from `Given`, which
     * posts its commands for a tenant of its own.
     */
    private fun createCustomer(): Command {
        val message = unpack(Given.ACommand.createCustomer().message) as CommandMessage
        return requests.createCommand(message)
    }

    private fun post(context: BoundedContext, command: Command) {
        val observer = memoizingObserver<Ack>()
        context.commandBus().post(command, observer)
        observer.firstResponse().status.hasOk() shouldBe true
    }

    private fun nothingRecorded() {
        projectsMonitor.events.shouldBeEmpty()
        customersMonitor.events.shouldBeEmpty()
    }

    /**
     * Obtains the updates of the target with the given index.
     */
    private fun MemoizingObserver<SubscriptionUpdate>.forTarget(index: Int) =
        responses().filter { it.targetIndex == index }

    private companion object {

        val PROJECT_CREATED = AggProjectCreated::class.java
        val CUSTOMER = Customer::class.java
        val PROJECT_STATE: TypeUrl = TypeUrl.of(AggProject::class.java)
        val CUSTOMER_STATE: TypeUrl = TypeUrl.of(Customer::class.java)
    }
}

/**
 * Tells whether the context has a repository of entities with the given state type.
 */
private fun BoundedContext.hasEntitiesWithState(type: TypeUrl): Boolean =
    stand().exposedTypes().contains(type)

