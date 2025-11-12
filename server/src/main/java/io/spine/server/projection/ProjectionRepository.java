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

package io.spine.server.projection;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.OverridingMethodsMustInvokeSuper;
import com.google.protobuf.Timestamp;
import io.spine.annotation.Internal;
import io.spine.annotation.VisibleForTesting;
import io.spine.base.ProjectionState;
import io.spine.base.Time;
import io.spine.core.Event;
import io.spine.server.BoundedContext;
import io.spine.server.ServerEnvironment;
import io.spine.server.delivery.BatchDeliveryListener;
import io.spine.server.delivery.CatchUpAlreadyStartedException;
import io.spine.server.delivery.CatchUpId;
import io.spine.server.delivery.CatchUpProcess;
import io.spine.server.delivery.CatchUpSignal;
import io.spine.server.delivery.Delivery;
import io.spine.server.delivery.Inbox;
import io.spine.server.delivery.InboxLabel;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.entity.EventDispatchingRepository;
import io.spine.server.entity.RepositoryCache;
import io.spine.server.entity.model.StateClass;
import io.spine.server.entity.storage.EntityRecordStorage;
import io.spine.server.event.EventStore;
import io.spine.server.projection.model.ProjectionClass;
import io.spine.server.route.EventRouting;
import io.spine.server.route.StateUpdateRouting;
import io.spine.server.route.setup.StateRoutingSetup;
import io.spine.server.type.EventClass;
import io.spine.server.type.EventEnvelope;
import io.spine.time.TimestampTemporal;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.jspecify.annotations.Nullable;

import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.Sets.intersection;
import static com.google.common.collect.Sets.union;
import static io.spine.option.EntityOption.Kind.PROJECTION;
import static io.spine.server.dispatch.DispatchOutcomes.sentToInbox;
import static io.spine.server.projection.model.ProjectionClass.asProjectionClass;
import static io.spine.server.tenant.TenantAwareRunner.withCurrentTenant;
import static io.spine.util.Exceptions.newIllegalArgumentException;
import static io.spine.util.Exceptions.newIllegalStateException;

/**
 * Abstract base for repositories managing {@link Projection}s.
 *
 * <p>{@linkplain #catchUp(Timestamp, Set) Provides an API} for the entity catch-up. During this
 * process, the framework re-builds the states of all or the selected projection instances
 * by replaying the historical events from the {@code EventStore} of its Bounded Context.
 * The catch-up process is fully automated and may be scaled across instances.
 *
 * <p>To start the catch-up, one should call a corresponding method (see below).
 *
 * <pre>
 *     var repository = new TaskViewRepository();
 *
 *     var builder = BoundedContext.singleTenant("Tasks")
 *             .add(repository)
 *             .build();
 *     // ...
 *
 *     //Start the catch-up when needed:
 *     Timestamp replayHistorySince = ...
 *     repository.catchUp(replayHistorySince, ImmutableSet.of(outdatedTaskId, anotherOne));
 * </pre>
 *
 * <p>All the live events dispatched to the entities-under-catch-up are not lost.
 * They are preserved and dispatched to the projections in a proper historical order.
 *
 * <p>After the catch-up is completed, the framework automatically switches back to the propagation
 * of the live events.
 *
 * @param <I>
 *         the type of IDs of projections
 * @param <P>
 *         the type of projections
 * @param <S>
 *         the type of projection state messages
 */
public abstract class ProjectionRepository<I,
                                           P extends Projection<I, S, ?>,
                                           S extends ProjectionState<I>>
        extends EventDispatchingRepository<I, P, S> {

    private @MonotonicNonNull Inbox<I> inbox;

    private @MonotonicNonNull CatchUpProcess<I> catchUpProcess;

    private @MonotonicNonNull RepositoryCache<I, P> cache;

    /**
     * Initializes the repository.
     *
     * <p>Ensures there is at least one event subscriber method (external or domestic) declared
     * by the class of the projection. Throws an {@code IllegalStateException} otherwise.
     *
     * <p>If projections of this repository are {@linkplain io.spine.core.Subscribe subscribed} to
     * entity state updates, a routing for state updates is created and
     * {@linkplain #setupStateRouting(StateUpdateRouting) configured}.
     * If one of the states of entities cannot be routed during the created schema,
     * {@code IllegalStateException} will be thrown.
     *
     * <p>Initializes the {@link Inbox}es for the instances of this repository and creates
     * a {@link RepositoryCache} to optimize the delivery of the event batches.
     *
     * <p>Creates an instance of the {@link CatchUpProcess} enabling this repository {@linkplain
     * #catchUp(Timestamp, Set) to catch-up} its instances.
     *
     * @param context
     *         the {@code BoundedContext} of this repository
     * @throws IllegalStateException
     *         if the state routing does not cover one of the entity state types to which
     *         the entities are subscribed
     */
    @Override
    @OverridingMethodsMustInvokeSuper
    public void registerWith(BoundedContext context) throws IllegalStateException {
        super.registerWith(context);
        ensureDispatchesEvents();
        initCache(context.isMultitenant());
        var delivery = ServerEnvironment.instance()
                                        .delivery();
        initInbox(delivery);
        initCatchUp(context, delivery);
    }

    /**
     * Initializes the catch-up process for the instances of this repository and registers it
     * as an event dispatcher in the same Bounded Context as the repository itself.
     */
    private void initCatchUp(BoundedContext context, Delivery delivery) {
        var builder = delivery.newCatchUpProcess(this);
        catchUpProcess = builder.setDispatchOp(this::sendToCatchingUp)
                                .build();
        context.internalAccess()
               .registerEventDispatcher(catchUpProcess);
    }

    private void initCache(boolean multitenant) {
        cache = new RepositoryCache<>(multitenant, this::doFindOrCreate, this::doStore);
    }

    /**
     * Initializes the {@code Inbox}.
     *
     * @param delivery
     *         the delivery of the current server environment
     */
    private void initInbox(Delivery delivery) {
        inbox = delivery
                .<I>newInbox(entityStateType())
                .withBatchListener(new BatchDeliveryListener<>() {
                    @Override
                    public void onStart(I id) {
                        cache.startCaching(id);
                    }

                    @Override
                    public void onEnd(I id) {
                        cache.stopCaching(id);
                    }
                })
                .addEventEndpoint(InboxLabel.UPDATE_SUBSCRIBER,
                                  e -> ProjectionEndpoint.of(this, e))
                .addEventEndpoint(InboxLabel.CATCH_UP,
                                  e -> CatchUpEndpoint.of(this, e))
                .build();
    }

    private Inbox<I> inbox() {
        return checkNotNull(inbox);
    }

    @Override
    @OverridingMethodsMustInvokeSuper
    protected void setupEventRouting(EventRouting<I> routing) {
        super.setupEventRouting(routing);
        if (projectionClass().subscribesToStates()) {
            var stateRouting = createStateRouting();
            routing.routeStateUpdates(stateRouting);
        }
    }

    private void ensureDispatchesEvents() {
        var noEventSubscriptions = !dispatchesEvents();
        if (noEventSubscriptions) {
            throw newIllegalStateException(
                    "Projections of the repository `%s` have neither domestic nor external" +
                            " event subscriptions.", this);
        }
    }

    /**
     * Creates and configures the {@code StateUpdateRouting} used by this repository.
     *
     * <p>This method verifies that the created state routing serves all the state classes to
     * which projections of this repository are subscribed.
     *
     * @throws IllegalStateException
     *         if one of the subscribed state classes cannot be served by the created state routing
     */
    private StateUpdateRouting<I> createStateRouting() {
        var routing = StateUpdateRouting.newInstance(idClass());
        StateRoutingSetup.apply(entityClass(), routing);
        setupStateRouting(routing);
        validate(routing);
        return routing;
    }

    private void validate(StateUpdateRouting<I> routing) throws IllegalStateException {
        var cls = projectionClass();
        Set<StateClass<?>> stateClasses = union(cls.domesticStates(), cls.externalStates());
        var unsupported =
                stateClasses.stream()
                            .filter(c -> !routing.supports(c.typedValue()))
                            .collect(toImmutableList());
        if (!unsupported.isEmpty()) {
            reportUnsupported(cls, unsupported);
        }
    }

    private void reportUnsupported(ProjectionClass<P> cls,
                                   ImmutableList<StateClass<?>> unsupported) {
        var moreThanOne = unsupported.size() > 1;
        var fmt =
                "The repository `%s` does not provide routing for updates of the state " +
                        (moreThanOne ? "classes" : "class") +
                        " — `%s` — to which the class `%s` is subscribed.";
        var typesAsString = Joiner.on(", ").join(unsupported);
        throw newIllegalStateException(fmt, this, typesAsString, cls);
    }

    /**
     * A callback for derived repository classes to customize routing schema for delivering
     * updated state to subscribed entities, if the default schema does not satisfy
     * the routing needs.
     *
     * @param routing
     *         the routing to customize
     */
    @SuppressWarnings("NoopMethodInAbstractClass") // see Javadoc
    protected void setupStateRouting(StateUpdateRouting<I> routing) {
        // Do nothing by default.
    }

    @VisibleForTesting
    static Timestamp nullToDefault(@Nullable Timestamp timestamp) {
        return timestamp == null
               ? Timestamp.getDefaultInstance()
               : timestamp;
    }

    /** Obtains {@link EventStore} from which to get events during catch-up. */
    final EventStore eventStore() {
        return context()
                .eventBus()
                .eventStore();
    }

    /** Obtains class information of projection managed by this repository. */
    private ProjectionClass<P> projectionClass() {
        return (ProjectionClass<P>) entityModelClass();
    }

    @Internal
    @Override
    protected final ProjectionClass<P> toModelClass(Class<P> cls) {
        return asProjectionClass(cls);
    }

    @OverridingMethodsMustInvokeSuper
    @Override
    public P create(I id) {
        var projection = super.create(id);
        lifecycleOf(id).onEntityCreated(PROJECTION);
        return projection;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Overrides the parent method to expose it to this package.
     */
    @Override
    protected final EntityRecordStorage<I, S> recordStorage() {
        return super.recordStorage();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Overrides to perform finding using the cache.
     */
    @Override
    protected final P findOrCreate(I id) {
        return cache.load(id);
    }

    private P doFindOrCreate(I id) {
        return super.findOrCreate(id);
    }

    @Override
    public final void store(P entity) {
        cache.store(entity);
    }

    private void doStore(P entity) {
        super.store(entity);
    }

    @Override
    public final ImmutableSet<EventClass> messageClasses() {
        return projectionClass().events();
    }

    @Override
    public final ImmutableSet<EventClass> domesticEventClasses() {
        return projectionClass().domesticEvents();
    }

    @Override
    public final ImmutableSet<EventClass> externalEventClasses() {
        return projectionClass().externalEvents();
    }

    @OverridingMethodsMustInvokeSuper
    @Override
    public final boolean canDispatch(EventEnvelope event) {
        var subscriber = projectionClass().subscriberOf(event);
        return subscriber.isPresent();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Sends the given event to the {@code Inbox}es of respective entities.
     */
    @Override
    protected final DispatchOutcome dispatchTo(Set<I> ids, EventEnvelope event) {
        ids.forEach(id -> inbox().send(event)
                                 .toSubscriber(id));
        return sentToInbox(event, ids);
    }

    /**
     * Repeats the dispatching of the events from the event log to the requested entities
     * since the specified time.
     *
     * <p>At the beginning of the process the state of each of the entities is set to the default.
     *
     * <p>During this process, the entities receive continuous updates to their state. After the
     * catch-up is completed, the framework automatically resumes the dispatching of ongoing live
     * events. When the catch-up is completed, a
     * {@link io.spine.server.delivery.event.CatchUpCompleted CatchUpCompleted} event is emitted.
     * One may use the identifier of the catch-up process and subscribe to the events of this type
     * to understand whether the operation is done.
     *
     * <p>The subscriptions to the entity state updates (i.e.
     * {@link io.spine.system.server.event.EntityStateChanged EntityStateChanged}) events are not
     * supported in the catch-up.
     *
     * @param since
     *         point in the past, since which the catch-up should be performed
     * @param ids
     *         identifiers of the entities to catch up, {@code null} means that all entities should
     *         be caught up
     * @return identifier of the catch-up operation
     * @throws CatchUpAlreadyStartedException
     *         if another catch-up for the same entity type and overlapping targets is already in
     *         progress
     * @see #catchUpAll(Timestamp) on a shortcut method which starts the catch-up for all
     *         entities in this repository
     */
    public CatchUpId catchUp(Timestamp since, @Nullable Set<I> ids)
            throws CatchUpAlreadyStartedException {
        checkCatchUpTargets(ids);
        checkCatchUpStartTime(since);

        var catchUpId = withCurrentTenant(context().isMultitenant())
                .evaluate(() -> catchUpProcess.startCatchUp(since, ids));
        return catchUpId;
    }

    private static void checkCatchUpStartTime(Timestamp since) {
        var whenStarts = TimestampTemporal.from(since);
        if (!whenStarts.isInPast()) {
            throw newIllegalArgumentException(
                    "The catch-up must be started from the moment in the past, " +
                            "but asked to start at `%s`, while now is `%s`.",
                    since, Time.currentTime());
        }
    }

    private void checkCatchUpTargets(@Nullable Set<I> ids) {
        if (ids != null) {
            checkArgument(!ids.isEmpty(),
                          "At least one ID is required to catch up the projection of type `%s`. " +
                                  "You may also pass `null` to catch up all of the instances.",
                          entityStateType());
        }
    }

    /**
     * Starts the catch-up of all entities in this repository.
     *
     * <p>This is a shortcut method for {@link #catchUp(Timestamp, Set) catchUp(since, null)}.
     *
     * @param since
     *         point in the past, since which the catch-up should be performed
     * @return identifier of the catch-up operation
     * @throws CatchUpAlreadyStartedException
     *         if another catch-up for the same entity type is already in progress
     * @see #catchUp(Timestamp, Set)
     */
    public CatchUpId catchUpAll(Timestamp since) throws CatchUpAlreadyStartedException {
        return catchUp(since, null);
    }

    /**
     * Sends the event to the inboxes of the catching-up projection instances.
     *
     * <p>Allows to restrict the target entities by identifiers. In this case, the event is
     * routed as per the repository routing schema, and the obtained set of the identifiers
     * is narrowed down to the restricted targets.
     *
     * <p>Such a setting allows catching up only the selected targets.
     *
     * <p>This API method also supports sending the special {@link CatchUpSignal}s
     * to the projection. They regulate the lifecycle of the catch-up and are handled by
     * the {@link CatchUpEndpoint} exposed by this repository.
     *
     * <p>Please note that the {@code CatchUpSignal}s are dispatched to the selected targets
     * only and cannot be dispatched to all the repository instances. The reason is that
     * handling of {@code CatchUpSignal}s may affect the lifecycle state of the projection
     * instances. E.g. the callee must know to what targets he is sending the "delete state" signal.
     *
     * @param event
     *         the event to dispatch
     * @param restrictToIds
     *         optional set of the target identifiers to which the dispatching must be restricted;
     *         if {@code null}, no restriction are applied and the event should be dispatched as per
     *         the routing schema
     * @return the set of the entity identifiers, which actually received the dispatched event
     * @see CatchUpEndpoint
     */
    private Set<I> sendToCatchingUp(Event event, @Nullable Set<I> restrictToIds) {
        var envelope = EventEnvelope.of(event);
        Set<I> catchUpTargets;
        if (envelope.message() instanceof CatchUpSignal) {
            catchUpTargets = restrictToIds == null
                             ? ImmutableSet.copyOf(index())
                             : restrictToIds;
        } else {
            var routedTargets = route(envelope);
            catchUpTargets = restrictToIds == null
                             ? routedTargets
                             : intersection(routedTargets, restrictToIds).immutableCopy();
        }
        var inbox = inbox();
        for (var target : catchUpTargets) {
            inbox.send(envelope)
                 .toCatchUp(target);
        }
        return catchUpTargets;
    }

    @OverridingMethodsMustInvokeSuper
    @Override
    public void close() {
        super.close();
        if (inbox != null) {
            inbox.unregister();
        }
    }
}
