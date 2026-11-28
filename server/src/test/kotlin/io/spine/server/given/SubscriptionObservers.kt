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

package io.spine.server.given

import io.grpc.stub.ServerCallStreamObserver
import io.spine.client.Subscription
import io.spine.client.SubscriptionUpdate
import io.spine.grpc.MemoizingObserver
import io.spine.grpc.StreamObservers.noOpObserver
import io.spine.server.BoundedContext

/**
 * A server call whose cancellation handler cannot be set, as gRPC refuses it once
 * the service method returns.
 *
 * Remembers the updates it receives.
 */
@Suppress("TooManyFunctions") // The abstract methods of `ServerCallStreamObserver`.
internal class FrozenCall : ServerCallStreamObserver<SubscriptionUpdate>() {

    private val received = mutableListOf<SubscriptionUpdate>()

    /**
     * The received updates, in the order of receiving.
     */
    val updates: List<SubscriptionUpdate> = received

    override fun setOnCancelHandler(onCancelHandler: Runnable) {
        error("The call is frozen.")
    }

    override fun isCancelled(): Boolean = false

    override fun setCompression(compression: String) = Unit

    override fun isReady(): Boolean = true

    override fun setOnReadyHandler(onReadyHandler: Runnable) = Unit

    override fun disableAutoInboundFlowControl() = Unit

    override fun request(count: Int) = Unit

    override fun setMessageCompression(enable: Boolean) = Unit

    override fun onNext(value: SubscriptionUpdate) {
        received.add(value)
    }

    override fun onError(t: Throwable) = Unit

    override fun onCompleted() = Unit
}

/**
 * Cancels the subscription in the given context when the stream completes.
 */
internal class CancellingObserver(
    private val context: BoundedContext,
    private val subscription: Subscription
) : MemoizingObserver<SubscriptionUpdate>() {

    override fun onCompleted() {
        super.onCompleted()
        context.stand().cancel(subscription, noOpObserver())
    }
}
