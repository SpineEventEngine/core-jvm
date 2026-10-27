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
import com.google.protobuf.ProtocolStringList;
import io.spine.server.delivery.event.AllShardsFinalized;
import io.spine.server.delivery.event.CatchUpCompleted;
import io.spine.server.delivery.event.CatchUpStarted;
import io.spine.server.delivery.event.FinalizationStarted;
import io.spine.server.delivery.event.HistoryEventsRecalled;
import io.spine.server.delivery.event.HistoryFullyRecalled;
import io.spine.server.delivery.event.LiveEventsPickedUp;
import io.spine.server.delivery.event.ShardProcessingRequested;
import io.spine.server.event.EventFilter;
import io.spine.server.event.EventStreamQuery;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;

/**
 * A utility class producing the messages related to the {@code CatchUp} process.
 */
final class CatchUpMessages {

    private CatchUpMessages() {
    }

    /**
     * Creates a {@code CatchUpStarted} event message with the specified ID.
     */
    static CatchUpStarted started(CatchUpId id) {
        checkNotNull(id);
        return CatchUpStarted.newBuilder()
                .setId(id)
                .build();
    }

    /**
     * Creates a limit to be used in querying the {@link io.spine.server.event.EventStore
     * EventStore}.
     */
    static EventStreamQuery.Limit limitOf(int value) {
        return EventStreamQuery.Limit.newBuilder()
                .setValue(value)
                .build();
    }

    /**
     * Creates a {@code HistoryEventsRecalled} event message with the specified ID.
     */
    static HistoryEventsRecalled recalled(CatchUpId id) {
        checkNotNull(id);
        return HistoryEventsRecalled.newBuilder()
                .setId(id)
                .build();
    }

    /**
     * Creates a {@code HistoryFullyRecalled} event message with the specified ID.
     */
    static HistoryFullyRecalled fullyRecalled(CatchUpId id) {
        checkNotNull(id);
        return HistoryFullyRecalled.newBuilder()
                .setId(id)
                .build();
    }

    /**
     * Wraps the passed list of {@code String}s into a list of {@code EventFilter}s.
     */
    @SuppressWarnings("NonApiType") // We highlight the fact of working with Protobuf strings.
    static ImmutableList<EventFilter> toFilters(ProtocolStringList rawEventTypes) {
        checkNotNull(rawEventTypes);
        return rawEventTypes.stream()
                .map(type -> EventFilter.newBuilder()
                        .setEventType(type)
                        .build())
                .collect(toImmutableList());
    }

    /**
     * Creates a {@code LiveEventsPickedUp} event message with the specified ID.
     */
    static LiveEventsPickedUp liveEventsPickedUp(CatchUpId id) {
        checkNotNull(id);
        return LiveEventsPickedUp.newBuilder()
                .setId(id)
                .build();
    }

    /**
     * Creates a {@code FinalizationStarted} event message with the specified ID.
     */
    static FinalizationStarted finalizationStarted(CatchUpId id) {
        checkNotNull(id);
        return FinalizationStarted.newBuilder()
                .setId(id)
                .build();
    }

    /**
     * Creates an {@code AllShardsFinalized} event message with the specified ID.
     */
    static AllShardsFinalized allShardsFinalized(CatchUpId id) {
        checkNotNull(id);
        return AllShardsFinalized.newBuilder()
                .setId(id)
                .build();
    }

    /**
     * Creates a {@code CatchUpCompleted} event message with the specified ID.
     */
    static CatchUpCompleted catchUpCompleted(CatchUpId id) {
        checkNotNull(id);
        return CatchUpCompleted.newBuilder()
                .setId(id)
                .build();
    }

    /**
     * Creates a {@code ShardProcessingRequested} event message with the specified index, requested
     * by the {@link CatchUpProcess} with the given ID.
     */
    static ShardProcessingRequested shardProcessingRequested(CatchUpId id, ShardIndex shardIndex) {
        checkNotNull(shardIndex);
        return ShardProcessingRequested.newBuilder()
                .setIndex(shardIndex)
                .setProcess(id)
                .build();
    }
}
