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

package io.spine.server.projection

import com.google.common.collect.ImmutableSet
import com.google.common.collect.Sets.intersection
import com.google.common.collect.Sets.union
import com.google.errorprone.annotations.OverridingMethodsMustInvokeSuper
import com.google.protobuf.Timestamp
import io.spine.annotation.Internal
import io.spine.annotation.VisibleForTesting
import io.spine.base.ProjectionState
import io.spine.base.Time
import io.spine.core.Event
import io.spine.option.EntityOption.Kind.PROJECTION
import io.spine.server.BoundedContext
import io.spine.server.ServerEnvironment
import io.spine.server.delivery.CatchUpAlreadyStartedException
import io.spine.server.delivery.CatchUpId
import io.spine.server.delivery.CatchUpProcess
import io.spine.server.delivery.CatchUpSignal
import io.spine.server.delivery.Inbox
import io.spine.server.delivery.InboxLabel
import io.spine.server.dispatch.DispatchOutcome
import io.spine.server.dispatch.DispatchOutcomes.sentToInbox
import io.spine.server.entity.EventDispatchingRepository
import io.spine.server.entity.model.StateClass
import io.spine.server.entity.storage.EntityRecordStorage
import io.spine.server.event.EventStore
import io.spine.server.projection.model.ProjectionClass
import io.spine.server.projection.model.ProjectionClass.asProjectionClass
import io.spine.server.route.EventRouting
import io.spine.server.route.StateUpdateRouting
import io.spine.server.route.setup.StateRoutingSetup
import io.spine.server.tenant.TenantAwareRunner.withCurrentTenant
import io.spine.server.type.EventClass
import io.spine.server.type.EventEnvelope
import io.spine.time.TimestampTemporal
import io.spine.util.Exceptions.newIllegalArgumentException
import io.spine.util.Exceptions.newIllegalStateException

/**
 * Abstract base for repositories managing [Projection]s.
 *
 * [Provides an API][catchUp] for the entity catch-up. During this process, the framework
 * re-builds the states of all or the selected projection instances by replaying the historical
 * events from the `EventStore` of its Bounded Context. The catch-up process is fully automated
 * and may be scaled across instances.
 *
 * To start the catch-up, one should call a corresponding method (see below).
 *
 * ```
 * val repository = TaskViewRepository()
 *
 * val context = BoundedContext.singleTenant("Tasks")
 *     .add(repository)
 *     .build()
 * // ...
 *
 * // Start the catch-up when needed:
 * val replayHistorySince: Timestamp = ...
 * repository.catchUp(replayHistorySince, setOf(outdatedTaskId, anotherOne))
 * ```
 *
 * All the live events dispatched to the entities-under-catch-up are not lost. They are preserved
 * and dispatched to the projections in a proper historical order.
 *
 * After the catch-up is completed, the framework automatically switches back to the propagation
 * of the live events.
 *
 * @param I The type of IDs of projections.
 * @param P The type of projections.
 * @param S The type of projection state messages.
 */
@Suppress("TooManyFunctions") // A framework repository base covering the projection lifecycle.
public abstract class ProjectionRepository<I : Any,
                                           P : Projection<I, S, *>,
                                           S : ProjectionState<I>> :
    EventDispatchingRepository<I, P, S>() {

    private lateinit var catchUpProcess: CatchUpProcess<I>

    /**
     * If projections of this repository are [subscribed][io.spine.core.Subscribe] to
     * entity state updates, a routing for state updates is created and
     * [configured][setupStateRouting]. If one of the states of entities cannot be routed
     * using the created schema, `IllegalStateException` will be thrown.
     *
     * Creates an instance of the [CatchUpProcess] enabling this repository
     * [to catch-up][catchUp] its instances.
     *
     * @param context The `BoundedContext` of this repository.
     * @throws IllegalStateException If the state routing does not cover one of the entity state
     *   types to which the entities are subscribed.
     */
    @OverridingMethodsMustInvokeSuper
    override fun registerWith(context: BoundedContext) {
        super.registerWith(context)
        initCatchUp(context)
    }

    /**
     * Initializes the catch-up process for the instances of this repository and registers it
     * as an event dispatcher in the same Bounded Context as the repository itself.
     */
    private fun initCatchUp(context: BoundedContext) {
        val delivery = ServerEnvironment.instance().delivery()
        val builder = delivery.newCatchUpProcess(this)
        catchUpProcess = builder.setDispatchOp(::sendToCatchingUp).build()
        context.internalAccess().registerEventDispatcher(catchUpProcess)
    }

    /**
     * Adds the endpoints updating the subscribing projections and catching them up.
     */
    protected final override fun setupInbox(builder: Inbox.Builder<I>) {
        builder.addEventEndpoint(InboxLabel.UPDATE_SUBSCRIBER) { e ->
            ProjectionEndpoint.of<I, P, S>(this, e)
        }.addEventEndpoint(InboxLabel.CATCH_UP) { e ->
            CatchUpEndpoint.of(this, e)
        }
    }

    @OverridingMethodsMustInvokeSuper
    protected override fun setupEventRouting(routing: EventRouting<I>) {
        super.setupEventRouting(routing)
        if (projectionClass().subscribesToStates()) {
            val stateRouting = createStateRouting()
            routing.routeStateUpdates(stateRouting)
        }
    }

    /**
     * Ensures there is at least one event subscriber method — domestic or external —
     * declared by the class of the projection.
     */
    protected final override fun checkDispatchesMessages() {
        val noEventSubscriptions = !dispatchesEvents()
        if (noEventSubscriptions) {
            throw newIllegalStateException(
                "Projections of the repository `%s` have neither domestic nor external" +
                        " event subscriptions.", this
            )
        }
    }

    /**
     * Creates and configures the `StateUpdateRouting` used by this repository.
     *
     * This method verifies that the created state routing serves all the state classes to
     * which projections of this repository are subscribed.
     *
     * @throws IllegalStateException If one of the subscribed state classes cannot be served by
     *   the created state routing.
     */
    private fun createStateRouting(): StateUpdateRouting<I> {
        val routing = StateUpdateRouting.newInstance(idClass())
        StateRoutingSetup.apply(entityClass(), routing)
        setupStateRouting(routing)
        validate(routing)
        return routing
    }

    private fun validate(routing: StateUpdateRouting<I>) {
        val cls = projectionClass()
        val stateClasses: Set<StateClass<*>> = union(cls.domesticStates(), cls.externalStates())
        // `typedValue`'s type parameter appears only in its return type, so it is supplied
        // explicitly here; the routing check accepts the state class regardless of its ID type.
        val unsupported = stateClasses.filter { !routing.supports(it.typedValue<Any>()) }
        if (unsupported.isNotEmpty()) {
            reportUnsupported(cls, unsupported)
        }
    }

    private fun reportUnsupported(cls: ProjectionClass<P>, unsupported: List<StateClass<*>>) {
        val moreThanOne = unsupported.size > 1
        val fmt =
            "The repository `%s` does not provide routing for updates of the state " +
                    (if (moreThanOne) "classes" else "class") +
                    " — `%s` — to which the class `%s` is subscribed."
        val typesAsString = unsupported.joinToString(", ")
        throw newIllegalStateException(fmt, this, typesAsString, cls)
    }

    /**
     * A callback for derived repository classes to customize routing schema for delivering
     * updated state to subscribed entities, if the default schema does not satisfy
     * the routing needs.
     *
     * @param routing The routing to customize.
     */
    @Suppress("NoopMethodInAbstractClass") // See the KDoc.
    protected open fun setupStateRouting(routing: StateUpdateRouting<I>) {
        // Do nothing by default.
    }

    /** Obtains the [EventStore] from which to get events during catch-up. */
    @JvmName("eventStore")
    internal fun eventStore(): EventStore =
        context().eventBus().eventStore()

    /** Obtains class information of the projection managed by this repository. */
    // The model class of a projection repository is always a `ProjectionClass`.
    @Suppress("UNCHECKED_CAST")
    private fun projectionClass(): ProjectionClass<P> = entityModelClass() as ProjectionClass<P>

    @Internal
    protected final override fun toModelClass(cls: Class<P>): ProjectionClass<P> =
        asProjectionClass(cls)

    @OverridingMethodsMustInvokeSuper
    override fun create(id: I): P {
        val projection = super.create(id)
        lifecycleOf(id).onEntityCreated(PROJECTION)
        return projection
    }

    /**
     * Overrides the parent method to expose it to this package.
     */
    protected final override fun recordStorage(): EntityRecordStorage<I, S> = super.recordStorage()

    /**
     * Overrides to expose the method to the package.
     */
    protected final override fun findOrCreate(id: I): P = super.findOrCreate(id)

    /**
     * Finds or creates the projection with the given ID.
     *
     * An `internal` seam exposing the `protected` [findOrCreate] to the Kotlin
     * [ProjectionEndpoint], which is not a subclass of this repository.
     */
    internal fun findOrCreateProjection(id: I): P = findOrCreate(id)

    public final override fun messageClasses(): ImmutableSet<EventClass> =
        projectionClass().events()

    public final override fun domesticEventClasses(): ImmutableSet<EventClass> =
        projectionClass().domesticEvents()

    public final override fun externalEventClasses(): ImmutableSet<EventClass> =
        projectionClass().externalEvents()

    @OverridingMethodsMustInvokeSuper
    public final override fun canDispatch(event: EventEnvelope): Boolean {
        val subscriber = projectionClass().subscriberOf(event)
        return subscriber.isPresent
    }

    /**
     * Sends the given event to the `Inbox`es of respective entities.
     */
    protected final override fun dispatchTo(
        ids: @JvmSuppressWildcards Set<I>,
        event: EventEnvelope
    ): DispatchOutcome {
        ids.forEach { id -> inbox().send(event).toSubscriber(id) }
        return sentToInbox(event, ids)
    }

    /**
     * Repeats the dispatching of the events from the event log to the requested entities
     * since the specified time.
     *
     * At the beginning of the process the state of each of the entities is set to the default.
     *
     * During this process, the entities receive continuous updates to their state. After the
     * catch-up is completed, the framework automatically resumes the dispatching of ongoing live
     * events. When the catch-up is completed, a
     * [CatchUpCompleted][io.spine.server.delivery.event.CatchUpCompleted] event is emitted.
     * One may use the identifier of the catch-up process and subscribe to the events of this type
     * to understand whether the operation is done.
     *
     * The subscriptions to the entity state updates (i.e.
     * [EntityStateChanged][io.spine.system.server.event.EntityStateChanged]) events are not
     * supported in the catch-up.
     *
     * @param since Point in the past, since which the catch-up should be performed.
     * @param ids Identifiers of the entities to catch up; `null` means that all entities should
     *   be caught up.
     * @return Identifier of the catch-up operation.
     * @throws CatchUpAlreadyStartedException If another catch-up for the same entity type and
     *   overlapping targets is already in progress.
     * @see catchUpAll A shortcut method that starts the catch-up for all entities in this
     *   repository.
     */
    @Throws(CatchUpAlreadyStartedException::class)
    public fun catchUp(since: Timestamp, ids: Set<I>?): CatchUpId {
        checkCatchUpTargets(ids)
        checkCatchUpStartTime(since)
        return withCurrentTenant(context().isMultitenant)
            .evaluate { catchUpProcess.startCatchUp(since, ids) }
    }

    private fun checkCatchUpTargets(ids: Set<I>?) {
        if (ids != null) {
            require(ids.isNotEmpty()) {
                "At least one ID is required to catch up the projection of type " +
                        "`${entityStateType()}`. " +
                        "You may also pass `null` to catch up all of the instances."
            }
        }
    }

    /**
     * Starts the catch-up of all entities in this repository.
     *
     * This is a shortcut method for [catchUp(since, null)][catchUp].
     *
     * @param since Point in the past, since which the catch-up should be performed.
     * @return Identifier of the catch-up operation.
     * @throws CatchUpAlreadyStartedException If another catch-up for the same entity type is
     *   already in progress.
     * @see catchUp
     */
    @Throws(CatchUpAlreadyStartedException::class)
    public fun catchUpAll(since: Timestamp): CatchUpId = catchUp(since, null)

    /**
     * Sends the event to the inboxes of the catching-up projection instances.
     *
     * Allows restricting the target entities by identifiers. In this case, the event is
     * routed as per the repository routing schema, and the obtained set of the identifiers
     * is narrowed down to the restricted targets.
     *
     * Such a setting allows catching up only the selected targets.
     *
     * This API method also supports sending the special [CatchUpSignal]s to the projection.
     * They regulate the lifecycle of the catch-up and are handled by the [CatchUpEndpoint]
     * exposed by this repository.
     *
     * Please note that the [CatchUpSignal]s are dispatched to the selected targets only and
     * cannot be dispatched to all the repository instances. The reason is that handling of
     * `CatchUpSignal`s may affect the lifecycle state of the projection instances. E.g. the
     * callee must know to what targets it is sending the "delete state" signal.
     *
     * @param event The event to dispatch.
     * @param restrictToIds Optional set of the target identifiers to which the dispatching must
     *   be restricted; if `null`, no restrictions are applied and the event should be dispatched
     *   as per the routing schema.
     * @return The set of the entity identifiers that actually received the dispatched event.
     * @see CatchUpEndpoint
     */
    private fun sendToCatchingUp(event: Event, restrictToIds: Set<I>?): Set<I> {
        val envelope = EventEnvelope.of(event)
        val catchUpTargets: Set<I> = if (envelope.message() is CatchUpSignal) {
            restrictToIds ?: ImmutableSet.copyOf(index())
        } else {
            val routedTargets = route(envelope)
            if (restrictToIds == null) {
                routedTargets
            } else {
                intersection(routedTargets, restrictToIds).immutableCopy()
            }
        }
        val inbox = inbox()
        for (target in catchUpTargets) {
            inbox.send(envelope).toCatchUp(target)
        }
        return catchUpTargets
    }

    public companion object {

        @JvmStatic
        @JvmName("nullToDefault")
        @VisibleForTesting
        internal fun nullToDefault(timestamp: Timestamp?): Timestamp =
            timestamp ?: Timestamp.getDefaultInstance()
    }
}

private fun checkCatchUpStartTime(since: Timestamp) {
    val whenStarts = TimestampTemporal.from(since)
    if (!whenStarts.isInPast) {
        throw newIllegalArgumentException(
            "The catch-up must be started from the moment in the past, " +
                    "but asked to start at `%s`, while now is `%s`.",
            since, Time.currentTime()
        )
    }
}
