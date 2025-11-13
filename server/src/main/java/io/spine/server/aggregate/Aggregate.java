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
import com.google.protobuf.Empty;
import io.spine.annotation.Internal;
import io.spine.annotation.VisibleForTesting;
import io.spine.base.AggregateState;
import io.spine.core.Event;
import io.spine.core.Version;
import io.spine.protobuf.AnyPacker;
import io.spine.server.aggregate.model.AggregateClass;
import io.spine.server.command.AssigneeEntity;
import io.spine.server.dispatch.BatchDispatchOutcome;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.entity.EventPlayer;
import io.spine.server.entity.HasLifecycleColumns;
import io.spine.server.entity.LifecycleFlags;
import io.spine.server.entity.RecentHistory;
import io.spine.server.event.EventReactor;
import io.spine.server.type.CommandEnvelope;
import io.spine.server.type.EventClass;
import io.spine.server.type.EventEnvelope;
import io.spine.validate.ValidatingBuilder;

import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

import static com.google.common.collect.Iterators.any;
import static io.spine.base.Time.currentTime;
import static io.spine.protobuf.AnyPacker.unpack;
import static io.spine.protobuf.Messages.isNotDefault;
import static io.spine.server.Ignored.ignored;
import static io.spine.server.aggregate.model.AggregateClass.asAggregateClass;
import static io.spine.util.Exceptions.newIllegalStateException;

/**
 * Abstract base for aggregates.
 *
 * <p>An aggregate is the main building block of a business model.
 * Aggregates guarantee consistency of data modifications in response to
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
        implements EventPlayer, EventReactor, HasLifecycleColumns<I, S> {

    private final UncommittedHistory uncommittedHistory = new UncommittedHistory(this::toSnapshot);

    /**
     * A guard for ensuring idempotency of messages dispatched by this aggregate.
     */
    private IdempotencyGuard idempotencyGuard;

    /**
     * Tells whether any applier method is being invoked
     * right now for this instance of aggregate.
     */
    private final ApplierWatcher applierWatcher = new ApplierWatcher();

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
     * <p>In {@code Aggregate} this method must be called only from within an event applier.
     *
     * @throws IllegalStateException
     *         if the method is called from outside an event applier
     */
    @Override
    protected final B builder() {
        return super.builder();
    }

    /**
     * Prohibits invoking {@link #state() state()} method from within an applier method.
     *
     * <p>All applier methods are always invoked in scope of an active transaction.
     * Until this transaction is completed, the {@code state()} of the corresponding aggregate
     * is not up-to-date. Therefore, relying upon it in code is prone to errors,
     * and is prohibited for good sake.
     *
     * @throws IllegalStateException
     *         if this method is called from within an event applier
     */
    @Override
    protected void ensureAccessToState() {
        if (applierWatcher.inProgress()) {
            throw newIllegalStateException(
                    "Aggregate `state()` method must not be used from `@Apply`-marked method." +
                            " Use `builder()` instead. The issue detected in `%s` aggregate class.",
                    getClass().getName());
        }
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
     * @return a list of event messages that the aggregate produces in reaction to the event or
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

    /**
     * Invokes applier method for the passed event message.
     *
     * @param event
     *         the event to apply
     */
    final DispatchOutcome invokeApplier(EventEnvelope event) {
        var method = thisClass().applierOf(event);
        var outcome = applierWatcher.perform(() -> method.invoke(this, event));
        return outcome;
    }

    @Override
    public final BatchDispatchOutcome play(Iterable<Event> events) {
        return EventPlayer
                .forTransactionOf(this)
                .play(events);
    }

    @Override
    @Internal
    public ImmutableSet<EventClass> producedEvents() {
        return modelClass().outgoingEvents();
    }

    /**
     * Restores the aggregate state from the passed event history.
     *
     * <p>The historical events are {@linkplain #play(Iterable) played} on this aggregate instance.
     * If the history includes a {@code Snapshot}, the aggregate state is restored from it first,
     * and only then the event history is applied.
     *
     * @param history
     *         the aggregate state with events to play
     * @throws IllegalStateException
     *         if applying events caused an exception, which is set as the {@code cause} for
     *         the thrown instance
     */
    final BatchDispatchOutcome replay(AggregateHistory history) {
        var snapshot = history.getSnapshot();
        if (isNotDefault(snapshot)) {
            restore(snapshot);
        }
        var events = history.getEventList();
        var batchDispatchOutcome = play(events);
        uncommittedHistory.onAggregateRestored(history);
        appendToRecentHistory(events);
        return batchDispatchOutcome;
    }

    /**
     * Applies events to this {@code Aggregate}.
     *
     * <p>Before applying the events, changes their versions as follows:
     * <ol>
     *     <li>The first event in the list gets the current version of the aggregate incremented
     *         by one.
     *     <li>All the next events get the following versions.
     * </ol>
     *
     * <p>For example, if the current version number of the aggregate is {@code 42}, and
     * the {@code events} list is of size 3, the applied events will have versions {@code 43},
     * {@code 44}, and {@code 45}.
     *
     * <p>All the events applied to the aggregate instance are
     * {@linkplain UncommittedHistory#startTracking(int) tracked} as a part of the aggregate's
     * {@link UncommittedHistory} and later are stored.
     *
     * <p>If during the application of the events, the number of the events since the last snapshot
     * exceeds the passed snapshot trigger, a new snapshot is made. The snapshot is then tracked
     * as a part of the aggregate's {@code UncommittedHistory}.
     *
     * @param events
     *         the events to apply
     * @param snapshotTrigger
     *         the snapshot trigger
     * @return the exact list of {@code events} but with adjusted versions
     */
    final BatchDispatchOutcome apply(List<Event> events, int snapshotTrigger) {
        var versionSequence = new VersionSequence(version());
        var versionedEvents = versionSequence.update(events);
        uncommittedHistory.startTracking(snapshotTrigger);
        var result = play(versionedEvents);
        uncommittedHistory.stopTracking();
        return result;
    }

    /**
     * A callback telling that the event has been played on this aggregate in scope
     * of a transaction.
     *
     * <p>If this event is new in the aggregate history (e.g. it's not already stored), it is
     * recorded as a part of the aggregate's {@link UncommittedHistory}.
     */
    final void onAfterEventPlayed(EventEnvelope event) {
        uncommittedHistory.track(event);
    }

    /**
     * Restores the state, the version and the lifecycle flags from the passed snapshot.
     *
     * <p>This method must be invoked in the scope of an {@linkplain #isTransactionInProgress()
     * active transaction}.
     *
     * @param snapshot
     *         the snapshot with the state to restore
     */
    final void restore(Snapshot snapshot) {
        @SuppressWarnings("unchecked") /* The cast is safe since the snapshot is created
            with the state of this aggregate, which is bound by the type <S>. */
        var stateToRestore = (S) unpack(snapshot.getState());
        var versionFromSnapshot = snapshot.getVersion();
        setInitialState(stateToRestore, versionFromSnapshot);
        var lifecycle = snapshot.getLifecycle();
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
     */
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
     * Creates an iterator of the aggregate event history with reverse traversal.
     *
     * <p>The records are returned sorted by timestamp in descending order
     * (from newer to older).
     *
     * <p>The iterator is empty if there's no history for the aggregate.
     *
     * @return new iterator instance
     */
    protected final Iterator<Event> historyBackward() {
        return recentHistory().iterator();
    }

    /**
     * Verifies if the aggregate history contains an event which satisfies the passed predicate.
     */
    protected final boolean historyContains(Predicate<Event> predicate) {
        var iterator = historyBackward();
        var found = any(iterator, predicate::test);
        return found;
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
