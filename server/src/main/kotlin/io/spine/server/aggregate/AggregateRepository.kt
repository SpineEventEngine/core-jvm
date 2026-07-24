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

package io.spine.server.aggregate

import com.google.common.collect.ImmutableSet
import com.google.errorprone.annotations.OverridingMethodsMustInvokeSuper
import io.spine.annotation.Internal
import io.spine.base.AggregateState
import io.spine.option.EntityOption.Kind.AGGREGATE
import io.spine.server.aggregate.model.AggregateClass
import io.spine.server.aggregate.model.AggregateClass.asAggregateClass
import io.spine.server.delivery.Inbox
import io.spine.server.delivery.InboxLabel.HANDLE_COMMAND
import io.spine.server.delivery.InboxLabel.REACT_UPON_EVENT
import io.spine.server.dispatch.DispatchOutcome
import io.spine.server.dispatch.DispatchOutcomes.sentToInbox
import io.spine.server.entity.EventProducingRepository
import io.spine.server.entity.SignalDispatchingRepository
import io.spine.server.event.EventBus
import io.spine.server.route.CommandRouting
import io.spine.server.type.CommandClass
import io.spine.server.type.EventClass
import io.spine.server.type.EventEnvelope
import io.spine.server.type.SignalEnvelope
import io.spine.util.Exceptions.newIllegalStateException
import java.util.Optional

/**
 * The repository that manages instances of `Aggregate`s.
 *
 * The repository routes commands and events to its aggregates, stores the events they
 * produce, and posts those events to the [EventBus][io.spine.server.event.EventBus].
 *
 * Since the event-sourcing cutover, an aggregate is loaded from its latest persisted
 * [EntityRecord][io.spine.server.entity.EntityRecord] rather than by replaying its event
 * journal. The repository keeps that latest state in its record storage and appends
 * the emitted events to a separate [event journal][eventStorage]
 * (an [io.spine.server.entity.storage.EntityEventStorage]), kept append-only for
 * traceability and for the opt-in double-dispatch guard.
 *
 * Three per-repository settings tune this behavior:
 *  - [useDoubleDispatchGuard] — enables the history-backed double-dispatch guard,
 *    which rejects a signal already seen among the last [eventHistoryDepth]
 *    dispatches — however long ago, including the earlier dispatches of the current
 *    delivery batch. It is **off by default** for performance — when enabled, every
 *    dispatch pays a bounded history read. This is a mechanism distinct from
 *    the delivery layer's time-windowed deduplication, not a replacement for it.
 *  - [eventHistoryDepth] — how many recent events the guard scans on each dispatch
 *    when enabled (default `SignalDispatchingEntity.DEFAULT_HISTORY_DEPTH`).
 *  - [recordStateHistory] — records the state history of the aggregates
 *    on each dispatch. This is an opt-in feature shared by all entity repositories
 *    (see [io.spine.server.entity.AbstractEntityRepository]).
 *    The aggregates read the recorded history via `stateAt(Timestamp)` and
 *    `stateHistoryBackward(int)` — e.g., for answering the "state at a time"
 *    query. It is **off by default**: recording adds a write to every dispatch,
 *    and the history grows until the application maintains it. Reading the
 *    [state history][stateHistory] of a repository that does not
 *    record it fails fast.
 *
 * @param I The type of the aggregate IDs.
 * @param A The type of the aggregates managed by this repository.
 * @param S The type of the state of aggregates managed by this repository.
 *
 * @see Aggregate
 */
@Suppress("TooManyFunctions") // This base for aggregate repositories has many functions.
public abstract class AggregateRepository<I : Any,
                                          A : Aggregate<I, S, *>,
                                          S : AggregateState<I>>
protected constructor() :
    SignalDispatchingRepository<I, A, S>(),
    EventProducingRepository {

    final override fun eventBus(): EventBus = context().eventBus()

    /**
     * Sets up the inbox with the endpoints reacting upon events and handling commands.
     */
    final override fun setupInbox(builder: Inbox.Builder<I>) {
        builder.addEventEndpoint(REACT_UPON_EVENT) { e -> AggregateEventReactionEndpoint(this, e) }
            .addCommandEndpoint(HANDLE_COMMAND) { c -> AggregateCommandEndpoint(this, c) }
    }

    /**
     * Ensures that the aggregates of this repository handle commands, react to events,
     * or both.
     */
    final override fun checkDispatchesMessages() {
        val handlesCommands = dispatchesCommands()
        val reactsOnEvents = dispatchesEvents()

        if (!handlesCommands && !reactsOnEvents) {
            throw newIllegalStateException(
                "Aggregates of the repository `%s` neither handle commands" +
                        " nor react on events.",
                this
            )
        }
    }

    /**
     * Does nothing by default: an Aggregate may react only to events,
     * in which case no command routing is needed.
     *
     * @param routing The routing schema to customize.
     */
    override fun setupCommandRouting(routing: CommandRouting<I>) {
        // Do nothing.
    }

    /**
     * Creates an aggregate with the given ID, also posting the
     * [entity-created][io.spine.system.server.event.EntityCreated] system event.
     *
     * The history loaders and, when enabled, the double-dispatch guard are installed
     * by the parent repository classes.
     */
    @OverridingMethodsMustInvokeSuper
    override fun create(id: I): A {
        val aggregate = super.create(id)
        lifecycleOf(id).onEntityCreated(AGGREGATE)
        return aggregate
    }

    /** Obtains class information of aggregates managed by this repository. */
    // The model class of an aggregate repository is always an `AggregateClass`.
    @Suppress("UNCHECKED_CAST")
    protected fun aggregateClass(): AggregateClass<A> = entityModelClass() as AggregateClass<A>

    override fun toModelClass(cls: Class<A>): AggregateClass<A> = asAggregateClass(cls)

    /**
     * Always `true`: an aggregate repository journals the events emitted by
     * its aggregates unconditionally.
     */
    final override fun eventHistoryEnabled(): Boolean = true

    /**
     * Stores the aggregate, skipping an instance that was neither changed nor
     * produced events.
     *
     * An overriding repository is expected to call `super`: writing an untouched
     * instance would overwrite the stored state of another instance sharing its ID.
     */
    @Internal
    override fun doStore(entity: A) {
        // Since the cutover the state is persisted independently of visibility, but still only
        // for an aggregate that was actually modified. Persisting an untouched instance would
        // overwrite the stored state of another instance sharing the same ID with a default
        // (empty) record. An unmodified aggregate has no state change and no uncommitted events.
        if (!entity.changed() && !entity.hasUncommittedEvents()) {
            return
        }
        super.doStore(entity)
    }

    /**
     * Obtains a set of classes of commands handled by the aggregates of this repository.
     *
     * @return A set of command classes or an empty set if the aggregates do not handle commands.
     */
    final override fun commandClasses(): ImmutableSet<CommandClass> = aggregateClass().commands()

    @Internal
    final override fun onRoutingFailed(envelope: SignalEnvelope<*, *, *>, cause: Throwable) {
        super.onRoutingFailed(envelope, cause)
        postIfCommandRejected(envelope, cause)
    }

    /**
     * Obtains a set of event classes on which the aggregates of this repository react.
     *
     * @return A set of event classes or an empty set if the aggregates do not react to events.
     */
    override fun messageClasses(): ImmutableSet<EventClass> = aggregateClass().events()

    /**
     * Obtains classes of domestic events on which the aggregates of this repository react.
     *
     * @return A set of event classes or an empty set if the aggregates do not react
     *   to domestic events.
     */
    override fun domesticEventClasses(): ImmutableSet<EventClass> =
        aggregateClass().domesticEvents()

    /**
     * Obtains classes of external events on which the aggregates of this repository react.
     *
     * @return A set of event classes or an empty set if the aggregates do not react
     *   to external events.
     */
    override fun externalEventClasses(): ImmutableSet<EventClass> =
        aggregateClass().externalEvents()

    override fun outgoingEvents(): ImmutableSet<EventClass> = aggregateClass().outgoingEvents()

    override fun canDispatch(envelope: EventEnvelope): Boolean =
        aggregateClass().reactorOf(envelope).isPresent

    /**
     * Sends the given event to the `Inbox`es of the aggregates reacting to it.
     */
    final override fun dispatchTo(
        ids: @JvmSuppressWildcards Set<I>,
        event: EventEnvelope
    ): DispatchOutcome {
        ids.forEach { id ->
            inbox().send(event)
                .toReactor(id)
        }
        return sentToInbox(event, ids)
    }

    /**
     * Loads or creates an aggregate by the passed ID.
     *
     * @param id The ID of the aggregate.
     * @return Loaded or created aggregate instance.
     */
    @JvmName("loadOrCreate") // Keeps the JVM name for the Java `AggregateRoot` caller.
    internal fun loadOrCreate(id: I): A = cache().load(id)

    /**
     * Loads an aggregate by the passed ID.
     *
     * Since the event-sourcing cutover, the aggregate is loaded from its latest persisted
     * [state record][io.spine.server.entity.EntityRecord] rather than by replaying its
     * event journal. The record is read directly from the record storage, regardless of
     * the visibility of the aggregate, so even `NONE`-visibility aggregates load correctly.
     *
     * @param id The ID of the aggregate.
     * @return The loaded instance or `Optional.empty()` if there is no `Aggregate`
     *   with the ID.
     */
    private fun load(id: I): Optional<A> = recordStorage().read(id).map(this::toEntity)

    /**
     * Loads an aggregate by the passed ID.
     *
     * An aggregate will be loaded even if the
     * [archived][io.spine.server.entity.Entity.isArchived]
     * or [deleted][io.spine.server.entity.Entity.isDeleted] lifecycle
     * attribute, or both of them, are set to `true`.
     *
     * @param id The ID of the aggregate to load.
     * @return The aggregate instance, or [empty()][Optional.empty] if there is no
     *   aggregate with such ID.
     */
    override fun find(id: I): Optional<A> = load(id)
}
