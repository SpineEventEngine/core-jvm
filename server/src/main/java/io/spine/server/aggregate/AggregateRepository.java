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

import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.OverridingMethodsMustInvokeSuper;
import com.google.protobuf.Timestamp;
import io.spine.annotation.Internal;
import io.spine.base.AggregateState;
import io.spine.server.aggregate.model.AggregateClass;
import io.spine.server.delivery.Inbox;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.entity.EntityRecord;
import io.spine.server.entity.EventProducingRepository;
import io.spine.server.entity.SignalDispatchingRepository;
import io.spine.server.entity.StateHistoryLoader;
import io.spine.server.entity.storage.EntityEventStorage;
import io.spine.server.entity.storage.EntityStateHistoryStorage;
import io.spine.server.event.EventBus;
import io.spine.server.route.CommandRouting;
import io.spine.server.type.CommandClass;
import io.spine.server.type.EventClass;
import io.spine.server.type.EventEnvelope;
import io.spine.server.type.SignalEnvelope;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Collection;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static io.spine.option.EntityOption.Kind.AGGREGATE;
import static io.spine.server.aggregate.model.AggregateClass.asAggregateClass;
import static io.spine.server.delivery.InboxLabel.HANDLE_COMMAND;
import static io.spine.server.delivery.InboxLabel.REACT_UPON_EVENT;
import static io.spine.server.dispatch.DispatchOutcomes.sentToInbox;
import static io.spine.util.Exceptions.newIllegalStateException;

/**
 * The repository that manages instances of {@code Aggregate}s.
 *
 * <p>The repository routes commands and events to its aggregates, stores the events they
 * produce, and posts those events to the {@link io.spine.server.event.EventBus EventBus}.
 *
 * <p>Since the event-sourcing cutover, an aggregate is loaded from its latest persisted
 * {@link io.spine.server.entity.EntityRecord EntityRecord} rather than by replaying its event
 * journal. The repository keeps that latest state in its record storage and appends
 * the emitted events to a separate {@linkplain #eventStorage() event journal}
 * (an {@link EntityEventStorage}), kept append-only for traceability and for the opt-in
 * {@link IdempotencyGuard}.
 *
 * <p>Three per-repository settings tune this behavior:
 * <ul>
 *     <li>{@link #useIdempotencyGuard()} — enables the journal-backed {@link IdempotencyGuard},
 *         which rejects a signal already seen among the last {@link #eventHistoryDepth()}
 *         dispatches (however long ago). It is <b>off by default</b> for performance — when
 *         enabled, every dispatch pays a bounded journal read. This is a mechanism distinct from
 *         the delivery layer's time-windowed deduplication, not a replacement for it.
 *     <li>{@linkplain #eventHistoryDepth() eventHistoryDepth} — how many recent journal events
 *         the guard scans on each dispatch when enabled (default {@value #DEFAULT_HISTORY_DEPTH}).
 *     <li>{@link #recordStateHistory()} — enables appending the state record of an
 *         aggregate to an {@link EntityStateHistoryStorage} on each dispatch.
 *         The aggregates read the recorded history via {@code stateAt(Timestamp)} and
 *         {@code stateHistoryBackward(int)} — e.g., for answering the "state at a time"
 *         query. It is <b>off by default</b>: recording adds a write to every dispatch,
 *         and the history grows until the application maintains it. Reading the
 *         {@linkplain #stateHistory() state history} of a repository that does not
 *         record it fails fast.
 * </ul>
 *
 * @param <I>
 *         the type of the aggregate IDs
 * @param <A>
 *         the type of the aggregates managed by this repository
 * @param <S>
 *         the type of the state of aggregates managed by this repository
 * @see Aggregate
 */
@SuppressWarnings("resource" /* Accessing `Closeable` properties. */)
public abstract class AggregateRepository<I,
                                          A extends Aggregate<I, S, ?>,
                                          S extends AggregateState<I>>
        extends SignalDispatchingRepository<I, A, S>
        implements EventProducingRepository {

    /** The default {@link #eventHistoryDepth()} value. */
    static final int DEFAULT_HISTORY_DEPTH = 100;

    /** The window (in journal events) the opt-in {@link IdempotencyGuard} scans. */
    private int eventHistoryDepth = DEFAULT_HISTORY_DEPTH;

    /** Whether the opt-in {@link IdempotencyGuard} is enabled for this repository. */
    private boolean idempotencyGuardEnabled = false;

    /**
     * Whether the opt-in state history recording is enabled for this repository.
     *
     * <p>Volatile: read by dispatch workers, while the recording may be
     * {@linkplain #recordStateHistory() enabled} and
     * {@linkplain #stopRecordingStateHistory() stopped} at runtime.
     */
    private volatile boolean stateHistoryEnabled = false;

    /** The storage of recent state records; created lazily once the history is first needed. */
    private @MonotonicNonNull EntityStateHistoryStorage<I> stateHistory;

    /**
     * The journal of the events emitted by the aggregates of this repository; created lazily
     * once the journal is first needed.
     */
    private @MonotonicNonNull EntityEventStorage<I> eventStorage;

    /** Creates a new instance. */
    protected AggregateRepository() {
        super();
    }

    @Override
    public final EventBus eventBus() {
        return context().eventBus();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Adds the endpoints reacting upon events and handling commands.
     */
    @Override
    protected final void setupInbox(Inbox.Builder<I> builder) {
        builder.addEventEndpoint(REACT_UPON_EVENT,
                                 e -> new AggregateEventReactionEndpoint<>(this, e))
               .addCommandEndpoint(HANDLE_COMMAND,
                                   c -> new AggregateCommandEndpoint<>(this, c));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Ensures that the aggregates of this repository handle commands, react on events,
     * or both.
     */
    @Override
    protected final void checkDispatchesMessages() {
        var handlesCommands = dispatchesCommands();
        var reactsOnEvents = dispatchesEvents();

        if (!handlesCommands && !reactsOnEvents) {
            throw newIllegalStateException(
                    "Aggregates of the repository `%s` neither handle commands" +
                            " nor react on events.", this);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Does nothing by default: an Aggregate may react only on events,
     * in which case no command routing is needed.
     */
    @Override
    @SuppressWarnings("NoopMethodInAbstractClass") // See Javadoc
    protected void setupCommandRouting(CommandRouting<I> routing) {
        // Do nothing.
    }

    /**
     * {@inheritDoc}
     *
     * <p>Also posts the {@linkplain io.spine.system.server.event.EntityCreated
     * entity-created} system event, and installs the history loaders and, when enabled,
     * the idempotency guard on the created aggregate.
     */
    @Override
    @OverridingMethodsMustInvokeSuper
    public A create(I id) {
        var aggregate = super.create(id);
        lifecycleOf(id).onEntityCreated(AGGREGATE);
        setUpHistoryReading(aggregate, id);
        return aggregate;
    }

    /**
     * Installs the history loaders and, when enabled, the idempotency guard
     * on a newly created aggregate.
     *
     * <p>{@linkplain #create(Object) Creation} and
     * {@linkplain #toEntity(EntityRecord) reconstruction} paths must call this method.
     *
     * @param aggregate
     *         the newly created aggregate
     * @param id
     *         the identifier of the aggregate
     */
    final void setUpHistoryReading(A aggregate, I id) {
        aggregate.setEventHistoryLoader(
                depth -> eventStorage().historyBackward(id, depth));
        aggregate.setStateHistoryLoader(stateHistoryLoaderFor(id));
        if (idempotencyGuardEnabled) {
            aggregate.enableIdempotencyGuard(eventHistoryDepth);
        }
    }

    /** Obtains class information of aggregates managed by this repository. */
    protected final AggregateClass<A> aggregateClass() {
        return (AggregateClass<A>) entityModelClass();
    }

    @Override
    protected AggregateClass<A> toModelClass(Class<A> cls) {
        return asAggregateClass(cls);
    }

    /**
     * {@inheritDoc}
     *
     * <p>When the state history is {@linkplain #recordStateHistory() recorded}, appends
     * the current state record on each call — that is, once per successful dispatch. The
     * append happens here and not in {@link #doStore}, because under a batched delivery the
     * cache defers {@code doStore()} to the end of the batch — the history still captures
     * every intermediate version of the batch.
     */
    @Override
    protected void afterStore(A aggregate) {
        if (stateHistoryEnabled) {
            appendStateHistory(aggregate);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Journals the events the aggregate emitted, then writes its latest state — rather
     * than delegating to {@link #store(io.spine.server.entity.Entity) store()}, which routes
     * through the cache.
     *
     * @implSpec Skips an aggregate that was neither changed nor produced events. An
     *         overriding repository is expected to call {@code super}: writing an untouched
     *         instance would overwrite the stored state of another instance sharing its ID.
     */
    @Override
    @Internal
    protected void doStore(A aggregate) {
        // Since the cutover the state is persisted independently of visibility, but still only
        // for an aggregate that was actually modified. Persisting an untouched instance would
        // overwrite the stored state of another instance sharing the same ID with a default
        // (empty) record. An unmodified aggregate has no state change and no uncommitted events.
        if (!aggregate.changed() && !aggregate.hasUncommittedEvents()) {
            return;
        }
        // Journal the emitted events, then store the latest state. Under a batched delivery the
        // aggregate accumulates its events until `commitEvents()`, so the journal drops none.
        var events = aggregate.uncommittedHistory().get();
        var journal = eventStorage();
        events.forEach(journal::write);
        super.doStore(aggregate);
        aggregate.commitEvents();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Stores each aggregate individually via
     * {@link #store(io.spine.server.entity.Entity) store()}, so that the emitted events
     * are journaled and an untouched instance is skipped — the bulk write of the parent
     * class would bypass both.
     */
    @Override
    public final void store(Collection<A> aggregates) {
        aggregates.forEach(this::store);
    }

    /**
     * Appends the current state record of the aggregate to the state history.
     *
     * <p>Obtains the storage directly, bypassing the fail-fast {@link #stateHistory()}
     * accessor: the decision to record is made by the single flag check in
     * {@link #store(io.spine.server.entity.Entity) store()}, so a concurrent
     * {@link #stopRecordingStateHistory()}
     * cannot fail a dispatch which has already persisted its state.
     *
     * <p>A failure to record the history fails the dispatch. Under a batched delivery,
     * the durable state write may happen later, at the batch flush, so a history record
     * may briefly precede the state it captures.
     */
    private void appendStateHistory(A aggregate) {
        var history = stateHistoryStorage();
        history.write(aggregate.toRecord());
    }

    /**
     * Creates a loader reading the recorded state history of the aggregate with
     * the given identifier.
     *
     * <p>The loader delegates to {@link #stateHistory()}: when the recording is not
     * enabled for this repository, reading through the loader fails fast the same way.
     */
    private StateHistoryLoader stateHistoryLoaderFor(I id) {
        return new StateHistoryLoader() {

            @Override
            public Iterator<EntityRecord> load(int depth) {
                return stateHistory().historyBackward(id, depth);
            }

            @Override
            public @Nullable EntityRecord stateAt(Timestamp at) {
                return stateHistory().stateAt(id, at);
            }
        };
    }

    /**
     * Obtains a set of classes of commands handled by the aggregates of this repository.
     *
     * @return a set of command classes or an empty set if the aggregates do not handle commands
     */
    @Override
    public final ImmutableSet<CommandClass> commandClasses() {
        return aggregateClass().commands();
    }

    @Internal
    @Override
    protected final void onRoutingFailed(SignalEnvelope<?, ?, ?> envelope, Throwable cause) {
        super.onRoutingFailed(envelope, cause);
        postIfCommandRejected(envelope, cause);
    }

    /**
     * Obtains a set of event classes on which the aggregates of this repository react.
     *
     * @return a set of event classes or an empty set if the aggregates do not react to events
     */
    @Override
    public ImmutableSet<EventClass> messageClasses() {
        return aggregateClass().events();
    }

    /**
     * Obtains classes of domestic events on which the aggregates of this repository react.
     *
     * @return a set of event classes or an empty set if the aggregates do not react
     *         to domestic events
     */
    @Override
    public ImmutableSet<EventClass> domesticEventClasses() {
        return aggregateClass().domesticEvents();
    }

    /**
     * Obtains classes of external events on which the aggregates of this repository react.
     *
     * @return a set of event classes or an empty set if the aggregates do not react
     *         to external events
     */
    @Override
    public ImmutableSet<EventClass> externalEventClasses() {
        return aggregateClass().externalEvents();
    }

    @Override
    public ImmutableSet<EventClass> outgoingEvents() {
        return aggregateClass().outgoingEvents();
    }

    @Override
    public boolean canDispatch(EventEnvelope envelope) {
        return aggregateClass().reactorOf(envelope).isPresent();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Sends the given event to the {@code Inbox}es of the aggregates reacting on it.
     */
    @Override
    protected final DispatchOutcome dispatchTo(Set<I> ids, EventEnvelope event) {
        ids.forEach(id -> inbox().send(event)
                                 .toReactor(id));
        return sentToInbox(event, ids);
    }

    /**
     * Returns the number of the most recent journal events scanned by the opt-in
     * {@link IdempotencyGuard} when it is enabled.
     *
     * @return a positive integer value; the default is {@value #DEFAULT_HISTORY_DEPTH}
     */
    protected int eventHistoryDepth() {
        return this.eventHistoryDepth;
    }

    /**
     * Sets the {@linkplain #eventHistoryDepth() event history depth} to the passed value.
     *
     * @param depth
     *         a positive number of recent journal events the idempotency guard scans
     */
    protected void setEventHistoryDepth(int depth) {
        checkArgument(depth > 0);
        this.eventHistoryDepth = depth;
    }

    /**
     * Enables the opt-in, journal-backed {@link IdempotencyGuard} for the aggregates of
     * this repository.
     *
     * <p>When enabled, each dispatch scans the last {@link #eventHistoryDepth()} journal events and
     * rejects a signal already seen among them, however long ago it was dispatched — a mechanism
     * distinct from the delivery layer's time-windowed deduplication. The guard is
     * <b>off by default</b> for performance: it adds a bounded journal read to every dispatch.
     */
    protected void useIdempotencyGuard() {
        this.idempotencyGuardEnabled = true;
    }

    /**
     * Tells whether the opt-in {@link IdempotencyGuard} is enabled for this repository.
     *
     * @return {@code false} by default
     */
    protected boolean idempotencyGuardEnabled() {
        return idempotencyGuardEnabled;
    }

    /**
     * Enables recording the state history for the aggregates of this repository.
     *
     * <p>When enabled, each successful dispatch appends the resulting
     * {@link EntityRecord} to an {@link EntityStateHistoryStorage} — e.g., for answering
     * {@linkplain EntityStateHistoryStorage#stateAt(Object, com.google.protobuf.Timestamp)
     * the "state at a time" query}. The history is <b>off by default</b>: recording adds
     * a write operation to every dispatch.
     *
     * <p>The aggregates of this repository read the recorded history via
     * {@link Aggregate#stateAt(Timestamp)} and {@link Aggregate#stateHistoryBackward(int)}.
     *
     * <p>Records are appended per dispatch even when the delivery batches the state
     * write-through, so the intermediate versions of a batch are retained.
     *
     * <p><b>The history is not trimmed automatically.</b> The framework does not spend
     * a maintenance query on the dispatch path, so the storage grows with every dispatch
     * until the application removes the records it no longer needs. Schedule the
     * maintenance suiting your domain — e.g., periodically invoke
     * {@link EntityStateHistoryStorage#truncate(Timestamp) truncate(olderThan)} on the
     * {@linkplain #stateHistory() state history}, or bound the history of a single
     * aggregate with {@link EntityStateHistoryStorage#trim(Object, int)
     * trim(entityId, keepMostRecent)}.
     *
     * <p>A failure to record the history fails the dispatch. Note that under a batched
     * delivery the durable write of the aggregate state itself may follow at the batch
     * flush, so a history record may briefly precede the state it captures.
     *
     * @see #stateHistory()
     * @see #stopRecordingStateHistory()
     */
    protected void recordStateHistory() {
        this.stateHistoryEnabled = true;
    }

    /**
     * Tells whether the opt-in state history recording is enabled for this repository.
     *
     * @return {@code false} by default
     * @see #recordStateHistory()
     */
    protected boolean stateHistoryEnabled() {
        return stateHistoryEnabled;
    }

    /**
     * Stops recording the state history for the aggregates of this repository.
     *
     * <p>The records already stored remain in the storage. While the recording is off,
     * reading the {@linkplain #stateHistory() state history} fails fast the usual way;
     * {@linkplain #recordStateHistory() re-enabling} the recording resumes over the
     * retained records, with a gap for the dispatches served while it was off.
     *
     * <p>The switch may be flipped at runtime: dispatch workers observe it on their
     * next dispatch. A dispatch already past its recording check may append one more
     * record after this call returns.
     *
     * <p>To also purge the retained records, truncate the history up to the present
     * <em>before</em> stopping: {@code stateHistory().truncate(currentTime())}.
     *
     * @see #recordStateHistory()
     */
    protected void stopRecordingStateHistory() {
        this.stateHistoryEnabled = false;
    }

    /**
     * Returns the storage of the recent state history of the aggregates of this repository.
     *
     * <p>The state history is an opt-in feature. Reading it while disabled is
     * a configuration error, so this method fails fast rather than acting as if
     * an empty history existed.
     *
     * @return the state history storage
     * @throws IllegalStateException
     *         if the state history is not {@linkplain #recordStateHistory() recorded}
     *         by this repository
     */
    protected final EntityStateHistoryStorage<I> stateHistory() {
        if (!stateHistoryEnabled) {
            throw newIllegalStateException(
                    "The state history is not recorded for the repository `%s`. " +
                            "Enable it by calling `recordStateHistory()`, " +
                            "e.g. from the repository constructor.", this);
        }
        return stateHistoryStorage();
    }

    /**
     * Creates the storage of the recent state history of the aggregates of this repository.
     *
     * <p>Mirrors {@link #createStorage()}: the default implementation uses the
     * {@linkplain #defaultStorageFactory() default storage factory} — the same one the default
     * {@code createStorage()} uses for the aggregate state. A repository that overrides
     * {@code createStorage()} to serve the aggregate state from a custom
     * {@link io.spine.server.storage.StorageFactory} or backend should override this method as
     * well, so the recorded state history is served by the same backend as the state, rather
     * than silently falling back to the default one.
     *
     * @return a new state history storage
     */
    protected EntityStateHistoryStorage<I> createStateHistoryStorage() {
        var factory = defaultStorageFactory();
        return factory.createEntityStateHistoryStorage(context().spec(), entityClass());
    }

    /**
     * Returns the storage of the recent state history of the aggregates of this repository,
     * creating it lazily via {@link #createStateHistoryStorage()} on the first access.
     *
     * <p>Unlike the fail-fast {@link #stateHistory()} accessor, this method does not require
     * recording to be {@linkplain #recordStateHistory() enabled}: it exposes the storage for
     * the maintenance operations — {@link EntityStateHistoryStorage#truncate(Timestamp) truncate}
     * and {@link EntityStateHistoryStorage#trim(Object, int) trim} — which a repository may run
     * even while the recording is off.
     *
     * <p>Synchronized: unlike the main {@linkplain #storage() storage}, which is first
     * accessed during the single-threaded registration of the repository, this storage
     * is first touched when a signal is dispatched, possibly by concurrent workers.
     *
     * @return the state history storage
     */
    protected final synchronized EntityStateHistoryStorage<I> stateHistoryStorage() {
        if (stateHistory == null) {
            stateHistory = createStateHistoryStorage();
        }
        return stateHistory;
    }

    /**
     * Creates the journal of the events emitted by the aggregates of this repository.
     *
     * <p>Mirrors {@link #createStorage()}: the default implementation uses the
     * {@linkplain #defaultStorageFactory() default storage factory} — the same one the default
     * {@code createStorage()} uses for the aggregate state. A repository that overrides
     * {@code createStorage()} to serve the aggregate state from a custom
     * {@link io.spine.server.storage.StorageFactory} or backend should override this method as
     * well, so the journal — feeding the {@link IdempotencyGuard} and the
     * {@linkplain Aggregate#eventHistoryBackward(int) recent-history} reads — is served by the
     * same backend as the state, rather than silently falling back to the default one.
     *
     * @return a new event journal
     */
    protected EntityEventStorage<I> createEventStorage() {
        return defaultStorageFactory().createEntityEventStorage(context().spec(), entityClass());
    }

    /**
     * Returns the journal of the events emitted by the aggregates of this repository,
     * creating it lazily via {@link #createEventStorage()} on the first access.
     *
     * <p>Unlike the opt-in {@linkplain #stateHistory() state history}, the journal is always
     * maintained, so this accessor has no fail-fast gate: it exposes the journal — e.g., for the
     * {@linkplain EntityEventStorage#truncate(com.google.protobuf.Timestamp) time-based
     * truncation} that bounds the journal growth as a periodic maintenance operation.
     *
     * <p>Synchronized for the same reason as {@link #stateHistoryStorage()}: unlike the main
     * {@linkplain #storage() storage}, the journal is first touched when a signal is dispatched,
     * possibly by concurrent workers.
     *
     * @return the event journal of this repository
     */
    protected final synchronized EntityEventStorage<I> eventStorage() {
        if (eventStorage == null) {
            eventStorage = createEventStorage();
        }
        return eventStorage;
    }

    /**
     * Loads or creates an aggregate by the passed ID.
     *
     * @param id
     *         the ID of the aggregate
     * @return loaded or created aggregate instance
     */
    final A loadOrCreate(I id) {
        return cache().load(id);
    }

    /**
     * Loads an aggregate by the passed ID.
     *
     * <p>Since the event-sourcing cutover, the aggregate is loaded from its latest persisted
     * {@link EntityRecord state record} rather than by replaying its event journal. The record
     * is read directly from the record storage, regardless of the visibility of
     * the aggregate, so even {@code NONE}-visibility aggregates load correctly.
     *
     * @param id
     *         the ID of the aggregate
     * @return the loaded instance or {@code Optional.empty()} if there is no {@code Aggregate}
     *         with the ID
     */
    private Optional<A> load(I id) {
        var found = recordStorage().read(id);
        var result = found.map(this::toEntity);
        return result;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Installs the history loaders and, when enabled, the idempotency guard on
     * the reconstructed aggregate.
     */
    @Override
    protected final A toEntity(EntityRecord record) {
        var result = super.toEntity(record);
        setUpHistoryReading(result, result.id());
        return result;
    }

    /**
     * Loads an aggregate by the passed ID.
     *
     * <p>An aggregate will be loaded even if
     * {@link io.spine.server.entity.Entity#isArchived() archived}
     * or {@link io.spine.server.entity.Entity#isDeleted() deleted} lifecycle
     * attribute, or both of them, are set to {@code true}.
     *
     * @param id
     *         the ID of the aggregate to load
     * @return the aggregate instance, or {@link Optional#empty() empty()} if there is no
     *         aggregate with such ID
     */
    @Override
    public Optional<A> find(I id) throws IllegalStateException {
        return load(id);
    }

    @OverridingMethodsMustInvokeSuper
    @Override
    public void close() {
        // Release every owned resource even if one close fails: the first failure is kept as
        // the primary exception and the later ones are attached to it as suppressed, so none of
        // them is lost. `super.close()` is called directly (not via the helper) so that the
        // `@OverridingMethodsMustInvokeSuper` check recognizes the super-call.
        RuntimeException failure = null;
        try {
            super.close();
        } catch (RuntimeException e) {
            failure = e;
        }
        failure = attemptClose(failure, this::closeEventStorage);
        failure = attemptClose(failure, this::closeStateHistory);
        if (failure != null) {
            throw failure;
        }
    }

    /**
     * Closes the event journal if it was created.
     *
     * <p>Synchronized to pair with {@link #eventStorage()}: the journal may have been
     * created by a dispatch worker, and the closing thread must observe that write.
     */
    private synchronized void closeEventStorage() {
        if (eventStorage != null && eventStorage.isOpen()) {
            eventStorage.close();
        }
    }

    /**
     * Closes the state history storage if it was created.
     *
     * <p>Synchronized to pair with {@link #stateHistoryStorage()}: the storage may have been
     * created by a dispatch worker, and the closing thread must observe that write.
     */
    private synchronized void closeStateHistory() {
        if (stateHistory != null && stateHistory.isOpen()) {
            stateHistory.close();
        }
    }
}
