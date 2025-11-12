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

import com.google.protobuf.Duration;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A station that delivers those messages which are incoming in a live mode.
 *
 * <p>Before the dispatching, the messages are deduplicated, taking into account {@linkplain
 * Conveyor#recentlyDelivered() all known delivered messages}. In this process, the messages
 * delivered previously and kept for longer are taken into account as well. The detected duplicates
 * are marked as such in the conveyor and are removed from the storage later.
 *
 * <p>The dispatched messages are reordered chronologically. However, the changes in ordering
 * are not propagated to the conveyor.
 *
 * @see CatchUpStation for the station performing the catch-up
 */
final class LiveDeliveryStation extends Station {

    /**
     * The action to use for the delivery of the messages to their targets.
     */
    private final DeliveryAction action;

    /**
     * The current setting of the deduplication window.
     *
     * <p>Is {@code null}, if not set.
     */
    private final @Nullable Duration deduplicationWindow;

    /**
     * Creates a new instance of {@code LiveDeliveryStation} with the action to use for the delivery
     * and the deduplication window.
     */
    LiveDeliveryStation(DeliveryAction action, Duration deduplicationWindow) {
        super();
        this.action = action;
        this.deduplicationWindow = !deduplicationWindow.equals(Duration.getDefaultInstance())
                                   ? deduplicationWindow
                                   : null;
    }

    /**
     * Filters the messages in {@link InboxMessageStatus#TO_DELIVER TO_DELIVER} from the conveyor
     * and dispatches them to their targets.
     *
     * <p>Before the dispatching, the messages are deduplicated, taking into account {@linkplain
     * Conveyor#recentlyDelivered() all known delivered messages}. In this process, the messages
     * delivered previously and kept for longer are taken into account as well. The detected
     * duplicates are marked as such in the conveyor and are removed from the storage later.
     *
     * <p>The dispatched messages are reordered chronologically. The changes in ordering are
     * not propagated to the conveyor.
     *
     * <p>After the messages are dispatched, they are marked {@link InboxMessageStatus#DELIVERED
     * DELIVERED}.
     *
     * @param conveyor
     *         the conveyor on which the messages are travelling
     * @return how many messages were delivered and whether there were any errors during
     *         the dispatching
     */
    @Override
    public Result process(Conveyor conveyor) {
        var filter = new FilterToDeliver(conveyor);
        var filtered = filter.messagesToDispatch();
        if (filtered.isEmpty()) {
            return emptyResult();
        }
        var toDispatch = deduplicateAndSort(filtered, conveyor);
        var errors = action.executeFor(toDispatch);
        conveyor.markDelivered(toDispatch);
        var result = new Result(toDispatch.size(), errors);
        return result;
    }

    /**
     * Runs through the conveyor and processes the messages in {@link InboxMessageStatus#TO_DELIVER
     * TO_DELIVER} status.
     */
    private class FilterToDeliver {

        private final Map<DispatchingId, InboxMessage> seen = new HashMap<>();
        private final Conveyor conveyor;

        private FilterToDeliver(Conveyor conveyor) {
            this.conveyor = conveyor;
        }

        /**
         * Returns the messages considered to ready for further dispatching.
         */
        private Collection<InboxMessage> messagesToDispatch() {
            for (var message : conveyor) {
                accept(message);
            }
            return seen.values();
        }

        /**
         * Processes the passed message matching it to the filter requirements.
         *
         * <p>The messages in {@link InboxMessageStatus#TO_DELIVER TO_DELIVER} are accepted for
         * further dispatching.
         *
         * <p>If this message has already been passed to this filter, it is removed as a duplicate.
         *
         * <p>If the deduplication window is
         * {@linkplain DeliveryBuilder#setDeduplicationWindow(Duration) set in the system} and
         * the message is not a duplicate, it is additionally
         * {@linkplain Conveyor#keepForLonger(InboxMessage, Duration) set to be kept} in their
         * inboxes for the duration, corresponding to the width of the window.
         *
         * @param message
         *         the message to run through the filter
         */
        private void accept(InboxMessage message) {
            var status = message.getStatus();
            if (status == InboxMessageStatus.TO_DELIVER) {
                var dispatchingId = new DispatchingId(message);
                if (seen.containsKey(dispatchingId)) {
                    conveyor.markDuplicateAndRemove(message);
                } else {
                    seen.put(dispatchingId, message);
                    if (deduplicationWindow != null) {
                        conveyor.keepForLonger(message, deduplicationWindow);
                    }
                }
            }
        }
    }

    /**
     * Deduplicates and sorts the messages.
     *
     * <p>The passed conveyor is used to understand which messages were previously delivered
     * and should be used as a deduplication source.
     *
     * <p>Duplicated messages are {@linkplain Conveyor#recentDuplicates() remembered by the
     * conveyor} and marked for removal.
     *
     * <p>Messages are sorted {@linkplain InboxMessageComparator#chronologically chronologically}.
     *
     * @param messages
     *         message to deduplicate and sort
     * @param conveyor
     *         current conveyor
     * @return deduplicated and sorted messages
     */
    private static List<InboxMessage> deduplicateAndSort(Collection<InboxMessage> messages,
                                                         Conveyor conveyor) {
        var previouslyDelivered = conveyor.allDelivered();
        List<InboxMessage> result = new ArrayList<>();
        for (var message : messages) {
            var id = new DispatchingId(message);
            if (previouslyDelivered.contains(id)) {
                conveyor.markDuplicateAndRemove(message);
            } else {
                result.add(message);
            }
        }
        result.sort(InboxMessageComparator.chronologically);
        return result;
    }
}
