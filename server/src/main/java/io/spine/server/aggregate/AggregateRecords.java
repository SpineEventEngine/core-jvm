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

package io.spine.server.aggregate;

import com.google.protobuf.util.Timestamps;
import io.spine.base.Identifier;
import io.spine.core.Event;
import io.spine.protobuf.AnyPacker;
import io.spine.server.entity.EntityRecord;
import io.spine.string.Stringifiers;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.protobuf.util.Timestamps.checkValid;
import static io.spine.util.Preconditions2.checkNotEmptyOrBlank;
import static java.lang.String.format;

/**
 * A factory of records storing the {@link Aggregate} data in a storage.
 */
final class AggregateRecords {

    /**
     * Prevents this utility from instantiation.
     */
    private AggregateRecords() {
    }

    /**
     * Creates a new record for the event emitted by an {@code Aggregate}.
     *
     * @param aggregateId
     *         identifier of the aggregate
     * @param event
     *         event to transform into a record
     * @param <I>
     *         type of Aggregate identifiers
     * @return a new record
     */
    static <I> AggregateEventRecord newEventRecord(I aggregateId, Event event) {
        checkNotNull(aggregateId);
        checkNotNull(event);
        checkArgument(event.hasContext(), "Event context must be set.");
        checkArgument(event.hasMessage(), "Event message must be set.");

        var eventIdStr = Identifier.toString(event.getId());
        checkNotEmptyOrBlank(eventIdStr, "Event ID cannot be empty or blank.");

        var context = event.context();
        var timestamp = checkValid(context.getTimestamp());
        var packedId = Identifier.pack(aggregateId);

        var recordId = eventRecordId(eventIdStr);
        var result = AggregateEventRecord.newBuilder()
                .setId(recordId)
                .setAggregateId(packedId)
                .setTimestamp(timestamp)
                .setEvent(event)
                .build();
        return result;
    }

    /**
     * Creates a new record for the snapshot of the {@code Aggregate}.
     *
     * @param aggregateId
     *         identifier of the aggregate
     * @param snapshot
     *         snapshot to transform into a record
     * @param <I>
     *         type of Aggregate identifiers
     * @return a new record
     */
    static <I> AggregateEventRecord newEventRecord(I aggregateId, Snapshot snapshot) {
        checkNotNull(aggregateId);
        checkNotNull(snapshot);
        var value = checkValid(snapshot.getTimestamp());

        var stringId = Stringifiers.toString(aggregateId);
        var snapshotTimestamp = Timestamps.toString(snapshot.getTimestamp());
        var snapshotColumnName = AggregateEventRecordColumn.snapshot.name().value();
        var snapshotId = format("%s_%s_%s", snapshotColumnName, stringId, snapshotTimestamp);
        var recordId = eventRecordId(snapshotId);
        var result = AggregateEventRecord.newBuilder()
                .setId(recordId)
                .setAggregateId(Identifier.pack(aggregateId))
                .setTimestamp(value)
                .setSnapshot(snapshot)
                .build();
        return result;
    }

    /**
     * Creates a new record to store the {@code Aggregate} state.
     *
     * @param aggregate
     *         an instance of the aggregate
     * @param includeState
     *         whether the {@linkplain Aggregate#state() business state}
     *         of the Aggregate should be stored
     * @param <I>
     *         type of Aggregate identifiers
     * @return a new record
     */
    static <I> EntityRecord newStateRecord(Aggregate<I, ?, ?> aggregate, boolean includeState) {
        checkNotNull(aggregate);

        var flags = aggregate.lifecycleFlags();
        var id = aggregate.id();
        var version = aggregate.version();

        var builder = EntityRecord.newBuilder()
                .setEntityId(Identifier.pack(id))
                .setLifecycleFlags(flags)
                .setVersion(version);
        if (includeState) {
            var state = aggregate.state();
            builder.setState(AnyPacker.pack(state));
        }
        return builder.build();
    }

    private static AggregateEventRecordId eventRecordId(String snapshotId) {
        return AggregateEventRecordId.newBuilder()
                                     .setValue(snapshotId)
                                     .build();
    }
}
