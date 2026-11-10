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

package io.spine.client

import com.google.protobuf.Message
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.stub.StreamObserver
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.spine.client.grpc.SubscriptionServiceGrpc
import io.spine.core.ErrorCase
import io.spine.core.ErrorCase.REQUEST_DENIED
import io.spine.core.ErrorCase.UNSUPPORTED_REQUEST
import io.spine.core.Response
import io.spine.core.Responses.errorWith
import io.spine.core.response
import io.spine.server.BoundedContextBuilder
import io.spine.test.client.ClientTestContext
import io.spine.test.client.users.event.UserLoggedIn
import io.spine.testing.client.TestActorRequestFactory
import io.spine.validation.NonValidated
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit.SECONDS
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`Subscriptions` failing to cancel a subscription should")
internal class SubscriptionCancellationErrorCaseSpec : AbstractClientTest() {

    private val serverErrors = CompletableFuture<Pair<Message, ErrorCase>>()
    private val streamingErrors = CompletableFuture<Throwable>()
    private val subscription = Subscriptions.from(
        TestActorRequestFactory(javaClass).topic().allOf(UserLoggedIn::class.java)
    )

    override fun contexts(): List<BoundedContextBuilder> =
        listOf(ClientTestContext.users())

    override fun newClientBuilder(serverName: String): Client.Builder =
        withErrorHandlers(super.newClientBuilder(serverName))

    @Test
    fun `pass the subscription and the error case to the server error handler`() {
        cancelWith(client())

        serverErrors.get(TIMEOUT, SECONDS) shouldBe (subscription to UNSUPPORTED_REQUEST)
        streamingErrors.isDone shouldBe false
    }

    @Test
    fun `pass the error case of the response to the server error handler`() {
        val answer = response { status = errorWith(REQUEST_DENIED) }

        cancelOnStub { observer ->
            observer.onNext(answer)
            observer.onCompleted()
        }

        serverErrors.get(TIMEOUT, SECONDS) shouldBe (subscription to REQUEST_DENIED)
        streamingErrors.isDone shouldBe false
    }

    @Test
    fun `pass a failure without an error case to the streaming error handler`() {
        cancelOnStub { observer ->
            observer.onError(Status.UNAVAILABLE.asRuntimeException())
        }

        val failure = streamingErrors.get(TIMEOUT, SECONDS)
            .shouldBeInstanceOf<StatusRuntimeException>()
        failure.status.code shouldBe Status.Code.UNAVAILABLE
        serverErrors.isDone shouldBe false
    }

    @Test
    fun `pass a response without a status to the streaming error handler`() {
        val answer: @NonValidated Response = Response.newBuilder().buildPartial()

        cancelOnStub { observer ->
            observer.onNext(answer)
            observer.onCompleted()
        }

        val failure = streamingErrors.get(TIMEOUT, SECONDS)
            .shouldBeInstanceOf<IllegalStateException>()
        failure.message shouldContain "`STATUS_NOT_SET`"
        serverErrors.isDone shouldBe false
    }

    private fun withErrorHandlers(builder: Client.Builder): Client.Builder =
        builder
            .onServerError { message, errorCase -> serverErrors.complete(message to errorCase) }
            .onStreamingError { throwable -> streamingErrors.complete(throwable) }

    /**
     * Makes the given client remember the [subscription], unknown to the server, and requests
     * its cancellation.
     */
    private fun cancelWith(client: Client) {
        val subscriptions = client.subscriptions()
        subscriptions.addAll(listOf(subscription))
        subscriptions.cancel(subscription)
    }

    /**
     * Requests the cancellation of the [subscription] from a stub of the subscription service,
     * which answers the cancellation as the given function does.
     */
    private fun cancelOnStub(answer: (StreamObserver<Response>) -> Unit) {
        val serverName = InProcessServerBuilder.generateName()
        val server = InProcessServerBuilder.forName(serverName)
            .directExecutor()
            .addService(AnsweringCancellation(answer))
            .build()
            .start()
        val client = withErrorHandlers(
            Client.inProcess(serverName).shutdownTimeout(0, SECONDS)
        ).build()
        try {
            cancelWith(client)
            CompletableFuture.anyOf(serverErrors, streamingErrors).get(TIMEOUT, SECONDS)
        } finally {
            client.shutdown()
            server.shutdownNow()
        }
    }

    private companion object {

        /**
         * The number of seconds to wait for the asynchronous answer to a cancellation.
         */
        const val TIMEOUT = 5L
    }
}

/**
 * Answers every cancellation request as the given function does.
 */
private class AnsweringCancellation(
    private val answer: (StreamObserver<Response>) -> Unit
) : SubscriptionServiceGrpc.SubscriptionServiceImplBase() {

    override fun cancel(request: Subscription, responseObserver: StreamObserver<Response>) {
        answer(responseObserver)
    }
}
