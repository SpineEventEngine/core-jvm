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
import io.spine.base.AggregateState;
import io.spine.server.aggregate.model.AggregateClass;
import io.spine.server.delivery.Inbox;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.entity.EntityRecord;
import io.spine.server.entity.EventProducingRepository;
import io.spine.server.entity.SignalDispatchingEntity;
import io.spine.server.entity.SignalDispatchingRepository;
import io.spine.server.entity.storage.EntityEventStorage;
import io.spine.server.event.EventBus;
import io.spine.server.route.CommandRouting;
import io.spine.server.type.CommandClass;
import io.spine.server.type.EventClass;
import io.spine.server.type.EventEnvelope;
import io.spine.server.type.SignalEnvelope;

import java.util.Optional;
import java.util.Set;

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
 * double-dispatch guard.
 *
 * <p>Three per-repository settings tune this behavior:
 * <ul>
 *     <li>{@link #useDoubleDispatchGuard()} — enables the history-backed double-dispatch guard,
 *         which rejects a signal already seen among the last {@link #eventHistoryDepth()}
 *         dispatches — however long ago, including the earlier dispatches of the current
 *         delivery batch. It is <b>off by default</b> for performance — when enabled, every
 *         dispatch pays a bounded history read. This is a mechanism distinct from
 *         the delivery layer's time-windowed deduplication, not a replacement for it.
 *     <li>{@linkplain #eventHistoryDepth() eventHistoryDepth} — how many recent events
 *         the guard scans on each dispatch when enabled
 *         (default {@value SignalDispatchingEntity#DEFAULT_HISTORY_DEPTH}).
 *     <li>{@link #recordStateHistory()} — records the state history of the aggregates
 *         on each dispatch. This is an opt-in feature shared by all entity repositories
 *         (see {@link io.spine.server.entity.AbstractEntityRepository}).
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
public abstract class AggregateRepository<I,
                                          A extends Aggregate<I, S, ?>,
                                          S extends AggregateState<I>>
        extends SignalDispatchingRepository<I, A, S>
        implements EventProducingRepository {

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
     * <p>Ensures that the aggregates of this repository handle commands, react to events,
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
     * <p>Does nothing by default: an Aggregate may react only to events,
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
     * entity-created} system event. The history loaders and, when enabled, the
     * double-dispatch guard are installed by the parent repository classes.
     */
    @Override
    @OverridingMethodsMustInvokeSuper
    public A create(I id) {
        var aggregate = super.create(id);
        lifecycleOf(id).onEntityCreated(AGGREGATE);
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
     * {@inheritDoc}
     *
     * <p>Always {@code true}: an aggregate repository journals the events emitted by
     * its aggregates unconditionally.
     */
    @Override
    protected final boolean eventHistoryEnabled() {
        return true;
    }

    /**
     * {@inheritDoc}
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
        super.doStore(aggregate);
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
     * <p>Sends the given event to the {@code Inbox}es of the aggregates reacting to it.
     */
    @Override
    protected final DispatchOutcome dispatchTo(Set<I> ids, EventEnvelope event) {
        ids.forEach(id -> inbox().send(event)
                                 .toReactor(id));
        return sentToInbox(event, ids);
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
     * Loads an aggregate by the passed ID.
     *
     * <p>An aggregate will be loaded even if the
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
}
