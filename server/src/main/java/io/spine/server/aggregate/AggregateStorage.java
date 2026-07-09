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
import com.google.common.collect.Lists;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;
import io.spine.annotation.Internal;
import io.spine.annotation.SPI;
import io.spine.annotation.VisibleForTesting;
import io.spine.base.AggregateState;
import io.spine.base.EntityState;
import io.spine.client.ResponseFormat;
import io.spine.client.TargetFilters;
import io.spine.core.Event;
import io.spine.core.Version;
import io.spine.query.EntityQuery;
import io.spine.server.ContextSpec;
import io.spine.server.entity.EntityEventHistory;
import io.spine.server.entity.EntityEventRecord;
import io.spine.server.entity.EntityRecord;
import io.spine.server.entity.storage.EntityEventRecords;
import io.spine.server.entity.storage.EntityEventStorage;
import io.spine.server.entity.storage.EntityRecordStorage;
import io.spine.server.entity.storage.ToEntityRecordQuery;
import io.spine.server.storage.AbstractStorage;
import io.spine.server.storage.QueryConverter;
import io.spine.server.storage.RecordWithColumns;
import io.spine.server.storage.StorageFactory;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;
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
 *     the source of truth for {@linkplain AggregateRepository#load(Object) loading} an Aggregate;
 *
 *     <li>journaling the events emitted by the Aggregates — for traceability and for
 *     the recent-history lookups such as {@link Aggregate#historyBackward(int)} and
 *     the opt-in {@link IdempotencyGuard}.
 * </ol>
 *
 * <h2>Storing and querying the latest Aggregate states</h2>
 *
 * <p>The state of an Aggregate is persisted on each update, regardless of the
 * {@linkplain io.spine.server.entity.EntityVisibility visibility} of the Aggregate state type.
 * Similar to storages of other Entity types, the states are persisted as {@link EntityRecord}s
 * in an intermediate {@link EntityRecordStorage}, to which all state-related operations
 * are delegated.
 *
 * <p>End-users of the framework are able to set the visibility level for each Aggregate state
 * by using an {@linkplain io.spine.server.entity.EntityVisibility (entity).visibility} option
 * in the Protobuf message corresponding to the Aggregate state. For those Aggregates that are
 * visible, the framework routines {@linkplain #enableStateQuerying() enable this storage}
 * to expose the latest states of Aggregates for further
 * {@linkplain #readStates(TargetFilters, ResponseFormat) querying}. To some extent, it makes this
 * storage a part of an application's read-side. Visibility gates only this query exposure —
 * never the state {@linkplain #writeState(Aggregate) writes} or the
 * {@linkplain #readState(Object) load-path reads}.
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
 * <h2>Legacy journal</h2>
 *
 * <p>Before the event-sourcing cutover, the journal was persisted as
 * {@link AggregateEventRecord}s — a record kind that could also hold {@link Snapshot}s.
 * Such records are not visible to the reads performed by this storage. The deprecated
 * {@linkplain #truncateOlderThan(int) snapshot-index truncation} keeps operating on them,
 * so the leftover data can still be trimmed.
 *
 * @param <I>
 *         the type of IDs of aggregates served by this storage
 * @param <S>
 *         the type of states of aggregates served by this storage
 */
@SPI
public class AggregateStorage<I, S extends AggregateState<I>>
        extends AbstractStorage<I, EntityEventHistory> {

    private static final String TRUNCATE_ON_WRONG_SNAPSHOT_MESSAGE =
            "The specified snapshot index `%d` must be non-negative.";

    /**
     * Stores the events emitted by the served Aggregates.
     */
    private final EntityEventStorage eventStorage;

    /**
     * Stores the legacy journal records written before the event-sourcing cutover.
     *
     * <p>Serves only the deprecated {@linkplain #truncateOlderThan(int) snapshot-index
     * truncation}; the current runtime neither writes to this storage nor otherwise
     * reads from it.
     */
    @SuppressWarnings("deprecation") // Kept on purpose to serve the legacy journal.
    private final AggregateEventStorage legacyEventStorage;

    /**
     * If enabled, stores the latest states of Aggregates.
     */
    private final EntityRecordStorage<I, S> stateStorage;

    /**
     * Tells whether the latest aggregate states should be stored and exposed for querying.
     */
    private boolean queryingEnabled = false;

    /**
     * A method object performing the truncation of the legacy journal.
     */
    private final TruncateOperation truncation;

    /**
     * Creates an instance of the storage for a certain aggregate class registered
     * in a specific context.
     *
     * @param context
     *         the specification of the context within which this storage is being created
     * @param aggregateClass
     *         the class of stored aggregates
     * @param factory
     *         a storage factory to create the underlying storages for the event journal
     *         and aggregate states
     */
    @SuppressWarnings("deprecation") /* Creates the legacy journal storage on purpose,
        to serve the deprecated truncation. */
    public AggregateStorage(ContextSpec context,
                            Class<? extends Aggregate<I, S, ?>> aggregateClass,
                            StorageFactory factory) {
        super(context.isMultitenant());
        eventStorage = factory.createEntityEventStorage(context);
        legacyEventStorage = factory.createAggregateEventStorage(context);
        stateStorage = factory.createEntityRecordStorage(context, aggregateClass);
        truncation = new TruncateOperation(legacyEventStorage);
    }

    protected AggregateStorage(AggregateStorage<I, S> delegate) {
        super(delegate.isMultitenant());
        this.eventStorage = delegate.eventStorage;
        this.legacyEventStorage = delegate.legacyEventStorage;
        this.stateStorage = delegate.stateStorage;
        this.queryingEnabled = delegate.queryingEnabled;
        this.truncation = delegate.truncation;
    }

    /**
     * Enables the querying of the latest Aggregate states persisted by this storage.
     */
    void enableStateQuerying() {
        queryingEnabled = true;
    }

    /**
     * Returns an iterator over identifiers of Aggregates served by this storage.
     *
     * <p>The results include IDs corresponding only to those Aggregate instances whose state
     * was {@linkplain #writeState(Aggregate) written} to this storage. Starting with Spine 2.0,
     * the framework writes the essential bits of every Aggregate — its identifier, lifecycle
     * flags, and version — to this storage on each update, regardless of the
     * {@linkplain io.spine.option.EntityOption.Visibility visibility} of the Aggregate state.
     * Therefore, for the data produced by the current framework version, this index returns all
     * the Aggregates of the served type.
     *
     * <p>The identifiers found only in the persisted events are <em>not</em> taken into account,
     * as obtaining them would require scanning and de-duplicating too many event records.
     * As a consequence, an Aggregate persisted by an older framework version is not returned
     * until its state is written to this storage. For an Aggregate that still has a record in
     * the now-deprecated {@link io.spine.system.server.Mirror Mirror} projection, run the
     * {@link io.spine.server.migration.mirror.MirrorMigration Mirror migration}, which writes
     * the migrated states into this storage. An Aggregate that exists only as events — with
     * neither a state record nor a {@code Mirror} record — is picked up once its state is next
     * {@linkplain #writeState(Aggregate) written}, for example, on its next update.
     */
    @Override
    public Iterator<I> index() {
        return stateStorage.index();
    }

    /**
     * Reads the most recent events emitted by the aggregate with the passed ID and returns
     * them as an {@link EntityEventHistory}, listing the events in the order of emission.
     *
     * @param id
     *         the identifier of the aggregate for which to return the recent history
     * @param batchSize
     *         the maximum number of the events to read
     * @return the recent history, or {@code Optional.empty()} if the journal has no events
     *         emitted by the aggregate
     * @throws IllegalStateException
     *         if the storage was closed before
     * @throws IllegalArgumentException
     *         if the {@code batchSize} is not positive
     */
    public Optional<EntityEventHistory> read(I id, int batchSize) {
        checkNotClosedAndArguments(id, batchSize);
        checkPositive(batchSize);
        var events = readHistoryBackward(id, batchSize);
        if (events.isEmpty()) {
            return Optional.empty();
        }
        var history = EntityEventHistory.newBuilder()
                .addAllEvent(Lists.reverse(events))
                .build();
        return Optional.of(history);
    }

    @Override
    public Optional<EntityEventHistory> read(I id) {
        return read(id, DEFAULT_HISTORY_DEPTH);
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
     *         non-empty piece of the event history to store
     */
    @Override
    public void write(I id, EntityEventHistory events) {
        checkNotClosedAndArguments(id, events);

        var eventList = events.getEventList();
        checkArgument(!eventList.isEmpty(), "Event list must not be empty.");

        for (var event : eventList) {
            var record = EntityEventRecords.create(id, event);
            writeEventRecord(id, record);
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
        var record = EntityEventRecords.create(id, eventWithoutEnrichments);
        writeEventRecord(id, record);
    }

    /**
     * Writes the passed record into the storage.
     *
     * @param id
     *         the aggregate ID
     * @param record
     *         the record to write
     */
    protected void writeEventRecord(I id, EntityEventRecord record) {
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

    /**
     * Reads the latest persisted state record of the aggregate with the passed ID.
     *
     * <p>Since the event-sourcing cutover this record is the source of truth for
     * {@linkplain AggregateRepository#load(Object) loading} an aggregate. Unlike the
     * {@link #readStates(ResponseFormat) query reads}, this method is <em>not</em> gated by the
     * querying/visibility check — the state must be readable even for {@code NONE}-visibility
     * aggregates, which never expose their states for client querying.
     *
     * @param id
     *         the identifier of the aggregate
     * @return the latest state record, or {@code Optional.empty()} if the aggregate has never
     *         been stored
     */
    Optional<EntityRecord> readState(I id) {
        return stateStorage.read(id);
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

    private Class<? extends EntityState<?>> stateClass() {
        return stateStorage.stateClass();
    }

    protected void writeState(Aggregate<I, ?, ?> aggregate) {
        var record = AggregateRecords.newStateRecord(aggregate);
        var result = RecordWithColumns.create(record, stateStorage.recordSpec());
        stateStorage.write(result);
    }

    /**
     * Writes the uncommitted history of the aggregate: appends the emitted events to
     * the journal and stores the latest state.
     *
     * @param aggregate
     *         the aggregate to store
     * @param history
     *         the events emitted by the aggregate since it was stored last time;
     *         may be empty if the state was modified without emitting events
     */
    protected void writeAll(Aggregate<I, ?, ?> aggregate, EntityEventHistory history) {
        if (!history.getEventList().isEmpty()) {
            write(aggregate.id(), history);
        }
        writeState(aggregate);
    }

    /**
     * Creates an iterator over the journal of the Aggregate, ordering the records
     * from newer to older.
     *
     * <p>The iterator is empty if there's no journaled history for the aggregate with passed ID.
     *
     * @param id
     *         the identifier of the Aggregate
     * @param batchSize
     *         the maximum number of the history records to read
     * @return new iterator instance
     */
    Iterator<EntityEventRecord> historyBackward(I id, int batchSize) {
        return historyBackward(id, batchSize, null);
    }

    /**
     * Reads up to {@code depth} most recent events of the aggregate's journal, newest first.
     *
     * <p>Used to lazily load recent history for the opt-in {@link IdempotencyGuard} and for
     * business access via {@link Aggregate#historyBackward(int)}.
     *
     * <p>Only the events journaled by Spine 2.0 and later are read; the journal records
     * persisted by the earlier, event-sourced versions of the framework are a separate legacy
     * record kind, not visible to this method.
     *
     * @param id
     *         the identifier of the aggregate
     * @param depth
     *         the maximum number of the most recent events to read
     * @return the most recent events, newest first
     */
    List<Event> readHistoryBackward(I id, int depth) {
        var records = historyBackward(id, depth);
        List<Event> events = new ArrayList<>();
        while (records.hasNext()) {
            var record = records.next();
            events.add(record.getEvent());
        }
        return events;
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
    protected Iterator<EntityEventRecord>
    historyBackward(I id, int batchSize, @Nullable Version startingFrom) {
        var original = eventStorage.historyBackward(id, batchSize, startingFrom);
        var copied = ImmutableList.copyOf(original);
        return copied.iterator();
    }

    /**
     * Truncates the legacy journal, dropping all records which occur before the N-th snapshot
     * for each entity.
     *
     * <p>The snapshot index is counted from the latest to earliest, with {@code 0} representing
     * the latest snapshot.
     *
     * <p>If the passed value of snapshot index is higher than the overall snapshot count of
     * the Aggregate, this method does nothing.
     *
     * @throws IllegalArgumentException
     *         if the {@code snapshotIndex} is negative
     * @deprecated This method operates only on the journal records left by the earlier,
     *         event-sourced versions of the framework. The current journal contains no
     *         snapshots, so there is nothing for this method to truncate in it.
     */
    @Internal
    @Deprecated
    public void truncateOlderThan(int snapshotIndex) {
        checkArgument(snapshotIndex >= 0, TRUNCATE_ON_WRONG_SNAPSHOT_MESSAGE);
        doTruncate(snapshotIndex);
    }

    /**
     * Truncates the legacy journal, dropping all records which are older than both the passed
     * {@code date} and N-th snapshot.
     *
     * <p>The snapshot index is counted from the latest to earliest, with {@code 0} representing
     * the latest snapshot for each entity.
     *
     * <p>If the passed value of snapshot index is higher than the overall snapshot count of
     * the Aggregate, this method does nothing.
     *
     * @throws IllegalArgumentException
     *         if the {@code snapshotIndex} is negative
     * @deprecated This method operates only on the journal records left by the earlier,
     *         event-sourced versions of the framework. The current journal contains no
     *         snapshots, so there is nothing for this method to truncate in it.
     */
    @Internal
    @Deprecated
    @SuppressWarnings("LenientFormatStringValidation")
    public void truncateOlderThan(int snapshotIndex, Timestamp date) {
        checkNotNull(date);
        checkArgument(snapshotIndex >= 0, TRUNCATE_ON_WRONG_SNAPSHOT_MESSAGE, snapshotIndex);
        doTruncate(snapshotIndex, date);
    }

    /**
     * Drops all legacy journal records which occur before the N-th snapshot for each entity.
     *
     * @deprecated See {@link #truncateOlderThan(int)}.
     */
    @Deprecated
    protected void doTruncate(int snapshotIndex) {
        truncation.performWith(snapshotIndex, (r) -> true);
    }

    /**
     * Drops all legacy journal records older than {@code date} but not newer than the N-th
     * snapshot for each entity.
     *
     * @deprecated See {@link #truncateOlderThan(int, Timestamp)}.
     */
    @Deprecated
    protected void doTruncate(int snapshotIndex, Timestamp date) {
        truncation.performWith(snapshotIndex,
                               (r) -> Timestamps.compare(r.getTimestamp(), date) < 0);
    }

    /**
     * Obtains the storage of the legacy journal records for the tests of their truncation.
     *
     * @deprecated Exists only until the legacy truncation is removed.
     */
    @Deprecated
    @VisibleForTesting
    AggregateEventStorage legacyJournal() {
        return legacyEventStorage;
    }

    private void checkNotClosedAndArguments(I id, Object argument) {
        checkNotClosed();
        checkNotNull(id);
        checkNotNull(argument);
    }
}
