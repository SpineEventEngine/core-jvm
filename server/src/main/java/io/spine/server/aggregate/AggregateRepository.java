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

import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.OverridingMethodsMustInvokeSuper;
import io.spine.annotation.Internal;
import io.spine.annotation.VisibleForTesting;
import io.spine.base.AggregateState;
import io.spine.base.EventMessage;
import io.spine.client.ResponseFormat;
import io.spine.client.TargetFilters;
import io.spine.core.Event;
import io.spine.core.EventContext;
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
import io.spine.server.event.EventBus;
import io.spine.server.event.EventDispatcherDelegate;
import io.spine.server.route.CommandRouting;
import io.spine.server.route.EventRouting;
import io.spine.server.route.RouteFn;
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
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Suppliers.memoize;
import static com.google.common.collect.Iterators.transform;
import static io.spine.option.EntityOption.Kind.AGGREGATE;
import static io.spine.server.aggregate.model.AggregateClass.asAggregateClass;
import static io.spine.server.delivery.InboxLabel.HANDLE_COMMAND;
import static io.spine.server.delivery.InboxLabel.IMPORT_EVENT;
import static io.spine.server.delivery.InboxLabel.REACT_UPON_EVENT;
import static io.spine.server.dispatch.DispatchOutcomes.maybeSentToInbox;
import static io.spine.server.dispatch.DispatchOutcomes.sentToInbox;
import static io.spine.server.tenant.TenantAwareRunner.with;
import static io.spine.type.Json.toJson;
import static io.spine.util.Exceptions.newIllegalStateException;
import static java.util.Objects.requireNonNull;

/**
 * The repository which manages instances of {@code Aggregate}s.
 *
 * @param <I>
 *         the type of the aggregate IDs
 * @param <A>
 *         the type of the aggregates managed by this repository
 * @param <S>
 *         the type of the state of aggregates managed by this repository
 * @see Aggregate
 */
@SuppressWarnings("ClassWithTooManyMethods")
public abstract class AggregateRepository<I,
                                          A extends Aggregate<I, S, ?>,
                                          S extends AggregateState<I>>
        extends Repository<I, A>
        implements CommandDispatcher, EventProducingRepository,
                   EventDispatcherDelegate, QueryableRepository<I, S> {

    /** The default number of events to be stored before a new snapshot is made. */
    static final int DEFAULT_SNAPSHOT_TRIGGER = 100;

    /** The routing schema for commands handled by the aggregates. */
    private final Supplier<CommandRouting<I>> commandRouting;

    /** The routing schema for events to which aggregates react. */
    private final Supplier<EventRouting<I>> eventRouting;

    /**
     * The routing for event import, which by default obtains the target aggregate ID as the
     * {@linkplain io.spine.core.EventContext#getProducerId() producer ID} of the event.
     */
    private final Supplier<EventRouting<I>> eventImportRouting;

    /**
     * The {@link Inbox} for the messages, which are sent to the instances managed by this
     * repository.
     */
    private @MonotonicNonNull Inbox<I> inbox;

    private @MonotonicNonNull RepositoryCache<I, A> cache;

    /** The number of events to store between snapshots. */
    private int snapshotTrigger = DEFAULT_SNAPSHOT_TRIGGER;

    /** Creates a new instance. */
    protected AggregateRepository() {
        super();
        this.commandRouting = memoize(() -> CommandRouting.newInstance(idClass()));
        this.eventRouting = memoize(
                () -> EventRouting.withDefaultByProducerIdOrFirstField(idClass())
        );
        this.eventImportRouting = memoize(
                () -> EventRouting.withDefaultByProducerIdOrFirstField(idClass())
        );
    }

    /**
     * Initializes the repository during its registration with its context.
     *
     * <p>Verifies that the class of aggregates of this repository subscribes to at least one
     * type of messages.
     *
     * <p>Registers itself with {@link io.spine.server.commandbus.CommandBus CommandBus},
     * {@link io.spine.server.event.EventBus EventBus}, and
     * {@link io.spine.server.aggregate.ImportBus ImportBus} of the context for dispatching
     * messages to its aggregates.
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
        if (aggregateClass().importsEvents()) {
            context.importBus()
                   .register(EventImportDispatcher.of(this));
        }
        initCache(context.isMultitenant());
        initInbox();
        configureQuerying();
    }

    private void setupRouting() {
        doSetupCommandRouting();
        doSetupEventRouting();
        setupImportRouting(eventImportRouting.get());
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
                .addEventEndpoint(IMPORT_EVENT,
                                  e -> new EventImportEndpoint<>(this, e))
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
     * <p>Default routing returns the ID of the entity which
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

    /**
     * A callback for derived classes to customize routing schema for importable events.
     *
     * <p>The default routing uses {@linkplain io.spine.core.EventContext#getProducerId()
     * producer ID} of the event as the ID of the target aggregate.
     *
     * <p>This default routing requires that {@link Event} instances
     * {@linkplain ImportBus#post(io.spine.core.Signal, io.grpc.stub.StreamObserver) posted}
     * for import must {@link io.spine.core.EventContext#getProducerId() contain} the ID of the
     * target aggregate. Not providing a valid aggregate ID would result in
     * {@code RuntimeException}.
     *
     * <p>Some aggregates may produce events with the aggregate ID as the first field of an event
     * message. To set the default routing for repositories of such aggregates, please use the
     * code below:
     *
     * <pre>{@code
     * routing.replaceDefault(EventRoute.byFirstMessageField(SomeId.class));
     * }</pre>
     *
     * @param routing
     *         the routing schema to customize.
     */
    @SuppressWarnings("NoopMethodInAbstractClass") // see Javadoc
    protected void setupImportRouting(EventRouting<I> routing) {
        // Do nothing.
    }

    @Override
    public A create(I id) {
        var aggregate = aggregateClass().create(id);
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
        var history = aggregate.uncommittedHistory();
        aggregateStorage().writeAll(aggregate, history.get());
        aggregate.commitEvents();
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

    /**
     * Obtains classes of events that can be imported by aggregates of this repository.
     */
    public ImmutableSet<EventClass> importableEvents() {
        return aggregateClass().importableEvents();
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
     * Imports the passed event into one of the aggregates.
     */
    final DispatchOutcome importEvent(EventEnvelope event) {
        checkNotNull(event);
        var target = routeImport(event);
        target.ifPresent(id -> inbox().send(event)
                                      .toImporter(id));
        return maybeSentToInbox(event, target);
    }

    private Optional<I> routeImport(EventEnvelope event) {
        var id = route(eventImportRouting(), event);
        return id;
    }

    private RouteFn<? extends EventMessage, EventContext, I> eventImportRouting() {
        return this::idForImported;
    }

    /**
     * Obtains an aggregate ID from the given event message and context applying
     * {@link #eventImportRouting} to them.
     *
     * <p>Assumes that routing should give only one ID.
     *
     * @throws IllegalStateException
     *          if {@link #eventImportRouting} returns more than one ID
     */
    private I idForImported(EventMessage message, EventContext context) {
        var ids = eventImportRouting.get().invoke(message, context);
        var numberOfTargets = ids.size();
        var messageType = message.getClass()
                                 .getName();
        checkState(
                numberOfTargets > 0,
                "Could not get aggregate ID from the event context: `%s`. Event class: `%s`.",
                context,
                messageType
        );
        checkState(
                numberOfTargets == 1,
                "Expected one aggregate ID, but got %s (%s). Event class: `%s`, context: `%s`.",
                String.valueOf(numberOfTargets),
                ids,
                messageType,
                context
        );
        var id = ids.stream()
                .findFirst()
                .orElseThrow(() -> newIllegalStateException(
                        "Unable to route import event `%s`.", messageType)
                );
        return id;
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
     * Returns the number of events until a next {@code Snapshot} is made.
     *
     * @return a positive integer value
     * @see #DEFAULT_SNAPSHOT_TRIGGER
     */
    protected int snapshotTrigger() {
        return this.snapshotTrigger;
    }

    /**
     * Changes the number of events between making aggregate snapshots to the passed value.
     *
     * <p>The default value is defined in {@link #DEFAULT_SNAPSHOT_TRIGGER}.
     *
     * <p><b>NOTE</b>: repository read operations are optimized around the current snapshot
     * trigger. Setting the snapshot trigger to a new value may cause read operations to perform
     * suboptimally, until a new snapshot is created. This doesn't apply to newly created
     * repositories.
     *
     * @param snapshotTrigger
     *         a positive number of the snapshot trigger
     */
    protected void setSnapshotTrigger(int snapshotTrigger) {
        checkArgument(snapshotTrigger > 0);
        this.snapshotTrigger = snapshotTrigger;
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
     * <p>This method defines the basic flow of an {@code Aggregate} loading. First,
     * the {@linkplain AggregateHistory Aggregate history} is
     * {@linkplain #loadHistory fetched} from the storage. Then the {@code Aggregate} is
     * {@linkplain #restore restored} from its state history.
     *
     * @param id
     *         the ID of the aggregate
     * @return the loaded instance or {@code Optional.empty()} if there is no {@code Aggregate}
     *         with the ID
     */
    private Optional<A> load(I id) {
        var found = loadHistory(id);
        var result = found.map(history -> restore(id, history));
        return result;
    }

    /**
     * Loads the history of the {@code Aggregate} with the given ID.
     *
     * <p>The method loads only the recent history of the aggregate.
     *
     * <p>The current {@link #snapshotTrigger} is used as a batch size of the read operation,
     * so the method can perform suboptimally for some time
     * after the {@link #snapshotTrigger} change.
     *
     * @param id
     *         the ID of the {@code Aggregate} to fetch
     * @return the {@link AggregateHistory} for the {@code Aggregate} or
     *         {@code Optional.empty()} if there is no record with the ID
     */
    private Optional<AggregateHistory> loadHistory(I id) {
        var storage = aggregateStorage();
        var batchSize = snapshotTrigger + 1;
        var result = storage.read(id, batchSize);
        return result;
    }

    /**
     * Plays the given {@linkplain AggregateHistory Aggregate history} for an instance
     * of {@link Aggregate} with the given ID.
     *
     * @param id
     *         the ID of the {@code Aggregate} to load
     * @param history
     *         the state record of the {@code Aggregate} to load
     * @return an instance of {@link Aggregate}
     */
    protected A restore(I id, AggregateHistory history) {
        var result = create(id);
        AggregateTransaction<I, ?, ?> tx = AggregateTransaction.start(result);
        var outcome = result.replay(history);
        var success = outcome.getSuccessful();
        tx.commitIfActive();
        if (!success) {
            lifecycleOf(id).onCorruptedState(outcome);
            var outcomeDetails = toJson(outcome);
            var aggClass = aggregateClass().rawClass()
                                           .getName();
            throw newIllegalStateException("Aggregate `%s` (ID: %s) cannot be loaded.%n" +
                                                   "Erroneous dispatch outcome: `%s`.",
                                           aggClass,
                                           result.idAsString(),
                                           outcomeDetails);
        }
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
