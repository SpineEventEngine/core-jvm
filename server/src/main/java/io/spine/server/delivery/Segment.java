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

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Portion of the inbox messages that are headed to the same inbox.
 */
final class Segment {

    private final ShardedMessageDelivery<InboxMessage> delivery;
    private final List<InboxMessage> messages = new ArrayList<>();

    /**
     * Creates a new {@code Segment}.
     *
     * @param delivery
     *         the delivery of the inbox common for all messages in this segment
     */
    private Segment(ShardedMessageDelivery<InboxMessage> delivery) {
        this.delivery = delivery;
    }

    /**
     * Groups the messages into {@code Segments} keeping the original order of messages
     * across the returned list of segments.
     *
     * <p>Each segment holds the messages in a row that go to the same inbox.
     *
     * @param source
     *         the messages to group
     * @param deliveries
     *         the deliveries of the registered inboxes
     * @return an ordered list of {@code Segment}s
     * @throws IllegalStateException
     *         if the inbox of a message is not found
     */
    static ImmutableList<Segment> groupByInbox(Collection<InboxMessage> source,
                                               InboxDeliveries deliveries) {
        ImmutableList.Builder<Segment> builder = ImmutableList.builder();

        if (source.isEmpty()) {
            return ImmutableList.of();
        }
        Segment segment = null;
        for (var message : source) {
            var delivery = deliveries.get(message);
            if (segment == null) {
                segment = new Segment(delivery);
            } else {
                if (segment.delivery() != delivery) {
                    builder.add(segment);
                    segment = new Segment(delivery);
                }
            }
            segment.add(message);
        }
        if (segment.hasMessages()) {
            builder.add(segment);
        }
        var result = builder.build();
        return result;
    }

    /**
     * Returns the delivery of the inbox, to which the messages of this segment
     * need to be delivered.
     */
    ShardedMessageDelivery<InboxMessage> delivery() {
        return delivery;
    }

    /**
     * Returns the messages of this segment.
     */
    ImmutableList<InboxMessage> messages() {
        return ImmutableList.copyOf(messages);
    }

    /**
     * Adds another message into this segment.
     */
    private void add(InboxMessage message) {
        messages.add(message);
    }

    /**
     * Tells if this segment has any messages.
     */
    private boolean hasMessages() {
        return !messages.isEmpty();
    }
}
