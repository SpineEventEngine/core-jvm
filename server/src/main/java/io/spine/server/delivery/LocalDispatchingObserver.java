/*
 * Copyright 2022, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Redistribution and use in source and/or binary forms, with or without
 * modification, must retain the above copyright notice and the following
 * disclaimer.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.spine.server.delivery;

import io.spine.annotation.VisibleForTesting;
import io.spine.server.ServerEnvironment;
import io.spine.server.tenant.TenantAwareRunner;

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

    private final boolean async;

    /**
     * Creates a new observer performing the delivery in either synchronous manner or in
     * a new {@code Thread} for each received {@code InboxMessage}.
     *
     * @param asynchronous
     *         whether the delivery should be performed synchronously
     */
    LocalDispatchingObserver(boolean asynchronous) {
        this.async = asynchronous;
    }

    /**
     * Creates a new observer instance which performs the delivery synchronously.
     */
    public LocalDispatchingObserver() {
        this(false);
    }

    @Override
    public void onMessage(InboxMessage update) {
        var delivery = ServerEnvironment.instance()
                                        .delivery();
        var index = update.shardIndex();
        if (async) {
            new Thread(() -> runDelivery(update, delivery, index)).start();
        } else {
            runDelivery(update, delivery, index);
        }
    }

    private static void runDelivery(InboxMessage message, Delivery delivery, ShardIndex index) {
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
