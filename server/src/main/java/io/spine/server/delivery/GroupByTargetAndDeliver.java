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

import io.spine.server.model.ModelError;

import java.util.List;

/**
 * A method object performing the delivery of the messages grouping them by the inboxes of their
 * targets.
 */
final class GroupByTargetAndDeliver implements DeliveryAction {

    private final InboxDeliveries inboxDeliveries;
    private final DeliveryMonitor monitor;
    private final Conveyor conveyor;

    GroupByTargetAndDeliver(InboxDeliveries deliveries,
                            DeliveryMonitor monitor,
                            Conveyor conveyor) {
        inboxDeliveries = deliveries;
        this.monitor = monitor;
        this.conveyor = conveyor;
    }

    /**
     * Performs the delivery of the messages grouping them by the inboxes of their targets.
     *
     * <p>If an exception is thrown during delivery, this method propagates it.
     * If many exceptions are thrown, all of them are added to the first one
     * as {@code suppressed}, and the first one is propagated.
     *
     * <p>In case of an exception, the messages are marked as delivered, in order to avoid
     * repetitive delivery. However, if a JVM {@link Error} is thrown, only the messages
     * that were delivered successfully are marked as delivered. Moreover, a JVM {@link Error}
     * halts the delivery for all the subsequent messages in the batch.
     * However, this is not true for {@link ModelError}s, which are treated
     * in the same way as exceptions.
     *
     * @return errors occurred during the delivery
     */
    @Override
    public DeliveryErrors executeFor(List<InboxMessage> messages) {
        List<Segment> segments = Segment.groupByInbox(messages, inboxDeliveries);
        var errors = DeliveryErrors.newBuilder();
        for (var segment : segments) {
            var delivery = segment.delivery();
            List<InboxMessage> deliveryPackage = segment.messages();
            try {
                delivery.deliver(deliveryPackage, monitor, conveyor);
            } catch (RuntimeException exception) {
                errors.addException(exception);
            } catch (@SuppressWarnings("ErrorNotRethrown") /* False-positive */ ModelError error) {
                errors.addError(error);
            }
        }
        return errors.build();
    }
}
