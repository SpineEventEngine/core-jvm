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

import io.grpc.Status.Code.INTERNAL
import io.grpc.Status.Code.INVALID_ARGUMENT
import io.grpc.StatusRuntimeException
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.spine.client.Subscription
import io.spine.client.SubscriptionUpdate
import io.spine.client.Subscriptions
import io.spine.client.Topic
import io.spine.core.ErrorCase.INTERNAL_ERROR
import io.spine.core.ErrorCase.INVALID_REQUEST
import io.spine.core.ErrorCase.UNSUPPORTED_REQUEST
import io.spine.core.Response
import io.spine.grpc.MemoizingObserver
import io.spine.grpc.StreamObservers.memoizingObserver
import io.spine.server.bc.given.SecretProjectRepository
import io.spine.server.given.RequestEventMonitor
import io.spine.server.given.errorCase
import io.spine.system.server.HandlerFailedUnexpectedly
import io.spine.system.server.event.SubscriptionServiceFailed
import io.spine.system.server.event.SubscriptionValidationFailed
import io.spine.system.server.event.TopicValidationFailed
import io.spine.system.server.event.UnknownSubscriptionActivationRequested
import io.spine.system.server.event.UnknownSubscriptionCancellationRequested
import io.spine.system.server.event.UnsupportedTopicReceived
import io.spine.test.aggregate.AggProject
import io.spine.test.bc.SecretProject
import io.spine.test.client.tasks.CTask
import io.spine.test.client.tasks.event.CTaskCreated
import io.spine.testing.client.TestActorRequestFactory
import io.spine.testing.logging.mute.MuteLogging
import io.spine.type.TypeUrl
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Supplements the Java suite `SubscriptionServiceTest` with the specs of what
 * the service answers to a request it does not serve, and what it records.
 */
@MuteLogging
@DisplayName("`SubscriptionService` should")
internal class SubscriptionServiceErrorCaseSpec {

    private val requests = TestActorRequestFactory(javaClass)
    private val monitor = RequestEventMonitor()
    private lateinit var context: BoundedContext
    private lateinit var service: SubscriptionService

    @BeforeEach
    fun createService() {
        context = BoundedContextBuilder.assumingTests()
            .add(Given.ProjectAggregateRepository())
            .add(SecretProjectRepository())
            .build()
        context.internalAccess().registerEventDispatcher(monitor)
        service = SubscriptionService.withSingle(context)
    }

    @AfterEach
    fun closeContext() {
        context.close()
    }

    @Nested inner class
    `refuse to subscribe to a topic` {

        @Test
        fun `of a System event, recording nothing`() {
            val topic = requests.topic().allOf(HandlerFailedUnexpectedly::class.java)

            subscribe(topic).errorCase() shouldBe UNSUPPORTED_REQUEST

            monitor.events.shouldBeEmpty()
        }

        @Test
        fun `that fails validation, recording the violations`() {
            val invalid = projectsTopic().toBuilder()
                .clearId()
                .buildPartial()

            subscribe(invalid).errorCase() shouldBe INVALID_REQUEST

            val failed = monitor.single<TopicValidationFailed>()
            failed.validationError.constraintViolationList.shouldNotBeEmpty()
            failed.actorContext shouldBe invalid.context
        }

        @Test
        fun `of a type not exposed for subscription, recording the type`() {
            val topic = requests.topic().allOf(SecretProject::class.java)

            subscribe(topic).errorCase() shouldBe UNSUPPORTED_REQUEST

            val unsupported = monitor.single<UnsupportedTopicReceived>()
            unsupported.id shouldBe topic.id
            unsupported.typeUrl shouldBe TypeUrl.of(SecretProject::class.java).value()
            unsupported.actorContext shouldBe topic.context
        }
    }

    @Nested inner class
    `refuse a topic that no context serves, recording nothing, if it` {

        @Test
        fun `targets an entity type`() {
            val topic = requests.topic().allOf(CTask::class.java)

            subscribe(topic).errorCase() shouldBe UNSUPPORTED_REQUEST

            monitor.events.shouldBeEmpty()
        }

        @Test
        fun `fails validation`() {
            val invalid = requests.topic().allOf(CTaskCreated::class.java).toBuilder()
                .clearId()
                .buildPartial()

            subscribe(invalid).errorCase() shouldBe INVALID_REQUEST

            monitor.events.shouldBeEmpty()
        }
    }

    @Nested inner class
    `reject a subscription` {

        @Test
        fun `that fails validation, recording the violations`() {
            val invalid = subscribed().toBuilder()
                .clearId()
                .buildPartial()

            activate(invalid).errorCase() shouldBe INVALID_REQUEST

            val failed = monitor.single<SubscriptionValidationFailed>()
            failed.validationError.constraintViolationList.shouldNotBeEmpty()
        }

        @Test
        fun `to activate, which the context does not have, and record the request`() {
            val unknown = unknownSubscription(projectsTopic())

            activate(unknown).errorCase() shouldBe UNSUPPORTED_REQUEST

            val requested = monitor.single<UnknownSubscriptionActivationRequested>()
            requested.id shouldBe unknown.id
            requested.actorContext shouldBe unknown.topic.context
        }

        @Test
        fun `to cancel, which the context does not have, and record the request`() {
            val unknown = unknownSubscription(projectsTopic())

            cancel(unknown).errorCase() shouldBe UNSUPPORTED_REQUEST

            val requested = monitor.single<UnknownSubscriptionCancellationRequested>()
            requested.id shouldBe unknown.id
        }
    }

    @Nested inner class
    `refuse a subscription that no context has, recording nothing, when` {

        @Test
        fun activating() {
            val unknown = unknownSubscription(requests.topic().allOf(CTaskCreated::class.java))

            activate(unknown).errorCase() shouldBe UNSUPPORTED_REQUEST

            monitor.events.shouldBeEmpty()
        }

        @Test
        fun `activating, if it fails validation`() {
            val invalid = unknownSubscription(requests.topic().allOf(CTaskCreated::class.java))
                .toBuilder()
                .clearId()
                .buildPartial()

            activate(invalid).errorCase() shouldBe INVALID_REQUEST

            monitor.events.shouldBeEmpty()
        }

        @Test
        fun cancelling() {
            val unknown = unknownSubscription(requests.topic().allOf(CTaskCreated::class.java))

            cancel(unknown).errorCase() shouldBe UNSUPPORTED_REQUEST

            monitor.events.shouldBeEmpty()
        }
    }

    @Test
    fun `answer an unexpected failure with the internal error, and record it`() {
        val subscription = subscribed()
        val failingObserver = object : MemoizingObserver<Response>() {
            override fun onNext(value: Response) {
                super.onNext(value)
                error("The observer fails on purpose.")
            }
        }

        service.cancel(subscription, failingObserver)

        val error = failingObserver.error
        error.shouldBeInstanceOf<StatusRuntimeException>()
        error.status.code shouldBe INTERNAL
        error.errorCase() shouldBe INTERNAL_ERROR
        val failed = monitor.single<SubscriptionServiceFailed>()
        failed.topic shouldBe subscription.topic.id
        failed.subscription shouldBe subscription.id
        failed.error.type shouldBe IllegalStateException::class.java.canonicalName
    }

    @Test
    fun `record a failure to subscribe to a topic`() {
        val topic = projectsTopic()
        val failingObserver = object : MemoizingObserver<Subscription>() {
            override fun onNext(value: Subscription) {
                error("The observer fails on purpose.")
            }
        }

        service.subscribe(topic, failingObserver)

        failingObserver.error.shouldNotBeNull().errorCase() shouldBe INTERNAL_ERROR
        val failed = monitor.single<SubscriptionServiceFailed>()
        failed.topic shouldBe topic.id
        failed.hasSubscription() shouldBe false
    }

    @Test
    fun `answer the internal error on a failure with a topic that no context serves`() {
        val failingObserver = object : MemoizingObserver<Subscription>() {
            override fun onNext(value: Subscription) {
                error("The observer fails on purpose.")
            }
        }

        service.subscribe(requests.topic().allOf(CTaskCreated::class.java), failingObserver)

        failingObserver.error.shouldNotBeNull().errorCase() shouldBe INTERNAL_ERROR
        monitor.events.shouldBeEmpty()
    }

    @Test
    fun `answer the internal error if the observer fails to take an answer`() {
        val observer = FailingOnceObserver()

        service.subscribe(requests.topic().allOf(HandlerFailedUnexpectedly::class.java), observer)

        observer.error.shouldNotBeNull().errorCase() shouldBe INTERNAL_ERROR
    }

    @Test
    fun `cancel a subscription in the contexts that have it, if no context serves its type`() {
        val observer = memoizingObserver<Subscription>()
        service.subscribe(requests.topic().allOf(CTaskCreated::class.java), observer)
        val subscription = observer.firstResponse()
        val cancellation = memoizingObserver<Response>()

        service.cancel(subscription, cancellation)

        cancellation.error shouldBe null
        cancellation.firstResponse().status.hasOk() shouldBe true
        cancellation.isCompleted shouldBe true
        context.stand().hasSubscription(subscription.id) shouldBe false
    }

    @Test
    fun `refuse an invalid topic even if the service knows no context`() {
        BoundedContextBuilder.assumingTests().build().use { empty ->
            val noContexts = SubscriptionService.withSingle(empty)
            val invalid = requests.topic().allOf(CTaskCreated::class.java).toBuilder()
                .clearId()
                .buildPartial()
            val observer = memoizingObserver<Subscription>()

            noContexts.subscribe(invalid, observer)

            observer.responses().shouldBeEmpty()
            observer.error.shouldNotBeNull().errorCase() shouldBe INVALID_REQUEST
        }
    }

    @Test
    fun `fail a call with a status that tells only the error case`() {
        val topic = requests.topic().allOf(SecretProject::class.java)

        val error = subscribe(topic)

        error.shouldBeInstanceOf<StatusRuntimeException>()
        error.status.code shouldBe INVALID_ARGUMENT
        error.status.description.isNullOrEmpty() shouldBe true
    }

    private fun projectsTopic(): Topic = requests.topic().allOf(AggProject::class.java)

    private fun subscribed(): Subscription {
        val observer = memoizingObserver<Subscription>()
        service.subscribe(projectsTopic(), observer)
        observer.error shouldBe null
        return observer.firstResponse()
    }

    private fun unknownSubscription(topic: Topic): Subscription =
        Subscription.newBuilder()
            .setId(Subscriptions.generateId())
            .setTopic(topic)
            .build()

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

    private fun activate(subscription: Subscription): Throwable {
        val observer = memoizingObserver<SubscriptionUpdate>()
        service.activate(subscription, observer)
        observer.responses().shouldBeEmpty()
        return observer.error.shouldNotBeNull()
    }

    private fun cancel(subscription: Subscription): Throwable {
        val observer = memoizingObserver<Response>()
        service.cancel(subscription, observer)
        observer.responses().shouldBeEmpty()
        return observer.error.shouldNotBeNull()
    }

    /**
     * An observer that fails to take the first error, and memoizes the next one.
     */
    private class FailingOnceObserver : MemoizingObserver<Subscription>() {

        private var failed = false

        override fun onError(t: Throwable) {
            if (!failed) {
                failed = true
                error("The observer fails on purpose.")
            }
            super.onError(t)
        }
    }
}
