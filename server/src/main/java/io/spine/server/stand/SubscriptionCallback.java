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

package io.spine.server.stand;

import io.grpc.stub.StreamObserver;
import io.spine.client.Subscription;
import io.spine.client.SubscriptionUpdate;

import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Delivers the given subscription update to the read-side.
 *
 * @see Stand#activate(Subscription, SubscriptionCallback, StreamObserver)
 * @see Stand#cancel(Subscription, StreamObserver)
 */
public interface SubscriptionCallback extends Consumer<SubscriptionUpdate> {

    /**
     * Handles the end of the subscription for this callback: the subscription is cancelled,
     * or activated again with another callback.
     *
     * <p>The callback receives no updates after that.
     *
     * <p>Does nothing by default.
     */
    default void onCancelled() {
        // Do nothing.
    }

    /**
     * Creates the callback that forwards the updates to the passed observer and completes
     * the observer when the subscription is cancelled.
     */
    static SubscriptionCallback forwardingTo(StreamObserver<SubscriptionUpdate> observer) {
        checkNotNull(observer);
        return new ForwardingCallback(observer);
    }
}
