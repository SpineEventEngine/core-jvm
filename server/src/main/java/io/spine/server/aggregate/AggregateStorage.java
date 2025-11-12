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

import com.google.common.collect.ImmutableList;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;
import io.spine.annotation.Internal;
import io.spine.annotation.SPI;
import io.spine.base.AggregateState;
import io.spine.base.EntityState;
import io.spine.client.ResponseFormat;
import io.spine.client.TargetFilters;
import io.spine.core.Event;
import io.spine.core.Version;
import io.spine.query.EntityQuery;
import io.spine.server.ContextSpec;
import io.spine.server.entity.EntityRecord;
import io.spine.server.entity.storage.EntityRecordStorage;
import io.spine.server.entity.storage.ToEntityRecordQuery;
import io.spine.server.storage.AbstractStorage;
import io.spine.server.storage.QueryConverter;
import io.spine.server.storage.RecordWithColumns;
import io.spine.server.storage.StorageFactory;
import org.jspecify.annotations.Nullable;

import java.util.Iterator;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.server.aggregate.AggregateRecords.newEventRecord;
import static io.spine.server.aggregate.AggregateRepository.DEFAULT_SNAPSHOT_TRIGGER;
import static io.spine.server.storage.QueryConverter.convert;
import static io.spine.util.Exceptions.newIllegalStateException;

/**
 * A storage of aggregate events, snapshots and the most recent aggregate states.
 *
 * <p>The instances of this type solve two problems.
 *
 * <ol>
 *     <li>efficient loading of an Aggregate instance from its events;
 *
 *     <li>storing the latest state of an Aggregate along with its lifecycle flags to allow
 *     its further querying.
 * </ol>
 *
 * <h1>Storing Aggregate events</h1>
 *
 * <p>Each Aggregate is an event-sourced Entity. To load an Aggregate instance, one plays all
 * the events emitted by it, eventually obtaining the last known state. While the Event Store
 * of a Bounded Context, to which some Aggregate belongs, stores all domain events, using it
 * for the sake of loading an Aggregate is inefficient in most cases. An overwhelming number of
 * the domain events emitted in a Bounded Context and the restrictions applied by an underlying
 * storage tools make searching for the events of a particular Aggregate a difficult task, given
 * that the events are constantly being appended to the respective Event Store.
 *
 * <p>This storage duplicates a portion of events emitted by the Aggregates of a certain type.
 * It dramatically narrows down the number of storage records to traverse when loading.
 * Additionally, this storage is responsible for storing the intermediate snapshots of Aggregate
 * states, incorporating them into an optimized loading routines. The history of an Aggregate
 * is being read from the most recent event till its most recent snapshot, which reduces
 * the I/O between the underlying storage and application even more and enhances the overall
 * system performance.
 *
 * <p>In order to store events and snapshots, an intermediate {@link AggregateEventStorage} is
 * created. It persists data as Protobuf message records in its pre-configured
 * {@link io.spine.server.storage.RecordStorage RecordStorage}.
 *
 * <h1>Storing and querying the latest Aggregate states</h1>
 *
 * <p>End-users of the framework are able to set the visibility level for each Aggregate state
 * by using an {@linkplain io.spine.server.entity.EntityVisibility (entity).visibility} option
 * in the Protobuf message corresponding to the Aggregate state. For those Aggregates which are
 * visible, the framework routines {@linkplain #enableStateQuerying() enable this storage}
 * to persist the latest states of Aggregates and allow their further
 * {@linkplain #readStates(TargetFilters, ResponseFormat) querying}. To some extent, it makes this
 * storage a part of an application's read-side.
 *
 * <p>Similar to storages of other Entity types, {@code AggregateStorage} supports querying
 * the Aggregate states by the values of their declared entity columns. See {@code io.spine.query}
 * package docs for more details on the query language.
 *
 * <p>However, even if the Aggregate visibility is set to
 * {@link io.spine.option.EntityOption.Visibility#NONE NONE}, the storage still persists
 * the essential bits of Aggregate as-an-Entity. Namely, its identifier, its lifecycle flags
 * and version. Such a behavior allows speeding up the execution of calls such
 * as {@linkplain #index() obtaining an index} of Aggregate identifiers, which otherwise would
 * involve major scans of event storage, with {@code DISTINCT} group operation applied.
 *
 * <p>As long as the Aggregate states are no different in their persistence from other Entities,
 * the {@code AggregateStorage} uses an intermediate {@link EntityRecordStorage} by delegating
 * all state-related operations to it.
 *
 * @param <I>
 *         the type of IDs of aggregates served by this storage
 * @param <S>
 *         the type of states of aggregates served by this storage
 *
 */
@SPI
public class AggregateStorage<I, S extends AggregateState<I>>
        extends AbstractStorage<I, AggregateHistory> {

    private static final String TRUNCATE_ON_WRONG_SNAPSHOT_MESSAGE =
            "The specified snapshot index `%d` must be non-negative.";

    /**
     * Stores the events and snapshots for the served Aggregates.
     */
    private final AggregateEventStorage eventStorage;

    /**
     * If enabled, stores the latest states of Aggregates.
     */
    private final EntityRecordStorage<I, S> stateStorage;

    /**
     * Tells whether the latest aggregate states should be stored and exposed for querying.
     */
    private boolean queryingEnabled = false;

    /**
     * A method object performing a truncation of an Aggregate history.
     */
    private final TruncateOperation truncation;

    /**
     * A method object reading the Aggregate event history.
     */
    private final HistoryBackwardOperation<I> historyBackward;

    /**
     * Creates an instance of the storage for a certain aggregate class registered
     * in a specific context.
     *
     * @param context
     *         the specification of the context within which this storage is being created
     * @param aggregateClass
     *         the class of stored aggregates
     * @param factory
     *         a storage factory to create the underlying storages for event/snapshot records
     *         and aggregate states
     */
    public AggregateStorage(ContextSpec context,
                            Class<? extends Aggregate<I, S, ?>> aggregateClass,
                            StorageFactory factory) {
        super(context.isMultitenant());
        eventStorage = factory.createAggregateEventStorage(context);
        stateStorage = factory.createEntityRecordStorage(context, aggregateClass);
        truncation = new TruncateOperation(eventStorage);
        historyBackward = new HistoryBackwardOperation<>(eventStorage);
    }

    protected AggregateStorage(AggregateStorage<I, S> delegate) {
        super(delegate.isMultitenant());
        this.eventStorage = delegate.eventStorage;
        this.stateStorage = delegate.stateStorage;
        this.queryingEnabled = delegate.queryingEnabled;
        this.truncation = delegate.truncation;
        this.historyBackward = delegate.historyBackward;
    }

    /**
     * Enables this storage to persist the latest Aggregate states and allows their querying.
     */
    void enableStateQuerying() {
        queryingEnabled = true;
    }

    /**
     * Returns an iterator over identifiers of Aggregates served by this storage.
     *
     * <p>The results include IDs corresponding only to those Aggregate instances which were
     * {@linkplain #writeState(Aggregate) explicitly written} to this storage. The Aggregate
     * identifiers which are included into the persisted events are not taken into account
     * by this method due to a performance considerations, as too many event records
     * must be scanned and de-duplicated for this.
     */
    @Override
    public Iterator<I> index() {
        return stateStorage.index();
    }

    /**
     * Forms and returns an {@link AggregateHistory} based on the
     * {@linkplain #historyBackward(Object, int) aggregate history}.
     *
     * @param id
     *         the identifier of the aggregate for which to return the history
     * @param batchSize
     *         the maximum number of the events to read
     * @return the record instance or {@code Optional.empty()} if the
     *         {@linkplain #historyBackward(Object, int) aggregate history} is empty
     * @throws IllegalStateException
     *         if the storage was closed before
     */
    @SuppressWarnings("CheckReturnValue") // calling builder method
    public Optional<AggregateHistory> read(I id, int batchSize) {
        var op = new ReadOperation<>(this, id, batchSize);
        return op.perform();
    }

    @Override
    public Optional<AggregateHistory> read(I id) {
        return read(id, DEFAULT_SNAPSHOT_TRIGGER);
    }

    /**
     * Writes events into the storage.
     *
     * <p><b>NOTE</b>: This method does not rewrite any events, just appends them. Many events
     * can be associated with a single aggregate ID.
     *
     * @param id
     *         the ID for the record
     * @param events
     *         non-empty piece of {@code AggregateHistory} to store
     */
    @Override
    public void write(I id, AggregateHistory events) {
        checkNotClosedAndArguments(id, events);

        var eventList = events.getEventList();
        checkArgument(!eventList.isEmpty(), "Event list must not be empty.");

        for (var event : eventList) {
            var record = newEventRecord(id, event);
            writeEventRecord(id, record);
        }
        if (events.hasSnapshot()) {
            writeSnapshot(id, events.getSnapshot());
        }
    }

    /**
     * Writes an event to the storage by an aggregate ID.
     *
     * <p>Before storing, all {@linkplain io.spine.core.Event#clearEnrichments() enrichments}
     * are removed from the passed event.
     *
     * @param id
     *         the aggregate ID
     * @param event
     *         the event to write
     */
    void writeEvent(I id, Event event) {
        checkNotClosedAndArguments(id, event);

        var eventWithoutEnrichments = event.clearEnrichments();
        var record = newEventRecord(id, eventWithoutEnrichments);
        writeEventRecord(id, record);
    }

    /**
     * Writes a {@code snapshot} by an {@code aggregateId} to the storage.
     *
     * @param aggregateId
     *         an ID of an aggregate of which the snapshot is made
     * @param snapshot
     *         the snapshot of the aggregate
     */
    void writeSnapshot(I aggregateId, Snapshot snapshot) {
        checkNotClosedAndArguments(aggregateId, snapshot);

        var record = newEventRecord(aggregateId, snapshot);
        writeEventRecord(aggregateId, record);
    }

    /**
     * Writes the passed record into the storage.
     *
     * @param id
     *         the aggregate ID
     * @param record
     *         the record to write
     */
    protected void writeEventRecord(I id, AggregateEventRecord record) {
        eventStorage.write(record);
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
        var query = convert(filters, format, stateStorage.recordSpec());
        var result = stateStorage.readAll(query);
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
        var query = QueryConverter.newQuery(stateStorage.recordSpec(), format);
        var result = stateStorage.readAll(query);
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
        var result = stateStorage.readAll(recordQuery);
        return result;
    }

    private void ensureStatesQueryable() {
        if (!queryingEnabled) {
            throw newIllegalStateException(
                    "The storage of Aggregate of type `%s` is not configured to store " +
                            "the latest Aggregate states. " +
                            "Check the entity visibility level of the Aggregate.",
                    stateClass());
        }
    }

    private Class<? extends EntityState<?>> stateClass() {
        return stateStorage.stateClass();
    }

    protected void writeState(Aggregate<I, ?, ?> aggregate) {
        var record = AggregateRecords.newStateRecord(aggregate, queryingEnabled);
        var result = RecordWithColumns.create(record, stateStorage.recordSpec());
        stateStorage.write(result);
    }

    protected void writeAll(Aggregate<I, ?, ?> aggregate,
                            ImmutableList<AggregateHistory> historySegments) {
        for (var history : historySegments) {
            write(aggregate.id(), history);
        }
        writeState(aggregate);
    }

    /**
     * Creates an iterator by the Aggregate event history, ordering the items
     * from the newer to older.
     *
     * <p>The iterator is empty if there's no history for the aggregate with passed ID.
     *
     * @param id
     *         the identifier of the Aggregate
     * @param batchSize
     *         the maximum number of the history records to read
     * @return new iterator instance
     */
    Iterator<AggregateEventRecord> historyBackward(I id, int batchSize) {
        return historyBackward(id, batchSize, null);
    }

    /**
     * Acts similar to {@link #historyBackward(Object, int) historyBackward(id, batchSize)},
     * but also allows to set the Aggregate version to start the reading from.
     *
     * @param id
     *         identifier of the Aggregate
     * @param batchSize
     *         the maximum number of the history records to read
     * @param startingFrom
     *         an Aggregate version from which the historical events are read
     * @return a new instance of iterator over the results
     */
    protected Iterator<AggregateEventRecord>
    historyBackward(I id, int batchSize, @Nullable Version startingFrom) {
        var original = historyBackward.read(id, batchSize, startingFrom);
        var copied = ImmutableList.copyOf(original);
        return copied.iterator();
    }

    /**
     * Truncates the storage, dropping all records which occur before the N-th snapshot for each
     * entity.
     *
     * <p>The snapshot index is counted from the latest to earliest, with {@code 0} representing
     * the latest snapshot.
     *
     * <p>If the passed value of snapshot index is higher than the overall snapshot count of
     * the Aggregate, this method does nothing.
     *
     * @throws IllegalArgumentException
     *         if the {@code snapshotIndex} is negative
     */
    @Internal
    public void truncateOlderThan(int snapshotIndex) {
        checkArgument(snapshotIndex >= 0, TRUNCATE_ON_WRONG_SNAPSHOT_MESSAGE);
        doTruncate(snapshotIndex);
    }

    /**
     * Truncates the storage, dropping all records which are older than both the passed {@code date}
     * and N-th snapshot.
     *
     * <p>The snapshot index is counted from the latest to earliest, with {@code 0} representing
     * the latest snapshot for each entity.
     *
     * <p>If the passed value of snapshot index is higher than the overall snapshot count of
     * the Aggregate, this method does nothing.
     *
     * @throws IllegalArgumentException
     *         if the {@code snapshotIndex} is negative
     */
    @Internal
    @SuppressWarnings("LenientFormatStringValidation")
    public void truncateOlderThan(int snapshotIndex, Timestamp date) {
        checkNotNull(date);
        checkArgument(snapshotIndex >= 0, TRUNCATE_ON_WRONG_SNAPSHOT_MESSAGE, snapshotIndex);
        doTruncate(snapshotIndex, date);
    }

    /**
     * Drops all records which occur before the N-th snapshot for each entity.
     */
    protected void doTruncate(int snapshotIndex) {
        truncation.performWith(snapshotIndex, (r) -> true);
    }

    /**
     * Drops all records older than {@code date} but not newer than the N-th snapshot for each
     * entity.
     */
    protected void doTruncate(int snapshotIndex, Timestamp date) {
        truncation.performWith(snapshotIndex,
                               (r) -> Timestamps.compare(r.getTimestamp(), date) < 0);
    }

    /**
     * This method exposes {@linkplain #checkNotClosed() checkNotClosed()} part of API to
     * this package.
     */
    void ensureNotClosed() {
        checkNotClosed();
    }

    private void checkNotClosedAndArguments(I id, Object argument) {
        checkNotClosed();
        checkNotNull(id);
        checkNotNull(argument);
    }
}
