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
import io.spine.annotation.Internal
import io.spine.base.AggregateState
import io.spine.core.Event
import io.spine.server.Ignored.ignored
import io.spine.server.aggregate.model.AggregateClass
import io.spine.server.aggregate.model.AggregateClass.asAggregateClass
import io.spine.server.dispatch.DispatchOutcome
import io.spine.server.entity.SignalDispatchingEntity
import io.spine.server.entity.Transaction
import io.spine.server.entity.TransactionalEntity
import io.spine.server.event.EventReactor
import io.spine.server.type.CommandEnvelope
import io.spine.server.type.EventClass
import io.spine.server.type.EventEnvelope
import io.spine.validation.ValidatingBuilder
import java.util.function.Predicate

/**
 * Abstract base for aggregates.
 *
 * An aggregate is the main building block of a business model.
 * Aggregates guarantee the consistency of data modifications in response to
 * commands they receive.
 *
 * An aggregate modifies its state in response to a command — or to an event it reacts to —
 * and produces one or more events describing what happened. Since the event-sourcing cutover the
 * state is persisted directly: an aggregate loads from its latest
 * [EntityRecord][io.spine.server.entity.EntityRecord] rather than by replaying its events.
 * The produced events form an append-only journal kept for traceability and for the opt-in
 * double-dispatch guard.
 *
 * ## Creating an aggregate class
 *
 * To create a new aggregate class:
 *  1. Select a type for identifiers of the aggregate.
 *     If you select to use a typed identifier (which is recommended),
 *     define a protobuf message for the ID type.
 *  2. Define the structure of the aggregate state as a Protobuf message.
 *  3. Generate Java code for ID and state types.
 *  4. Create a new Java class derived from `Aggregate` passing ID and
 *     state types as generic parameters.
 *
 * ### Handling commands and reacting to events
 *
 * Command receptors of an `Aggregate` are defined in the same way as described in
 * [SignalDispatchingEntity]; please also refer to
 * [Assign][io.spine.server.command.Assign].
 * An aggregate may additionally react to events with [`@React`][io.spine.server.event.React]
 * methods. Event(s) returned by these receptors are posted to the
 * [EventBus][io.spine.server.event.EventBus] automatically by [AggregateRepository].
 *
 * ### Changing the state
 *
 * A receptor changes the aggregate state through the builder obtained from
 * [builder()][builder] and returns the event(s) it produces. The framework opens a
 * transaction *before* invoking the receptor — exactly as it does for a
 * [ProcessManager][io.spine.server.procman.ProcessManager] — so the changes accumulated in
 * `builder()` are validated and committed as the new [state()][state] when the
 * receptor returns. The version advances by one per dispatch, and the emitted events carry the
 * aggregate's pre-dispatch version.
 *
 * In Kotlin, a receptor can mutate the state more idiomatically through the builder DSL
 * inherited from [TransactionalEntity] rather
 * than calling [builder()][builder] directly. Each method takes a lambda with the state
 * builder as its receiver:
 *  - `alter` applies the changes to the live builder;
 *  - `update` does the same and returns the builder for further use;
 *  - `tryAlter` validates the candidate state first, applying it only when it is valid
 *    and otherwise leaving the state untouched and returning the constraint violations — a
 *    validate-before-apply guard for conditionally withholding a change.
 *
 * Event sourcing has been removed: an aggregate no longer declares `@Apply` event
 * appliers, and its state is not reconstructed by replaying events. Declaring an applier now
 * fails model building with a [ModelError][io.spine.server.model.ModelError].
 *
 * @param I The type for IDs of this class of aggregates.
 * @param S The type of the state held by the aggregate.
 * @param B The type of the aggregate state builder.
 */
@Suppress("TooManyFunctions") // The class is a framework base and thus has many functions.
public abstract class Aggregate<I : Any,
                                S : AggregateState<I>,
                                B : ValidatingBuilder<S>> :
    SignalDispatchingEntity<I, S, B>,
    EventReactor {

    /**
     * Creates a new instance.
     *
     * Constructors of derived classes are likely to have package-private access
     * level because of the following reasons:
     *  1. These constructors are not public API of an application.
     *     Commands and aggregate IDs are.
     *  2. These constructors need to be accessible from tests in the same package.
     *
     * If you do have tests that create aggregates via constructors, consider annotating
     * them with `@VisibleForTesting`. Otherwise, aggregate constructors (that are
     * invoked by [AggregateRepository] using Reflection) may be left `private`.
     */
    protected constructor() : super()

    /**
     * Creates a new instance.
     *
     * Constructors of derived classes are likely to have package-private access
     * level because of the following reasons:
     *  1. These constructors are not public API of an application.
     *     Commands and aggregate IDs are.
     *  2. These constructors need to be accessible from tests in the same package.
     *
     * If you do have tests that create aggregates via constructors, consider annotating
     * them with `@VisibleForTesting`. Otherwise, aggregate constructors (that are
     * invoked by [AggregateRepository] via Reflection) may be left `private`.
     *
     * @param id The ID for the new aggregate.
     */
    protected constructor(id: I) : super(id)

    /**
     * Obtains model class for this aggregate.
     */
    override fun thisClass(): AggregateClass<*> = super.thisClass() as AggregateClass<*>

    /**
     * Obtains the active transaction of this aggregate.
     *
     * The Java endpoints of this package reach the transaction through the Java
     * package-level slice of `protected`; this override must stay until they move
     * to Kotlin.
     */
    override fun tx(): Transaction<I, out TransactionalEntity<I, S, B>, S, B> = super.tx()

    @Internal
    override fun modelClass(): AggregateClass<*> = asAggregateClass(javaClass)

    /**
     * Obtains the builder of the aggregate state.
     *
     * In `Aggregate` the builder is mutated by a command handler (`@Assign`) or a
     * reactor (`@React`) while the framework-opened transaction is active — the same way a
     * [ProcessManager][io.spine.server.procman.ProcessManager] mutates its state.
     */
    final override fun builder(): B = super.builder()

    /**
     * Obtains a method for the passed command and invokes it.
     *
     * Dispatching the commands results in emitting event messages.
     *
     * @param command The envelope with the command to dispatch.
     * @return The outcome of dispatching the command.
     */
    override fun dispatchCommand(command: CommandEnvelope): DispatchOutcome {
        val duplicate = detectDuplicate(command)
        if (duplicate != null) {
            return duplicate
        }
        val method = thisClass().receptorOf(command)
        return method.invoke(this, command)
    }

    /**
     * Dispatches the event on which the aggregate reacts.
     *
     * Reacting to an event may result in emitting event messages.
     *
     * @param event The envelope with the event to dispatch.
     * @return The outcome of dispatching the event; an ignored outcome if the aggregate
     *   does not react to the event.
     */
    override fun dispatchEvent(event: EventEnvelope): DispatchOutcome {
        val duplicate = detectDuplicate(event)
        if (duplicate != null) {
            return duplicate
        }
        val method = thisClass().reactorOf(event)
        return method.map { reactorMethod -> reactorMethod.invoke(this, event) }
            .orElseGet { ignored(thisClass(), event) }
    }

    @Internal
    override fun producedEvents(): ImmutableSet<EventClass> = modelClass().outgoingEvents()

    /**
     * Returns the message shown when an aggregate's state or lifecycle flags are modified
     * outside a receptor's transaction.
     */
    final override fun missingTxMessage(): String =
        "Modification of aggregate state or its lifecycle flags is not available this way." +
                " Modify it from within a command handler (`@Assign`)" +
                " or an event reactor (`@React`)."

    /**
     * Creates an iterator of the aggregate event history with reverse traversal.
     */
    @Deprecated(
        "Please use `eventHistoryBackward(int)` and state the history window explicitly." +
                " This form reads the last `DEFAULT_HISTORY_DEPTH` events.",
        ReplaceWith("this.eventHistoryBackward(DEFAULT_HISTORY_DEPTH)")
    )
    protected fun historyBackward(): Iterator<Event> =
        eventHistoryBackward(DEFAULT_HISTORY_DEPTH)

    /**
     * Verifies if the aggregate history contains an event that satisfies the passed predicate.
     */
    @Deprecated(
        "Please use `eventHistoryContains(int, Predicate)` and state the history window" +
                " explicitly. This form inspects the last `DEFAULT_HISTORY_DEPTH` events.",
        ReplaceWith("this.eventHistoryContains(DEFAULT_HISTORY_DEPTH, predicate)")
    )
    protected fun historyContains(predicate: Predicate<Event>): Boolean =
        eventHistoryContains(DEFAULT_HISTORY_DEPTH, predicate)
}
