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
import com.google.protobuf.Empty;
import io.spine.annotation.Internal;
import io.spine.annotation.VisibleForTesting;
import io.spine.base.AggregateState;
import io.spine.base.Identifier;
import io.spine.core.Event;
import io.spine.core.Version;
import io.spine.server.aggregate.model.AggregateClass;
import io.spine.server.command.AssigneeEntity;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.entity.EntityRecord;
import io.spine.server.entity.RecentEventHistory;
import io.spine.server.entity.RecentStateHistory;
import io.spine.server.entity.Transaction;
import io.spine.server.entity.TransactionalEntity;
import io.spine.server.event.EventReactor;
import io.spine.server.type.CommandEnvelope;
import io.spine.server.type.EventClass;
import io.spine.server.type.EventEnvelope;
import io.spine.validation.ValidatingBuilder;

import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

import static com.google.common.collect.Iterators.any;
import static io.spine.protobuf.AnyPacker.pack;
import static io.spine.server.Ignored.ignored;
import static io.spine.server.aggregate.model.AggregateClass.asAggregateClass;

/**
 * Abstract base for aggregates.
 *
 * <p>An aggregate is the main building block of a business model.
 * Aggregates guarantee the consistency of data modifications in response to
 * commands they receive.
 *
 * <p>An aggregate modifies its state in response to a command — or to an event it reacts to —
 * and produces one or more events describing what happened. Since the event-sourcing cutover the
 * state is persisted directly: an aggregate loads from its latest
 * {@link io.spine.server.entity.EntityRecord EntityRecord} rather than by replaying its events.
 * The produced events form an append-only journal kept for traceability and for the opt-in
 * {@link IdempotencyGuard}.
 *
 * <h2>Creating an aggregate class</h2>
 *
 * <p>To create a new aggregate class:
 * <ol>
 *     <li>Select a type for identifiers of the aggregate.
 *         If you select to use a typed identifier (which is recommended),
 *         define a protobuf message for the ID type.
 *     <li>Define the structure of the aggregate state as a Protobuf message.
 *     <li>Generate Java code for ID and state types.
 *     <li>Create a new Java class derived from {@code Aggregate} passing ID and
 *         state types as generic parameters.
 * </ol>
 *
 * <h3>Handling commands and reacting to events</h3>
 *
 * <p>Command receptors of an {@code Aggregate} are defined in the same way as described in
 * {@link AssigneeEntity}; please also refer to {@link io.spine.server.command.Assign Assign}.
 * An aggregate may additionally react to events with {@link io.spine.server.event.React @React}
 * methods. Event(s) returned by these receptors are posted to the
 * {@link io.spine.server.event.EventBus EventBus} automatically by {@link AggregateRepository}.
 *
 * <h3>Changing the state</h3>
 *
 * <p>A receptor changes the aggregate state through the builder obtained from
 * {@link #builder() builder()} and returns the event(s) it produces. The framework opens a
 * transaction <em>before</em> invoking the receptor — exactly as it does for a
 * {@link io.spine.server.procman.ProcessManager ProcessManager} — so the changes accumulated in
 * {@code builder()} are validated and committed as the new {@link #state() state()} when the
 * receptor returns. The version advances by one per dispatch, and the emitted events carry the
 * aggregate's pre-dispatch version.
 *
 * <p>In Kotlin, a receptor can mutate the state more idiomatically through the builder DSL
 * inherited from {@link io.spine.server.entity.TransactionalEntity TransactionalEntity} rather
 * than calling {@link #builder() builder()} directly. Each method takes a lambda with the state
 * builder as its receiver:
 * <ul>
 *     <li>{@code alter} applies the changes to the live builder;
 *     <li>{@code update} does the same and returns the builder for further use;
 *     <li>{@code tryAlter} validates the candidate state first, applying it only when it is valid
 *         and otherwise leaving the state untouched and returning the constraint violations — a
 *         validate-before-apply guard for conditionally withholding a change.
 * </ul>
 *
 * <p>Event sourcing has been removed: an aggregate no longer declares {@code @Apply} event
 * appliers, and its state is not reconstructed by replaying events. Declaring an applier now fails
 * model building with a {@link io.spine.server.model.ModelError ModelError}.
 *
 * @param <I>
 *         the type for IDs of this class of aggregates
 * @param <S>
 *         the type of the state held by the aggregate
 * @param <B>
 *         the type of the aggregate state builder
 */
public abstract class Aggregate<I,
                                S extends AggregateState<I>,
                                B extends ValidatingBuilder<S>>
        extends AssigneeEntity<I, S, B>
        implements EventReactor {

    /**
     * The fixed number of the most recent journal events read by the deprecated parameterless
     * {@linkplain #historyBackward() history accessors}.
     *
     * <p>Equal to {@code AggregateRepository.DEFAULT_HISTORY_DEPTH}.
     */
    private static final int DEFAULT_HISTORY_DEPTH = 100;

    private final UncommittedHistory uncommittedHistory = new UncommittedHistory();

    /**
     * A guard for ensuring idempotency of messages dispatched by this aggregate.
     */
    private IdempotencyGuard idempotencyGuard;

    /**
     * Creates a new instance.
     *
     * @apiNote Constructors of derived classes are likely to have package-private access
     *         level because of the following reasons:
     *         <ol>
     *           <li>These constructors are not public API of an application.
     *                Commands and aggregate IDs are.
     *           <li>These constructors need to be accessible from tests in the same package.
     *         </ol>
     *
     *         <p>If you do have tests that create aggregates via constructors, consider annotating
     *         them with {@code @VisibleForTesting}. Otherwise, aggregate constructors (that are
     *         invoked by {@link io.spine.server.aggregate.AggregateRepository AggregateRepository}
     *         using Reflection) may be left {@code private}.
     */
    protected Aggregate() {
        super();
        setIdempotencyGuard();
    }

    /**
     * Creates a new instance.
     *
     * @param id
     *         the ID for the new aggregate
     * @apiNote Constructors of derived classes are likely to have package-private access
     *         level because of the following reasons:
     *         <ol>
     *           <li>These constructors are not public API of an application.
     *               Commands and aggregate IDs are.
     *           <li>These constructors need to be accessible from tests in the same package.
     *         </ol>
     *
     *         <p>If you do have tests that create aggregates via constructors, consider annotating
     *         them with {@code @VisibleForTesting}. Otherwise, aggregate constructors (that are
     *         invoked by {@link io.spine.server.aggregate.AggregateRepository AggregateRepository}
     *         via Reflection) may be left {@code private}.
     */
    protected Aggregate(I id) {
        super(id);
        setIdempotencyGuard();
    }

    /**
     * Creates and assigns the aggregate an {@link IdempotencyGuard idempotency guard}.
     */
    private void setIdempotencyGuard() {
        idempotencyGuard = new IdempotencyGuard(this);
    }

    /**
     * Enables the opt-in {@link IdempotencyGuard} for this aggregate, scanning up to
     * {@code historyDepth} most recent events for a duplicate on each dispatch.
     */
    final void enableIdempotencyGuard(int historyDepth) {
        idempotencyGuard.enable(historyDepth);
    }

    /**
     * Obtains model class for this aggregate.
     */
    @Override
    protected AggregateClass<?> thisClass() {
        return (AggregateClass<?>) super.thisClass();
    }

    @Internal
    @Override
    public AggregateClass<?> modelClass() {
        return asAggregateClass(getClass());
    }

    /**
     * {@inheritDoc}
     *
     * <p>In {@code Aggregate} the builder is mutated by a command handler ({@code @Assign}) or a
     * reactor ({@code @React}) while the framework-opened transaction is active — the same way a
     * {@link io.spine.server.procman.ProcessManager ProcessManager} mutates its state.
     */
    @Override
    protected final B builder() {
        return super.builder();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Overridden to be accessible from the {@code aggregate} package so that the endpoint can
     * dispatch a command or an event through the aggregate's transaction, mirroring
     * {@link io.spine.server.procman.ProcessManager#tx() ProcessManager.tx()}.
     */
    @Override
    protected Transaction<I, ? extends TransactionalEntity<I, S, B>, S, B> tx() {
        return super.tx();
    }

    /**
     * Obtains a method for the passed command and invokes it.
     *
     * <p>Dispatching the commands results in emitting event messages. All the
     * {@linkplain Empty empty} messages are filtered out from the result.
     *
     * @param command
     *         the envelope with the command to dispatch
     * @return a list of event messages that the aggregate produces by handling the command
     */
    @Override
    protected DispatchOutcome dispatchCommand(CommandEnvelope command) {
        var error = idempotencyGuard.check(command);
        if (error.isPresent()) {
            var outcome = DispatchOutcome.newBuilder()
                    .setPropagatedSignal(command.messageId())
                    .setError(error.get())
                    .build();
            return outcome;
        } else {
            var method = thisClass().receptorOf(command);
            var outcome = method.invoke(this, command);
            return outcome;
        }
    }

    /**
     * Dispatches the event on which the aggregate reacts.
     *
     * <p>Reacting to an event may result in emitting event messages.
     * All the {@linkplain Empty empty} messages are filtered out from the result.
     *
     * @param event
     *         the envelope with the event to dispatch
     * @return a list of event messages that the aggregate produces in reaction to the event, or
     *         an empty list if the aggregate state does not change because of the event
     */
    DispatchOutcome reactOn(EventEnvelope event) {
        var error = idempotencyGuard.check(event);
        if (error.isPresent()) {
            var outcome = DispatchOutcome.newBuilder()
                    .setPropagatedSignal(event.messageId())
                    .setError(error.get())
                    .build();
            return outcome;
        }
        var method = thisClass().reactorOf(event);
        return method.map(reactorMethod -> reactorMethod.invoke(this, event))
                     .orElseGet(() -> ignored(thisClass(), event));
    }

    @Override
    @Internal
    public ImmutableSet<EventClass> producedEvents() {
        return modelClass().outgoingEvents();
    }

    /**
     * Records the events produced by the current dispatch so that they are stored into the
     * journal and made available as recent history.
     *
     * <p>Called by the framework after a command or reaction has been dispatched and its
     * transaction committed. Rejection events are not journaled.
     *
     * <p>The journaled events also enter the {@linkplain #recentEventHistory() recent
     * event history} right away, so the subsequent dispatches served by this instance —
     * e.g., the later signals of a delivery batch — read them without waiting for
     * the journal write, which may be deferred to the end of the batch.
     *
     * @param events
     *         the events emitted by the current command handler or reactor
     */
    final void recordEvents(List<Event> events) {
        var journaled = uncommittedHistory.record(events);
        recentEventHistory().append(journaled);
    }

    /**
     * Returns all uncommitted events.
     *
     * @return immutable view of all uncommitted events
     */
    @VisibleForTesting
    UncommittedEvents getUncommittedEvents() {
        return uncommittedHistory.events();
    }

    /**
     * Tells if there are any uncommitted events.
     */
    boolean hasUncommittedEvents() {
        return uncommittedHistory.hasEvents();
    }

    /**
     * Returns the uncommitted events of this aggregate.
     */
    UncommittedHistory uncommittedHistory() {
        return uncommittedHistory;
    }

    /**
     * Marks the uncommitted events of this aggregate as committed and clears them.
     */
    final void commitEvents() {
        uncommittedHistory.commit();
    }

    /**
     * Returns the message shown when an aggregate's state or lifecycle flags are modified outside
     * a receptor's transaction.
     */
    @Override
    protected final String missingTxMessage() {
        return "Modification of aggregate state or its lifecycle flags is not available this way." +
                " Modify it from within a command handler (`@Assign`)" +
                " or an event reactor (`@React`).";
    }

    /**
     * {@inheritDoc}
     *
     * <p>Opens the method to this package for testing.
     */
    @VisibleForTesting
    @Override
    protected final RecentEventHistory recentEventHistory() {
        return super.recentEventHistory();
    }

    /**
     * Creates an iterator over up to {@code depth} most recent events of this aggregate's
     * history, newest first.
     *
     * <p>Fewer events are returned if the history retains fewer. The events emitted by the
     * current, not-yet-committed dispatch are excluded. The events committed by the earlier
     * dispatches served by this instance — e.g., the preceding signals of a
     * delivery batch — are included even while the deferred journal write has
     * not persisted them yet.
     *
     * @param depth
     *         the maximal number of the most recent events to return; must be positive
     * @return new iterator instance
     * @throws IllegalArgumentException
     *         if the {@code depth} is not positive
     */
    protected final Iterator<Event> eventHistoryBackward(int depth) {
        return recentEventHistory().read(depth);
    }

    /**
     * Verifies if up to {@code depth} most recent events of this aggregate's history contain an
     * event that satisfies the passed predicate.
     *
     * <p>The visibility caveats of {@link #eventHistoryBackward(int)} apply to this check.
     *
     * @param depth
     *         the maximal number of the most recent events to inspect; must be positive
     * @param predicate
     *         the predicate to test the events against
     */
    protected final boolean eventHistoryContains(int depth, Predicate<Event> predicate) {
        var iterator = eventHistoryBackward(depth);
        return any(iterator, predicate::test);
    }

    /**
     * Creates an iterator of the aggregate event history with reverse traversal.
     *
     * @deprecated Please use {@link #eventHistoryBackward(int)} and state the history window
     *         explicitly. This form reads the last {@value #DEFAULT_HISTORY_DEPTH} events.
     */
    @Deprecated
    protected final Iterator<Event> historyBackward() {
        return eventHistoryBackward(DEFAULT_HISTORY_DEPTH);
    }

    /**
     * Verifies if the aggregate history contains an event that satisfies the passed predicate.
     *
     * @deprecated Please use {@link #eventHistoryContains(int, Predicate)} and state the history
     *         window explicitly. This form inspects the last {@value #DEFAULT_HISTORY_DEPTH}
     *         events.
     */
    @Deprecated
    protected final boolean historyContains(Predicate<Event> predicate) {
        return eventHistoryContains(DEFAULT_HISTORY_DEPTH, predicate);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Overrides to expose the method to the package.
     */
    @Override
    @VisibleForTesting
    protected final int versionNumber() {
        return super.versionNumber();
    }
}
