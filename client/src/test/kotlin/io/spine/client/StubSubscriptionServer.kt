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

import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.stub.StreamObserver
import io.spine.client.grpc.SubscriptionServiceGrpc
import io.spine.core.Response
import io.spine.core.Responses
import java.util.concurrent.TimeUnit.SECONDS

/**
 * An in-process server that has only a stub of the subscription service, and the [client]
 * connected to it.
 *
 * The stub accepts every subscription, and starts its stream of updates with
 * the given [activation]. It answers every cancellation with `OK`. The server has
 * no command service, so posting a command fails with `UNIMPLEMENTED`.
 */
internal class StubSubscriptionServer(
    activation: (StreamObserver<SubscriptionUpdate>) -> Unit
) : AutoCloseable {

    private val name = InProcessServerBuilder.generateName()
    private val server = InProcessServerBuilder.forName(name)
        .directExecutor()
        .addService(Service(activation))
        .build()
        .start()

    /**
     * The client connected to this server.
     */
    val client: Client = Client.inProcess(name)
        .shutdownTimeout(0, SECONDS)
        .build()

    override fun close() {
        client.shutdown()
        server.shutdownNow()
    }

    private class Service(
        private val activation: (StreamObserver<SubscriptionUpdate>) -> Unit
    ) : SubscriptionServiceGrpc.SubscriptionServiceImplBase() {

        override fun subscribe(request: Topic, responseObserver: StreamObserver<Subscription>) {
            responseObserver.onNext(Subscriptions.from(request))
            responseObserver.onCompleted()
        }

        override fun activate(
            request: Subscription,
            responseObserver: StreamObserver<SubscriptionUpdate>
        ) = activation(responseObserver)

        override fun cancel(request: Subscription, responseObserver: StreamObserver<Response>) {
            responseObserver.onNext(Responses.ok())
            responseObserver.onCompleted()
        }
    }
}
