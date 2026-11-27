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

import io.grpc.Status
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.stub.StreamObserver
import io.spine.client.grpc.CommandServiceGrpc
import io.spine.client.grpc.SubscriptionServiceGrpc
import io.spine.core.Ack
import io.spine.core.Command
import io.spine.core.Response
import io.spine.core.Responses
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit.SECONDS

/**
 * An in-process server that has a stub of the subscription service, and the [client]
 * connected to it.
 *
 * Unless there is a [subscriptionError], the stub accepts every subscription, and starts its
 * stream of updates with the given [activation]. It answers every cancellation with `OK`.
 *
 * If there is a [commandAck], a stub of the command service answers every command with it.
 * Otherwise, the server has no command service, and posting a command fails with
 * `UNIMPLEMENTED`.
 *
 * @param commandAck The acknowledgement for every posted command, or `null` to have no
 *   command service.
 * @param subscriptionError The error with which the stub answers every subscription,
 *   or `null` to accept them.
 * @param configureClient Configures the builder of the [client], for example, with
 *   error handlers.
 * @param activation Starts the stream of updates of each subscription.
 */
internal class StubSubscriptionServer(
    commandAck: Ack? = null,
    subscriptionError: Status? = null,
    configureClient: Client.Builder.() -> Unit = {},
    activation: (StreamObserver<SubscriptionUpdate>) -> Unit
) : AutoCloseable {

    private val name = InProcessServerBuilder.generateName()

    /**
     * The subscriptions that the stub made, in their order.
     */
    val subscribed: MutableList<Subscription> = CopyOnWriteArrayList()

    /**
     * The subscriptions that the clients asked the stub to cancel, in their order.
     */
    val cancelled: MutableList<Subscription> = CopyOnWriteArrayList()

    /**
     * The commands that the stub of the command service received, in their order.
     */
    val posted: MutableList<Command> = CopyOnWriteArrayList()

    private val server = InProcessServerBuilder.forName(name)
        .directExecutor()
        .addService(SubscriptionStub(subscriptionError, activation))
        .apply { commandAck?.let { addService(CommandStub(it)) } }
        .build()
        .start()

    /**
     * The client connected to this server.
     */
    val client: Client = Client.inProcess(name)
        .shutdownTimeout(0, SECONDS)
        .apply(configureClient)
        .build()

    override fun close() {
        client.shutdown()
        server.shutdownNow()
    }

    private inner class SubscriptionStub(
        private val error: Status?,
        private val activation: (StreamObserver<SubscriptionUpdate>) -> Unit
    ) : SubscriptionServiceGrpc.SubscriptionServiceImplBase() {

        override fun subscribe(request: Topic, responseObserver: StreamObserver<Subscription>) {
            if (error != null) {
                responseObserver.onError(error.asRuntimeException())
                return
            }
            val subscription = Subscriptions.from(request)
            subscribed += subscription
            responseObserver.onNext(subscription)
            responseObserver.onCompleted()
        }

        override fun activate(
            request: Subscription,
            responseObserver: StreamObserver<SubscriptionUpdate>
        ) = activation(responseObserver)

        override fun cancel(request: Subscription, responseObserver: StreamObserver<Response>) {
            cancelled += request
            responseObserver.onNext(Responses.ok())
            responseObserver.onCompleted()
        }
    }

    private inner class CommandStub(
        private val ack: Ack
    ) : CommandServiceGrpc.CommandServiceImplBase() {

        override fun post(request: Command, responseObserver: StreamObserver<Ack>) {
            posted += request
            responseObserver.onNext(ack)
            responseObserver.onCompleted()
        }
    }
}
