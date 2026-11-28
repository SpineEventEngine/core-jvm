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

package io.spine.server.stand

import io.grpc.stub.StreamObserver
import io.spine.client.SubscriptionUpdate
import io.spine.server.GuardedObserver

/**
 * A [SubscriptionCallback] that passes the updates to an observer and completes
 * the observer when the subscription is cancelled.
 *
 * Several Bounded Contexts may hold the subscription, deliver its updates, and tell
 * the callback about its cancellation at the same time. The callback is safe to use from
 * several threads: the observer is completed once, and gets no updates after that.
 *
 * @param observer The observer to pass the updates to.
 */
internal class ForwardingCallback(
    observer: StreamObserver<SubscriptionUpdate>
) : SubscriptionCallback {

    private val guarded = GuardedObserver(observer)

    override fun accept(update: SubscriptionUpdate) {
        guarded.onNext(update)
    }

    override fun onCancelled() {
        guarded.onCompleted()
    }
}
