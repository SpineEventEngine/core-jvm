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

import io.spine.annotation.Experimental;

import java.util.List;

/**
 * A delivery routine of postponed processing the previously sharded messages.
 *
 * @param <M>
 *         the type of sharded messages
 */
interface ShardedMessageDelivery<M extends ShardedRecord> {

    /**
     * Delivers the previously sharded messages to their targets.
     *
     * <p>The descendants typically will initialize the targets for the messages (such as entities)
     * and handle the dispatching results.
     *
     * <p>Any runtime issues should be handled by the descendants by emitting the corresponding
     * rejection events and potentially notifying the respective entity repositories.
     *
     * @param incoming
     *         the incoming messages to deliver
     * @param monitor
     *         the delivery monitor to be notified of the reception failures
     * @param conveyor
     *         the conveyor holding the current state of the inbox messages being delivered
     */
    void deliver(List<M> incoming, DeliveryMonitor monitor, Conveyor conveyor);

    /**
     * Delivers a single message to its target directly, omitting any delivery monitoring etc.
     *
     * <p>This method is experimental,
     * and is a part of {@linkplain Delivery#direct() direct delivery} feature.
     *
     * @param message
     *         the incoming message to deliver
     */
    @Experimental
    void deliverDirectly(M message);

    /**
     * Serves to notify that the given message was originally sent to be
     * {@linkplain #deliver(List, DeliveryMonitor, Conveyor) delivered},
     * but turned out to be a duplicate.
     */
    void onDuplicate(M message);
}
