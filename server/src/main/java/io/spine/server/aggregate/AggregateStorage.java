/*
 * Copyright 2026, TeamDev. All rights reserved.
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

import com.google.common.collect.ImmutableList;
import com.google.protobuf.Timestamp;
import io.spine.annotation.SPI;
import io.spine.base.AggregateState;
import io.spine.client.ResponseFormat;
import io.spine.client.TargetFilters;
import io.spine.core.Event;
import io.spine.core.Version;
import io.spine.query.EntityQuery;
import io.spine.server.ContextSpec;
import io.spine.server.entity.EntityRecord;
import io.spine.server.entity.storage.EntityEventStorage;
import io.spine.server.entity.storage.EntityRecordStorage;
import io.spine.server.entity.storage.ToEntityRecordQuery;
import io.spine.server.storage.QueryConverter;
import io.spine.server.storage.RecordWithColumns;
import io.spine.server.storage.StorageFactory;
import org.jspecify.annotations.Nullable;

import java.util.Iterator;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.server.aggregate.AggregateRepository.DEFAULT_HISTORY_DEPTH;
import static io.spine.server.storage.QueryConverter.convert;
import static io.spine.util.Exceptions.newIllegalStateException;
import static io.spine.util.Preconditions2.checkPositive;

/**
 * A storage of the latest Aggregate states and the journal of the events emitted by Aggregates.
 *
 * <p>The instances of this type solve two problems:
 *
 * <ol>
 *     <li>storing the latest state of each Aggregate along with its lifecycle flags and version —
 *     the source of truth for loading an Aggregate by its {@link AggregateRepository};
 *
 *     <li>journaling the events emitted by the Aggregates — for traceability and for
 *     the recent-history lookups such as {@link Aggregate#eventHistoryBackward(int)} and
 *     the opt-in {@link IdempotencyGuard}.
 * </ol>
 *
 * <h2>Storing and querying the latest Aggregate states</h2>
 *
 * <p>The state of an Aggregate is persisted on each update, regardless of the
 * {@linkplain io.spine.server.entity.EntityVisibility visibility} of the Aggregate state type.
 * This storage {@code extends} {@link EntityRecordStorage}, so the latest states are persisted
 * as {@link EntityRecord}s and all state-related operations are served by the inherited
 * record storage.
 *
 * <p>End-users of the framework are able to set the visibility level for each Aggregate state
 * by using an {@linkplain io.spine.server.entity.EntityVisibility (entity).visibility} option
 * in the Protobuf message corresponding to the Aggregate state. For those Aggregates that are
 * visible, the framework routines {@linkplain #enableStateQuerying() enable this storage}
 * to expose the latest states of Aggregates for further
 * {@linkplain #readStates(TargetFilters, ResponseFormat) querying}. To some extent, it makes this
 * storage a part of an application's read-side. Visibility gates only this query exposure —
 * never the state {@linkplain #writeState(Aggregate) writes} or the
 * {@linkplain #read(Object) load-path reads}.
 *
 * <p>{@code AggregateStorage} supports querying the Aggregate states by the values of their
 * declared entity columns. See {@code io.spine.query} package docs for more details on
 * the query language.
 *
 * <h2>Journal of the emitted events</h2>
 *
 * <p>The events emitted by the Aggregates are appended to an intermediate
 * {@link EntityEventStorage}. The journal is not used for restoring the Aggregate states.
 * It narrows down the number of records to traverse when the recent history of an Aggregate
 * is read, compared to searching the {@linkplain io.spine.server.event.EventStore Event Store}
 * of the whole Bounded Context.
 *
 * <p>The journal grows as the Aggregates emit events. To bound the growth, production
 * systems run the {@linkplain #truncate(Timestamp) time-based truncation} as a periodic
 * maintenance operation.
 *
 * <h2>Legacy journal</h2>
 *
 * <p>Before the event-sourcing cutover, the journal was persisted as
 * {@code AggregateEventRecord}s — a record kind that could also hold {@code Snapshot}s.
 * The current runtime neither writes nor reads records of that kind. They remain in the
 * underlying storage as inert data, and their proto definitions are retained so that
 * the persisted records stay parseable.
 *
 * @param <I>
 *         the type of IDs of aggregates served by this storage
 * @param <S>
 *         the type of states of aggregates served by this storage
 */
@SPI
public class AggregateStorage<I, S extends AggregateState<I>>
        extends EntityRecordStorage<I, S> {

    /**
     * Stores the events emitted by the served Aggregates.
     */
    private final EntityEventStorage<I> eventStorage;

    /**
     * Tells whether the latest aggregate states should be stored and exposed for querying.
     */
    private boolean queryingEnabled = false;

    /**
     * Creates an instance of the storage for a certain aggregate class registered
     * in a specific context.
     *
     * @param context
     *         the specification of the context within which this storage is being created
     * @param aggregateClass
     *         the class of stored aggregates
     * @param factory
     *         a storage factory to create the underlying storage for the event journal
     */
    public AggregateStorage(ContextSpec context,
                            Class<? extends Aggregate<I, S, ?>> aggregateClass,
                            StorageFactory factory) {
        super(context, factory, aggregateClass);
        eventStorage = factory.createEntityEventStorage(context, aggregateClass);
    }

    /**
     * Enables the querying of the latest Aggregate states persisted by this storage.
     */
    void enableStateQuerying() {
        queryingEnabled = true;
    }

    /**
     * Reads the most recent events emitted by the aggregate with the passed ID,
     * listing them in the order of emission.
     *
     * @param id
     *         the identifier of the aggregate for which to return the recent events
     * @param batchSize
     *         the maximum number of the events to read
     * @return the recent events in the order of emission, or an empty list if the journal
     *         has no events emitted by the aggregate
     * @throws IllegalStateException
     *         if the storage is closed
     * @throws IllegalArgumentException
     *         if the {@code batchSize} is not positive
     */
    public List<Event> readEvents(I id, int batchSize) {
        checkNotClosedAndArguments(id, batchSize);
        checkPositive(batchSize);
        // `eventHistoryBackward` yields the events newest-first; reverse them into
        // the order of emission.
        return ImmutableList.copyOf(eventHistoryBackward(id, batchSize)).reverse();
    }

    /**
     * Reads the most recent events emitted by the aggregate with the passed ID, using the
     * default history depth, and lists them in the order of emission.
     *
     * @param id
     *         the identifier of the aggregate for which to return the recent events
     * @return the recent events in the order of emission, or an empty list if the journal
     *         has no events emitted by the aggregate
     */
    public List<Event> readEvents(I id) {
        return readEvents(id, DEFAULT_HISTORY_DEPTH);
    }

    /**
     * Writes an event to the storage.
     *
     * <p>The journal storage {@linkplain io.spine.core.Event#clearEnrichments() removes
     * the enrichments} before storing and journals the event under its producer —
     * the aggregate which emitted it.
     *
     * @param id
     *         the ID of the aggregate that emitted the event
     * @param event
     *         the event to write
     */
    void writeEvent(I id, Event event) {
        checkNotClosedAndArguments(id, event);
        eventStorage.write(event);
    }

    /**
     * Queries the storage for the Aggregate states according to the passed filters and returns
     * the results in the specified response format.
     *
     * <p>The results of this call are eventually consistent with the latest states of aggregates,
     * as instances are <em>not</em> restored from their events for querying.
     * Instead, this method works on top of the storage of the latest known aggregate states,
     * for better performance. In a distributed environment, the records in this storage may be
     * outdated, as new events may have been emitted by an aggregate instance on other server nodes.
     *
     * @param filters
     *         the filters to use when querying the aggregate states
     * @param format
     *         the format of the response
     * @return an iterator over the matching {@code EntityRecord}s
     */
    protected Iterator<EntityRecord> readStates(TargetFilters filters, ResponseFormat format) {
        ensureStatesQueryable();
        var query = convert(filters, format, recordSpec());
        var result = readAll(query);
        return result;
    }

    /**
     * Reads the aggregate states from the storage and returns the results in the specified format.
     *
     * <p>This method performs no filtering. Other than that, it works in the same manner
     * as {@link #readStates(TargetFilters, ResponseFormat) readStates(filters, format)}.
     *
     * @param format
     *         the format of the response
     * @return an iterator over the records
     */
    protected Iterator<EntityRecord> readStates(ResponseFormat format) {
        ensureStatesQueryable();
        var query = QueryConverter.newQuery(recordSpec(), format);
        var result = readAll(query);
        return result;
    }

    /**
     * Reads the aggregate states from the storage according to the specified query.
     *
     * @param query
     *         the query to execute
     * @return an iterator over the records
     */
    protected Iterator<EntityRecord> readStates(EntityQuery<I, S, ?> query) {
        ensureStatesQueryable();
        var recordQuery = ToEntityRecordQuery.transform(query);
        var result = readAll(recordQuery);
        return result;
    }

    private void ensureStatesQueryable() {
        if (!queryingEnabled) {
            throw newIllegalStateException(
                    "The storage of Aggregate of type `%s` is not configured to expose " +
                            "the latest Aggregate states for querying. " +
                            "Check the entity visibility level of the Aggregate.",
                    stateClass());
        }
    }

    protected void writeState(Aggregate<I, ?, ?> aggregate) {
        var record = aggregate.toRecord();
        var result = RecordWithColumns.create(record, recordSpec());
        write(result);
    }

    /**
     * Writes the uncommitted history of the aggregate: appends the emitted events to
     * the journal and stores the latest state.
     *
     * @param aggregate
     *         the aggregate to store
     * @param events
     *         the events emitted by the aggregate since it was stored last time;
     *         may be empty if the state was modified without emitting events
     */
    protected void writeAll(Aggregate<I, ?, ?> aggregate, List<Event> events) {
        checkNotClosedAndArguments(aggregate.id(), events);
        for (var event : events) {
            eventStorage.write(event);
        }
        writeState(aggregate);
    }

    /**
     * Creates an iterator over the journal of the Aggregate, ordering the events
     * from newer to older.
     *
     * <p>The iterator is lazy, reading from the journal as it advances. It serves
     * the recent-history reads of the opt-in {@link IdempotencyGuard} and the
     * business access via {@link Aggregate#eventHistoryBackward(int)}.
     *
     * <p>The iterator is empty if there's no journaled history for the aggregate with passed ID.
     *
     * <p>Only the events journaled by Spine 2.0 and later are read; the journal records
     * persisted by the earlier, event-sourced versions of the framework are a separate legacy
     * record kind, not visible to this method.
     *
     * @param id
     *         the identifier of the Aggregate
     * @param batchSize
     *         the maximum number of the events to read
     * @return new iterator instance
     */
    Iterator<Event> eventHistoryBackward(I id, int batchSize) {
        return eventHistoryBackward(id, batchSize, null);
    }

    /**
     * Acts similar to
     * {@link #eventHistoryBackward(Object, int) eventHistoryBackward(id, batchSize)}, but also
     * allows setting the Aggregate version to start the reading from.
     *
     * @param id
     *         identifier of the Aggregate
     * @param batchSize
     *         the maximum number of the history records to read
     * @param startingFrom
     *         an Aggregate version from which the historical events are read
     * @return a new instance of iterator over the results
     */
    protected Iterator<Event>
    eventHistoryBackward(I id, int batchSize, @Nullable Version startingFrom) {
        return eventStorage.historyBackward(id, batchSize, startingFrom);
    }

    /**
     * Truncates the journal, deleting the events emitted before {@code olderThan}.
     *
     * <p>An event record is deleted if and only if its event was emitted strictly before
     * the given time. To purge the whole journal of the served Aggregates, pass the current
     * time — or any later moment.
     *
     * <p>Truncation bounds the {@linkplain Aggregate#eventHistoryBackward(int) recent history}
     * available to the business logic and to the opt-in {@link IdempotencyGuard}. When the
     * guard is {@linkplain AggregateRepository#useIdempotencyGuard() enabled}, choose a cutoff
     * old enough to retain the {@linkplain AggregateRepository#eventHistoryDepth() event history
     * depth} of the repository, so that the deduplication window stays intact.
     *
     * <p>The events are removed by a query on their creation time rather than by reading
     * the journal into memory, so the operation scales to large journals; it remains bulk
     * maintenance rather than a per-dispatch call.
     *
     * @param olderThan
     *         only the events emitted strictly before this time are deleted
     */
    public void truncate(Timestamp olderThan) {
        checkNotNull(olderThan);
        eventStorage.truncate(olderThan);
    }

    /**
     * Closes this storage, releasing the inherited record storage and the event journal.
     *
     * <p>Unlike a plain {@link EntityRecordStorage}, this storage owns an additional
     * {@link EntityEventStorage journal}, so closing it must also close the journal.
     *
     * @throws IllegalStateException
     *         if the storage was already closed
     */
    @Override
    public void close() {
        checkNotClosed();
        try {
            eventStorage.close();
        } finally {
            super.close();
        }
    }

    private void checkNotClosedAndArguments(I id, Object argument) {
        checkNotClosed();
        checkNotNull(id);
        checkNotNull(argument);
    }
}
