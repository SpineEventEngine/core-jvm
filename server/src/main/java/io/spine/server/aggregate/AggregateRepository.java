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
import io.spine.annotation.Internal;
import io.spine.annotation.VisibleForTesting;
import io.spine.base.AggregateState;
import io.spine.client.ResponseFormat;
import io.spine.client.TargetFilters;
import io.spine.query.EntityQuery;
import io.spine.server.BoundedContext;
import io.spine.server.ServerEnvironment;
import io.spine.server.aggregate.model.AggregateClass;
import io.spine.server.commandbus.CommandDispatcher;
import io.spine.server.delivery.BatchDeliveryListener;
import io.spine.server.delivery.Inbox;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.entity.EntityRecord;
import io.spine.server.entity.EventProducingRepository;
import io.spine.server.entity.QueryableRepository;
import io.spine.server.entity.Repository;
import io.spine.server.entity.RepositoryCache;
import io.spine.server.entity.storage.EntityStateHistoryStorage;
import io.spine.server.event.EventBus;
import io.spine.server.event.EventDispatcherDelegate;
import io.spine.server.route.CommandRouting;
import io.spine.server.route.EventRouting;
import io.spine.server.route.setup.CommandRoutingSetup;
import io.spine.server.route.setup.EventRoutingSetup;
import io.spine.server.type.CommandClass;
import io.spine.server.type.CommandEnvelope;
import io.spine.server.type.EventClass;
import io.spine.server.type.EventEnvelope;
import io.spine.server.type.SignalEnvelope;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Suppliers.memoize;
import static com.google.common.collect.Iterators.transform;
import static io.spine.option.EntityOption.Kind.AGGREGATE;
import static io.spine.server.aggregate.model.AggregateClass.asAggregateClass;
import static io.spine.server.delivery.InboxLabel.HANDLE_COMMAND;
import static io.spine.server.delivery.InboxLabel.REACT_UPON_EVENT;
import static io.spine.server.dispatch.DispatchOutcomes.maybeSentToInbox;
import static io.spine.server.dispatch.DispatchOutcomes.sentToInbox;
import static io.spine.server.tenant.TenantAwareRunner.with;
import static io.spine.util.Exceptions.newIllegalStateException;
import static java.util.Objects.requireNonNull;

/**
 * The repository that manages instances of {@code Aggregate}s.
 *
 * <p>The repository routes commands and events to its aggregates, stores the events they
 * produce, and posts those events to the {@link io.spine.server.event.EventBus EventBus}.
 *
 * <p>Since the event-sourcing cutover, an aggregate is loaded from its latest persisted
 * {@link io.spine.server.entity.EntityRecord EntityRecord} rather than by replaying its event
 * journal. The journal is kept append-only for traceability and for the opt-in
 * {@link IdempotencyGuard}.
 *
 * <p>Three per-repository settings tune this behavior:
 * <ul>
 *     <li>{@link #useIdempotencyGuard()} — enables the journal-backed {@link IdempotencyGuard},
 *         which rejects a signal already seen among the last {@link #historyDepth()} dispatches
 *         (however long ago). It is <b>off by default</b> for performance — when enabled, every
 *         dispatch pays a bounded journal read. This is a mechanism distinct from the delivery
 *         layer's time-windowed deduplication, not a replacement for it.
 *     <li>{@linkplain #historyDepth() historyDepth} — how many recent journal events the guard
 *         scans on each dispatch when enabled (default {@value #DEFAULT_HISTORY_DEPTH}).
 *     <li>{@link #recordStateHistory(int)} — enables keeping the given number of the most
 *         recent state records per aggregate in an {@link EntityStateHistoryStorage} — e.g.,
 *         for answering the "state at a time" query. It is <b>off by default</b>: recording
 *         adds a write and a bounded trim to every dispatch. Reading the
 *         {@linkplain #stateHistory() state history} of a repository that does not record
 *         it fails fast.
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
@SuppressWarnings({"ClassWithTooManyMethods", "OverlyCoupledClass"})
public abstract class AggregateRepository<I,
                                          A extends Aggregate<I, S, ?>,
                                          S extends AggregateState<I>>
        extends Repository<I, A>
        implements CommandDispatcher, EventProducingRepository,
                   EventDispatcherDelegate, QueryableRepository<I, S> {

    /** The default {@link #historyDepth()} and {@link #stateHistoryDepth()} value. */
    static final int DEFAULT_HISTORY_DEPTH = 100;

    /** The routing schema for commands handled by the aggregates. */
    private final Supplier<CommandRouting<I>> commandRouting;

    /** The routing schema for events to which aggregates react. */
    private final Supplier<EventRouting<I>> eventRouting;

    /**
     * The {@link Inbox} for the messages, which are sent to the instances managed by this
     * repository.
     */
    private @MonotonicNonNull Inbox<I> inbox;

    private @MonotonicNonNull RepositoryCache<I, A> cache;

    /** The window (in journal events) the opt-in {@link IdempotencyGuard} scans. */
    private int historyDepth = DEFAULT_HISTORY_DEPTH;

    /** Whether the opt-in {@link IdempotencyGuard} is enabled for this repository. */
    private boolean idempotencyGuardEnabled = false;

    /** The number of recent state records kept per aggregate when the state history is enabled. */
    private int stateHistoryDepth = DEFAULT_HISTORY_DEPTH;

    /** Whether the opt-in state history recording is enabled for this repository. */
    private boolean stateHistoryEnabled = false;

    /** The storage of recent state records; created lazily once the history is first needed. */
    private @MonotonicNonNull EntityStateHistoryStorage stateHistory;

    /** Creates a new instance. */
    protected AggregateRepository() {
        super();
        this.commandRouting = memoize(() -> CommandRouting.newInstance(idClass()));
        this.eventRouting = memoize(
                () -> EventRouting.withDefaultByProducerIdOrFirstField(idClass())
        );
    }

    /**
     * Initializes the repository during its registration with its context.
     *
     * <p>Verifies that the class of aggregates of this repository subscribes to at least one
     * type of messages.
     *
     * <p>Registers itself with {@link io.spine.server.commandbus.CommandBus CommandBus} and
     * {@link io.spine.server.event.EventBus EventBus} of the context for dispatching messages to
     * its aggregates.
     *
     * @param context
     *         the context of this repository
     * @throws IllegalStateException
     *         if the aggregate class does not handle any messages
     */
    @Override
    @OverridingMethodsMustInvokeSuper
    public void registerWith(BoundedContext context) {
        checkNotVoid();
        super.registerWith(context);
        setupRouting();
        context.internalAccess()
               .registerCommandDispatcher(this);
        initCache(context.isMultitenant());
        initInbox();
        configureQuerying();
    }

    private void setupRouting() {
        doSetupCommandRouting();
        doSetupEventRouting();
    }

    private void doSetupCommandRouting() {
        var cmdRouting = commandRouting();
        var entityClass = entityClass();
        CommandRoutingSetup.apply(entityClass, cmdRouting);
        setupCommandRouting(cmdRouting);
    }

    private void doSetupEventRouting() {
        EventRoutingSetup.apply(entityClass(), eventRouting.get());
        setupEventRouting(eventRouting.get());
    }

    @Override
    public final EventBus eventBus() {
        return context().eventBus();
    }

    private void initCache(boolean multitenant) {
        cache = new RepositoryCache<>(multitenant, this::doLoadOrCreate, this::doStore);
    }

    /**
     * Initializes the {@code Inbox}.
     */
    private void initInbox() {
        var delivery = ServerEnvironment.instance().delivery();
        inbox = delivery.<I>newInbox(entityStateType())
                .withBatchListener(newCachingListener())
                .addEventEndpoint(REACT_UPON_EVENT,
                                  e -> new AggregateEventReactionEndpoint<>(this, e))
                .addCommandEndpoint(HANDLE_COMMAND,
                                    c -> new AggregateCommandEndpoint<>(this, c))
                .build();
    }

    private BatchDeliveryListener<I> newCachingListener() {
        return new BatchDeliveryListener<>() {
            @Override
            public void onStart(I id) {
                cache.startCaching(id);
            }

            @Override
            public void onEnd(I id) {
                cache.stopCaching(id);
            }
        };
    }

    private Inbox<I> inbox() {
        return requireNonNull(inbox);
    }

    /**
     * Ensures that this repository dispatches at least one kind of messages.
     */
    private void checkNotVoid() {
        var handlesCommands = dispatchesCommands();
        var reactsOnEvents = dispatchesEvents();

        if (!handlesCommands && !reactsOnEvents) {
            throw newIllegalStateException(
                    "Aggregates of the repository `%s` neither handle commands" +
                            " nor react on events.", this);
        }
    }

    /**
     * A callback for derived classes to customize routing schema for commands.
     *
     * <p>Default routing returns the value of the first field of a command message.
     *
     * @param routing
     *         the routing schema to customize
     */
    @SuppressWarnings("NoopMethodInAbstractClass") // See Javadoc
    protected void setupCommandRouting(CommandRouting<I> routing) {
        // Do nothing.
    }

    /**
     * A callback for derived classes to customize routing schema for events.
     *
     * <p>Default routing returns the ID of the entity that
     * {@linkplain io.spine.core.EventContext#getProducerId() produced} the event.
     * This allows to “link” different kinds of entities by having the same class of IDs.
     * More complex scenarios (e.g., one-to-many relationships) may require custom routing schemas.
     *
     * @param routing
     *         the routing schema to customize
     */
    @SuppressWarnings("NoopMethodInAbstractClass") // see Javadoc
    protected void setupEventRouting(EventRouting<I> routing) {
        // Do nothing.
    }

    @Override
    public A create(I id) {
        var aggregate = aggregateClass().create(id);
        aggregate.setRecentHistoryLoader(
                depth -> aggregateStorage().readHistoryBackward(id, depth)
                                           .iterator());
        if (idempotencyGuardEnabled) {
            aggregate.enableIdempotencyGuard(historyDepth);
        }
        return aggregate;
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
     * Stores the passed aggregate and commits its uncommitted events.
     */
    @Override
    protected final void store(A aggregate) {
        cache.store(aggregate);
    }

    @VisibleForTesting
    protected void doStore(A aggregate) {
        // Since the cutover the state is persisted independently of visibility, but still only
        // for an aggregate that was actually modified. Persisting an untouched instance would
        // overwrite the stored state of another instance sharing the same ID with a default
        // (empty) record. An unmodified aggregate has no state change and no uncommitted events.
        if (!aggregate.changed() && !aggregate.hasUncommittedEvents()) {
            return;
        }
        var history = aggregate.uncommittedHistory();
        aggregateStorage().writeAll(aggregate, history.get());
        aggregate.commitEvents();
        if (stateHistoryEnabled) {
            appendStateHistory(aggregate);
        }
    }

    /**
     * Appends the current state record of the aggregate to the state history,
     * trimming the history to the configured {@linkplain #stateHistoryDepth() depth}.
     *
     * <p>A failure to record the history fails the dispatch, even though the aggregate
     * state itself is already stored at this point.
     */
    private void appendStateHistory(A aggregate) {
        var history = stateHistory();
        history.write(aggregate.toRecord());
        history.trim(aggregate.id(), stateHistoryDepth);
    }

    /**
     * Creates aggregate storage for the repository.
     *
     * @return new storage
     */
    @Override
    protected AggregateStorage<I, S> createStorage() {
        var sf = defaultStorageFactory();
        var result = sf.createAggregateStorage(context().spec(), entityClass());
        return result;
    }

    @Override
    public final ImmutableSet<CommandClass> messageClasses() {
        return aggregateClass().commands();
    }

    /**
     * Dispatches the passed command to an aggregate.
     *
     * <p>The aggregate ID is obtained from the passed command.
     *
     * <p>The repository loads the aggregate by this ID, or creates a new aggregate
     * if there is no aggregate with such ID.
     *
     * @param cmd
     *         the command to dispatch
     */
    @Override
    public final DispatchOutcome dispatch(CommandEnvelope cmd) {
        checkNotNull(cmd);
        var target = route(cmd);
        target.ifPresent(id -> inbox().send(cmd)
                                      .toHandler(id));
        return maybeSentToInbox(cmd, target);
    }

    /**
     * {@inheritDoc}
     *
     * <p>An {@code AggregateRepository} always dispatches commands with the correct type.
     */
    @Override
    public final boolean canDispatch(CommandEnvelope envelope) {
        return true;
    }

    private Optional<I> route(CommandEnvelope cmd) {
        var target = route(commandRouting(), cmd);
        target.ifPresent(id -> onCommandTargetSet(id, cmd));
        return target;
    }

    @Internal
    @Override
    protected final void onRoutingFailed(SignalEnvelope<?, ?, ?> envelope, Throwable cause) {
        super.onRoutingFailed(envelope, cause);
        postIfCommandRejected(envelope, cause);
    }

    @Override
    public ImmutableSet<EventClass> events() {
        return aggregateClass().events();
    }

    @Override
    public ImmutableSet<EventClass> domesticEvents() {
        return aggregateClass().domesticEvents();
    }

    @Override
    public ImmutableSet<EventClass> externalEvents() {
        return aggregateClass().externalEvents();
    }

    @Override
    public ImmutableSet<EventClass> outgoingEvents() {
        return aggregateClass().outgoingEvents();
    }

    @Override
    public boolean canDispatchEvent(EventEnvelope envelope) {
        return aggregateClass().reactorOf(envelope).isPresent();
    }

    /**
     * Dispatches event to one or more aggregates reacting on the event.
     *
     * @param event
     *         the event to dispatch
     */
    @Override
    public DispatchOutcome dispatchEvent(EventEnvelope event) {
        checkNotNull(event);
        var targets = route(event);
        targets.forEach((id) -> inbox().send(event)
                                       .toReactor(id));
        return sentToInbox(event, targets);
    }

    private Set<I> route(EventEnvelope event) {
        var route = route(eventRouting(), event);
        @SuppressWarnings("unchecked")
        var result = (Set<I>) route.orElse(ImmutableSet.of());
        return result;
    }

    /**
     * Obtains command routing instance used by this repository.
     */
    private CommandRouting<I> commandRouting() {
        return commandRouting.get();
    }

    /**
     * Obtains event routing instance used by this repository.
     */
    private EventRouting<I> eventRouting() {
        return eventRouting.get();
    }

    /**
     * Returns the number of the most recent journal events scanned by the opt-in
     * {@link IdempotencyGuard} when it is enabled.
     *
     * @return a positive integer value; the default is {@value #DEFAULT_HISTORY_DEPTH}
     */
    protected int historyDepth() {
        return this.historyDepth;
    }

    /**
     * Sets the {@linkplain #historyDepth() history depth} to the passed value.
     *
     * @param depth
     *         a positive number of recent journal events the idempotency guard scans
     */
    protected void setHistoryDepth(int depth) {
        checkArgument(depth > 0);
        this.historyDepth = depth;
    }

    /**
     * Enables the opt-in, journal-backed {@link IdempotencyGuard} for the aggregates of this
     * repository.
     *
     * <p>When enabled, each dispatch scans the last {@link #historyDepth()} journal events and
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
     * {@link EntityRecord} to an {@link EntityStateHistoryStorage}, keeping up to
     * {@code depth} most recent records per aggregate — e.g., for answering
     * {@linkplain EntityStateHistoryStorage#stateAt(Object, com.google.protobuf.Timestamp)
     * the "state at a time" query}. The history is <b>off by default</b>: recording adds
     * a write and a bounded trim to every dispatch.
     *
     * <p>The count-based retention bounds how far back the history reaches:
     * an aggregate updated often forgets quickly.
     *
     * <p>A failure to record the history fails the dispatch, even though the aggregate
     * state itself is already stored at that point.
     *
     * @param depth
     *         a positive number of the most recent state records to keep per aggregate
     * @see #stateHistory()
     */
    protected void recordStateHistory(int depth) {
        checkArgument(depth > 0);
        this.stateHistoryDepth = depth;
        this.stateHistoryEnabled = true;
    }

    /**
     * Tells whether the opt-in state history recording is enabled for this repository.
     *
     * @return {@code false} by default
     * @see #recordStateHistory(int)
     */
    protected boolean stateHistoryEnabled() {
        return stateHistoryEnabled;
    }

    /**
     * Returns the number of the most recent state records kept per aggregate
     * when the state history recording is enabled.
     *
     * @return a positive integer value; the default is {@value #DEFAULT_HISTORY_DEPTH}
     * @see #recordStateHistory(int)
     */
    protected int stateHistoryDepth() {
        return stateHistoryDepth;
    }

    /**
     * Checks if the aggregate should be mirrored, and configures
     * the underlying storage accordingly.
     */
    private void configureQuerying() {
        if(exposedToQuerying()) {
            aggregateStorage().enableStateQuerying();
        }
    }

    /**
     * Returns {@code true} if the aggregates of this repository should be available for querying.
     *
     * <p>When enabled, the underlying storage persists the latest state of the corresponding
     * Aggregate instance.
     *
     * <p>This feature is enabled for all aggregates visible for querying or subscribing.
     */
    private boolean exposedToQuerying() {
        var result = aggregateClass().visibility()
                                     .isNotNone();
        return result;
    }

    /**
     * Returns the storage assigned to this aggregate.
     *
     * @return storage instance
     * @throws IllegalStateException
     *         if the storage is null
     */
    protected AggregateStorage<I, S> aggregateStorage() {
        @SuppressWarnings("unchecked") // We check the type on initialization.
        var result = (AggregateStorage<I, S>) storage();
        return result;
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
     *         if the state history is not {@linkplain #recordStateHistory(int) recorded}
     *         by this repository
     */
    protected final EntityStateHistoryStorage stateHistory() {
        if (!stateHistoryEnabled) {
            throw newIllegalStateException(
                    "The state history is not recorded for the repository `%s`. " +
                            "Enable it by calling `recordStateHistory(depth)`, " +
                            "e.g. from the repository constructor.", this);
        }
        return stateHistoryStorage();
    }

    /**
     * Lazily creates the state history storage.
     *
     * <p>Synchronized: unlike the main {@linkplain #storage() storage}, which is first
     * accessed during the single-threaded registration of the repository, this storage
     * is first touched when a signal is dispatched, possibly by concurrent workers.
     */
    private synchronized EntityStateHistoryStorage stateHistoryStorage() {
        if (stateHistory == null) {
            var factory = defaultStorageFactory();
            stateHistory = factory.createEntityStateHistoryStorage(context().spec());
        }
        return stateHistory;
    }

    /**
     * Loads or creates an aggregate by the passed ID.
     *
     * @param id
     *         the ID of the aggregate
     * @return loaded or created aggregate instance
     */
    final A loadOrCreate(I id) {
        return cache.load(id);
    }

    @VisibleForTesting
    protected A doLoadOrCreate(I id) {
        var result = load(id).orElseGet(() -> createNew(id));
        return result;
    }

    /** Creates a new entity with the passed ID. */
    private A createNew(I id) {
        var created = create(id);
        lifecycleOf(id).onEntityCreated(AGGREGATE);
        return created;
    }

    /**
     * Loads an aggregate by the passed ID.
     *
     * <p>Since the event-sourcing cutover, the aggregate is loaded from its latest persisted
     * {@link EntityRecord state record} rather than by replaying its event journal. The record
     * is read directly from the state storage, bypassing the querying/visibility gate, so even
     * {@code NONE}-visibility aggregates load correctly.
     *
     * @param id
     *         the ID of the aggregate
     * @return the loaded instance or {@code Optional.empty()} if there is no {@code Aggregate}
     *         with the ID
     */
    private Optional<A> load(I id) {
        var found = aggregateStorage().readState(id);
        var result = found.map(record -> restore(id, record));
        return result;
    }

    /**
     * Restores an {@link Aggregate} instance with the given ID from its latest persisted state
     * record.
     *
     * <p>The state, version, and lifecycle flags are set directly from the record; no events are
     * replayed, so there is no corrupted-state outcome to report.
     *
     * @param id
     *         the ID of the {@code Aggregate} to load
     * @param record
     *         the latest state record of the {@code Aggregate}
     * @return an instance of {@link Aggregate}
     */
    protected A restore(I id, EntityRecord record) {
        var result = create(id);
        AggregateTransaction<I, ?, ?> tx = AggregateTransaction.start(result);
        result.restore(record);
        tx.commitIfActive();
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

    private void onCommandTargetSet(I id, CommandEnvelope cmd) {
        var lifecycle = lifecycleOf(id);
        var commandId = cmd.id();
        with(cmd.tenantId()).run(() -> lifecycle.onTargetAssignedToCommand(commandId));
    }

    @OverridingMethodsMustInvokeSuper
    @Override
    public void close() {
        super.close();
        if (inbox != null) {
            inbox.unregister();
        }
        closeStateHistory();
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

    @Override
    @Internal
    public Iterator<EntityRecord> findRecords(TargetFilters filters, ResponseFormat format) {
        return aggregateStorage().readStates(filters, format);
    }

    @Override
    public Iterator<S> findStates(EntityQuery<I, S, ?> query) {
        var rawStates = aggregateStorage().readStates(query);
        var result = transform(rawStates, this::stateFrom);
        return result;
    }

    @Override
    @Internal
    public Iterator<EntityRecord> findRecords(ResponseFormat format) {
        return aggregateStorage().readStates(format);
    }
}
