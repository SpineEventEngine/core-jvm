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

package io.spine.server.delivery;

import io.spine.annotation.VisibleForTesting;
import io.spine.server.tenant.TenantAwareRunner;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * An observer of changes to the shard contents, which triggers immediate delivery of the
 * sharded messages.
 *
 * <p>Depending on the configuration, the delivery may be triggered either synchronously
 * or asynchronously.
 *
 * <p>Suitable for the local and development environment.
 */
@VisibleForTesting
public final class LocalDispatchingObserver implements ShardObserver {

    private final Delivery delivery;
    private final boolean async;

    /**
     * Creates a new observer performing the delivery in either synchronous manner or in
     * a new {@code Thread} for each received {@code InboxMessage}.
     *
     * @param delivery
     *         the delivery which delivers the messages from the observed shards
     * @param asynchronous
     *         whether the delivery should be performed asynchronously
     */
    LocalDispatchingObserver(Delivery delivery, boolean asynchronous) {
        this.delivery = checkNotNull(delivery);
        this.async = asynchronous;
    }

    /**
     * Creates a new observer instance that performs the delivery synchronously.
     *
     * @param delivery
     *         the delivery which delivers the messages from the observed shards
     */
    public LocalDispatchingObserver(Delivery delivery) {
        this(delivery, false);
    }

    @Override
    public void onMessage(InboxMessage update) {
        var index = update.shardIndex();
        if (async) {
            new Thread(() -> runDelivery(update, index)).start();
        } else {
            runDelivery(update, index);
        }
    }

    private void runDelivery(InboxMessage message, ShardIndex index) {
        var tenant = message.tenant();
        TenantAwareRunner.with(tenant)
                         .run(() -> delivery.deliverMessagesFrom(index));
    }

    /**
     * Tells whether this observer runs in an asynchronous mode.
     */
    @VisibleForTesting
    boolean isAsync() {
        return async;
    }
}
