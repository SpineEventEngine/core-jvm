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

import com.google.common.collect.ImmutableList;
import com.google.protobuf.ProtocolStringList;
import io.spine.server.delivery.event.CatchUpCompleted;
import io.spine.server.delivery.event.CatchUpStarted;
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
     * Creates a {@code CatchUpStarted} event messages with the specified ID.
     */
    static CatchUpStarted started(CatchUpId id) {
        checkNotNull(id);
        return CatchUpStarted.newBuilder()
                .setId(id)
                .build();
    }

    /**
     * Creates a limit to be used in quering the {@link io.spine.server.event.EventStore
     * EventStore}.
     */
    static EventStreamQuery.Limit limitOf(int value) {
        return EventStreamQuery.Limit.newBuilder()
                .setValue(value)
                .build();
    }

    /**
     * Creates a {@code HistoryEventsRecalled} event messages with the specified ID.
     */
    static HistoryEventsRecalled recalled(CatchUpId id) {
        checkNotNull(id);
        return HistoryEventsRecalled.newBuilder()
                .setId(id)
                .build();
    }

    /**
     * Creates a {@code HistoryFullyRecalled} event messages with the specified ID.
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
    static ImmutableList<EventFilter> toFilters(ProtocolStringList rawEventTypes) {
        checkNotNull(rawEventTypes);
        return rawEventTypes.stream()
                .map(type -> EventFilter.newBuilder()
                        .setEventType(type)
                        .build())
                .collect(toImmutableList());
    }

    /**
     * Creates a {@code LiveEventsPickedUp} event messages with the specified ID.
     */
    static LiveEventsPickedUp liveEventsPickedUp(CatchUpId id) {
        checkNotNull(id);
        return LiveEventsPickedUp.newBuilder()
                .setId(id)
                .build();
    }

    /**
     * Creates a {@code CatchUpCompleted} event messages with the specified ID.
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
