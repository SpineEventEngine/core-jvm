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
     * and is a part of
     * {@linkplain Delivery#direct(io.spine.server.ServerEnvironment) direct delivery} feature.
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
