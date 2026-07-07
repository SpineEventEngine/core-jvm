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
import io.spine.core.Event;
import io.spine.core.Version;
import io.spine.protobuf.AnyPacker;
import io.spine.server.aggregate.model.AggregateClass;
import io.spine.server.command.AssigneeEntity;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.entity.EntityRecord;
import io.spine.server.entity.HasLifecycleColumns;
import io.spine.server.entity.LifecycleFlags;
import io.spine.server.entity.RecentHistory;
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterators.any;
import static com.google.common.collect.Iterators.limit;
import static io.spine.base.Time.currentTime;
import static io.spine.protobuf.AnyPacker.unpack;
import static io.spine.server.Ignored.ignored;
import static io.spine.server.aggregate.model.AggregateClass.asAggregateClass;

/**
 * Abstract base for aggregates.
 *
 * <p>An aggregate is the main building block of a business model.
 * Aggregates guarantee the consistency of data modifications in response to
 * commands they receive.
 *
 * <p>An aggregate modifies its state in response to a command and produces
 * one or more events. These events are used later to restore the state of the
 * aggregate.
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
 *     <li>Create new Java class derived from {@code Aggregate} passing ID and
 *         state types as generic parameters.
 * </ol>
 *
 * <h3>Assigning methods to handle commands</h3>
 *
 * <p>Command receptors of an {@code Aggregate} are defined in
 * the same way as described in {@link AssigneeEntity}.
 * Please also refer to {@link io.spine.server.command.Assign Assign}.
 *
 * <p>Event(s) returned by command-handling methods are posted to
 * the {@link io.spine.server.event.EventBus EventBus} automatically
 * by {@link AggregateRepository}.
 *
 * <h3>Adding event appliers</h3>
 *
 * <p>Aggregate data is stored as a sequence of events it produces.
 * The state of the aggregate is restored by re-playing the history of
 * events and invoking corresponding <em>event applier methods</em>.
 *
 * <p>An event applier is a method that changes the state of the aggregate
 * in response to an event. An event applier takes a single parameter of the
 * event message it handles and returns {@code void}.
 * Please see {@link Apply} for more details.
 *
 * <p>The modification of the state is done using a builder instance obtained
 * from {@link #builder() builder()}. All changes to state become reflected
 * in {@link #state() state()}, after <em>all</em> events (obtained from
 * aggregate's history when loading an aggregate, or emitted by command handlers
 * during the command dispatching) are played.
 *
 * <p>End-users must not call {@code state()} method within an event applier.
 * It is so, because event appliers are invoked in scope of an active transaction,
 * which accumulates the model updates in aggregate's {@code builder()},
 * and not in {@code state()}. Therefore, {@code state()} invocation from
 * the applier's code may return some inconsistent result, and thus
 * is prone to errors. All such attempts will result in a {@code RuntimeException}.
 *
 * <p>An {@code Aggregate} class must have applier methods for
 * <em>all</em> types of the events that it produces.
 *
 * <h2>Performance considerations</h2>
 *
 * <p>To improve performance of loading aggregates, an
 * {@link AggregateRepository} periodically stores aggregate snapshots.
 * See {@link AggregateRepository#setSnapshotTrigger(int)} for details.
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
        implements EventReactor, HasLifecycleColumns<I, S> {

    /**
     * The number of the most recent journal events exposed by the deprecated parameterless
     * {@linkplain #historyBackward() history accessors} and used as the window of the opt-in
     * {@link IdempotencyGuard}.
     *
     * <p>Equal to the former {@code AggregateRepository.DEFAULT_SNAPSHOT_TRIGGER}.
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
     * <p>Reacting on an event may result in emitting event messages.
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
        if (method.isEmpty()) {
            return ignored(thisClass(), event);
        }
        return method.get().invoke(this, event);

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
     * @param events the events emitted by the current command handler or reactor
     */
    final void recordEvents(List<Event> events) {
        uncommittedHistory.record(events);
    }

    /**
     * Restores the state, the version, and the lifecycle flags of this aggregate from the
     * passed record loaded from the state storage.
     *
     * <p>Since the event-sourcing cutover, an aggregate loads from its latest persisted
     * {@link EntityRecord} rather than by replaying its event journal. This method must be
     * invoked in the scope of an {@linkplain #isTransactionInProgress() active transaction}.
     *
     * @param record
     *         the latest state record of the aggregate
     */
    final void restore(EntityRecord record) {
        @SuppressWarnings("unchecked") /* The cast is safe since the record holds the state of
            this aggregate, which is bound by the type <S>. */
        var stateToRestore = (S) unpack(record.getState());
        var version = record.getVersion();
        setInitialState(stateToRestore, version);
        var lifecycle = record.getLifecycleFlags();
        setArchived(lifecycle.getArchived());
        setDeleted(lifecycle.getDeleted());
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
     * Returns the uncommitted events and snapshots for this aggregate.
     */
    UncommittedHistory uncommittedHistory() {
        return uncommittedHistory;
    }

    /**
     * {@linkplain #appendToRecentHistory Remembers} the uncommitted events as
     * the {@link io.spine.server.entity.RecentHistory RecentHistory} and clears them.
     */
    final void commitEvents() {
        List<Event> recentEvents = uncommittedHistory.events()
                                                     .list();
        appendToRecentHistory(recentEvents);
        uncommittedHistory.commit();
    }

    /**
     * Instructs to modify the state of an aggregate only within an event applier method.
     */
    @Override
    protected final String missingTxMessage() {
        return "Modification of aggregate state or its lifecycle flags is not available this way." +
                " Make sure to modify those only from an event applier method.";
    }

    /**
     * Transforms the current state of the aggregate into the {@link Snapshot} instance.
     *
     * <p>If the {@linkplain #isTransactionInProgress() transaction is in progress}, the state,
     * version and lifecycle are taken from the transactional data.
     *
     * @return new snapshot
     * @deprecated Snapshots were removed with event-sourced loading; an aggregate now loads from
     *         its latest {@link EntityRecord}. Retained only for wire compatibility of the
     *         {@code Snapshot} message and scheduled for removal in v2.0.0.
     */
    @Deprecated
    final Snapshot toSnapshot() {
        S state;
        Version version;
        LifecycleFlags lifecycle;
        if (isTransactionInProgress()) {
            AggregateTransaction<?, ?, ?> tx = (AggregateTransaction<?, ?, ?>) tx();
            state = builder().buildPartial();
            version = tx.currentVersion();
            lifecycle = tx.lifecycleFlags();
        } else {
            state = state();
            version = version();
            lifecycle = lifecycleFlags();
        }

        var packedState = AnyPacker.pack(state);
        var builder = Snapshot.newBuilder()
                .setState(packedState)
                .setVersion(version)
                .setTimestamp(currentTime())
                .setLifecycle(lifecycle);
        return builder.build();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Opens the method for the repository.
     */
    @Override
    protected final void clearRecentHistory() {
        super.clearRecentHistory();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Opens the method to this package for testing.
     */
    @VisibleForTesting
    @Override
    protected final RecentHistory recentHistory() {
        return super.recentHistory();
    }

    /**
     * Creates an iterator over up to {@code depth} most recent events of this aggregate's
     * journal, newest first.
     *
     * <p>Fewer events are returned if the journal holds fewer. The events emitted by the
     * current, not-yet-committed dispatch are excluded.
     *
     * @param depth
     *         the maximal number of the most recent events to return; must be positive
     * @return new iterator instance
     */
    protected final Iterator<Event> historyBackward(int depth) {
        checkArgument(depth > 0, "History depth must be positive. Got %s.", depth);
        return limit(recentHistory().iterator(), depth);
    }

    /**
     * Verifies if up to {@code depth} most recent events of this aggregate's journal contain an
     * event that satisfies the passed predicate.
     *
     * @param depth
     *         the maximal number of the most recent events to inspect; must be positive
     * @param predicate
     *         the predicate to test the events against
     */
    protected final boolean historyContains(int depth, Predicate<Event> predicate) {
        var iterator = historyBackward(depth);
        return any(iterator, predicate::test);
    }

    /**
     * Creates an iterator of the aggregate event history with reverse traversal.
     *
     * @deprecated Please use {@link #historyBackward(int)} and state the history window
     *         explicitly. This form reads the last {@value #DEFAULT_HISTORY_DEPTH} events.
     */
    @Deprecated
    protected final Iterator<Event> historyBackward() {
        return historyBackward(DEFAULT_HISTORY_DEPTH);
    }

    /**
     * Verifies if the aggregate history contains an event that satisfies the passed predicate.
     *
     * @deprecated Please use {@link #historyContains(int, Predicate)} and state the history
     *         window explicitly. This form inspects the last {@value #DEFAULT_HISTORY_DEPTH}
     *         events.
     */
    @Deprecated
    protected final boolean historyContains(Predicate<Event> predicate) {
        return historyContains(DEFAULT_HISTORY_DEPTH, predicate);
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
