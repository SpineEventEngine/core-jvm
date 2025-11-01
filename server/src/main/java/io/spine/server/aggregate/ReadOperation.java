/*
 * Copyright 2025, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
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

import io.spine.base.AggregateState;
import io.spine.core.Event;
import io.spine.core.Version;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

import java.util.Deque;
import java.util.Iterator;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Lists.newLinkedList;
import static io.spine.type.ProtoTexts.shortDebugString;
import static io.spine.util.Exceptions.newIllegalStateException;
import static io.spine.util.Preconditions2.checkPositive;

/**
 * Method object for reading {@link AggregateHistory}s.
 *
 * @param <I>
 *         the type of aggregate IDs
 * @param <S>
 *         the type of aggregate state
 */
final class ReadOperation<I, S extends AggregateState<I>> {

    private final AggregateStorage<I, S> storage;
    private final Deque<Event> history;
    private final I id;
    private final int batchSize;

    private @MonotonicNonNull Snapshot snapshot = null;

    /**
     * Creates a new history read operation for the {@code Aggregate} history
     * with the given identifier.
     *
     * @param storage
     *         the storage to use for reading
     * @param id
     *         the identifier of the {@code Aggregate} instance
     * @param batchSize
     *         how many records to read from the storage at a time
     */
    ReadOperation(AggregateStorage<I, S> storage, I id, int batchSize) {
        storage.ensureNotClosed();
        this.storage = storage;
        this.id = checkNotNull(id);
        checkPositive(batchSize);
        this.batchSize = batchSize;
        this.history = newLinkedList();
    }

    /**
     * Reads the history of the {@code Aggregate} starting from the most recent events until
     * either the snapshot is read or the bottom of the history is reached.
     *
     * <p>The reading is performed in batches, which size is determined by
     * the {@linkplain ReadOperation#ReadOperation(AggregateStorage, Object, int) pre-configured}
     * batch size.
     *
     * <p>If neither snapshot nor the bottom of the history is reached when the batch
     * is read, a new batch of records is read from the storage starting from the version of
     * the last record read in the previous batch.
     *
     * @return the {@code Aggregate} history,
     *         or {@code Optional.empty()} if this {@code Aggregate} has no history
     */
    Optional<AggregateHistory> perform() {
        var historyBackward = storage.historyBackward(id, batchSize);
        if (!historyBackward.hasNext()) {
            return Optional.empty();
        }

        var historyBottomReached = false;
        while(snapshot == null && !historyBottomReached) {
            var lastVersion = process(historyBackward);
            if(snapshot == null) {
                if(lastVersion.isEmpty()) {
                    historyBottomReached = true;
                } else {
                    historyBackward = storage.historyBackward(id, batchSize, lastVersion.get());
                }
            }
        }

        var result = buildRecord();
        return Optional.of(result);
    }

    /**
     * Handles the records provided by the history-backward iterator and return the version
     * of the last record handled.
     *
     * @return the version of the last handled event or snapshot, whichever is the last;
     * or {@code Optional.empty} if no records were handled
     */
    private Optional<Version> process(Iterator<AggregateEventRecord> historyBackward) {
        Version lastHandledVersion = null;
        while(historyBackward.hasNext() && snapshot == null) {
            var record = historyBackward.next();
            lastHandledVersion = handleRecord(record);
        }
        return Optional.ofNullable(lastHandledVersion);
    }

    private Version handleRecord(AggregateEventRecord record) {
        switch (record.getKindCase()) {
            case EVENT:
                history.addFirst(record.getEvent());
                return record.getEvent().getContext().getVersion();
            case SNAPSHOT:
                snapshot = record.getSnapshot();
                return snapshot.getVersion();
            case KIND_NOT_SET:
            default:
                throw newIllegalStateException("Event or snapshot missing in record: \"%s\"",
                                               shortDebugString(record));
        }
    }


    @SuppressWarnings("ResultOfMethodCallIgnored") // calling builder
    private AggregateHistory buildRecord() {
        var builder = AggregateHistory.newBuilder();
        if (snapshot != null) {
            builder.setSnapshot(snapshot);
        }
        builder.addAllEvent(history);

        var result = builder.build();
        checkRecord(result);
        return result;
    }

    /**
     * Ensures that the {@link AggregateHistory} is valid.
     *
     * <p>{@link AggregateHistory} is considered valid when one of the following is true:
     * <ul>
     *     <li>{@linkplain AggregateHistory#getSnapshot() snapshot} is not default;
     *     <li>{@linkplain AggregateHistory#getEventList() event list} is not empty.
     * </ul>
     *
     * @param record the record to check
     * @throws IllegalStateException if the {@link AggregateHistory} is not valid
     */
    private static void checkRecord(AggregateHistory record) throws IllegalStateException {
        var snapshotIsNotSet = !record.hasSnapshot();
        var noEvents = record.getEventList().isEmpty();
        if (noEvents && snapshotIsNotSet) {
            throw new IllegalStateException("AggregateStateRecord instance should have either "
                                                    + "snapshot or non-empty event list.");
        }
    }
}
