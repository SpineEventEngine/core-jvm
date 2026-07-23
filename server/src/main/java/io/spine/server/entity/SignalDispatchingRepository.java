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

package io.spine.server.entity;

import com.google.errorprone.annotations.OverridingMethodsMustInvokeSuper;
import io.spine.base.EntityState;
import io.spine.server.BoundedContext;
import io.spine.server.commandbus.CommandDispatcherDelegate;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.entity.storage.EntityEventStorage;
import io.spine.server.route.CommandRouting;
import io.spine.server.route.setup.CommandRoutingSetup;
import io.spine.server.type.CommandEnvelope;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.server.dispatch.DispatchOutcomes.maybeSentToInbox;
import static io.spine.server.tenant.TenantAwareRunner.with;
import static io.spine.util.Exceptions.newIllegalStateException;
import static io.spine.util.Suppliers2.memoize;

/**
 * Abstract base for repositories that dispatch signals — both events and commands —
 * to the entities they manage.
 *
 * <p>The entities of such a repository emit events. The repository appends the emitted
 * events to the per-entity {@linkplain #eventStorage() event journal}, kept append-only
 * for traceability. The journal serves the
 * {@linkplain SignalDispatchingEntity#eventHistoryBackward(int) recent-history reads}
 * and the opt-in double-dispatch guard. Whether the journal is written is determined by
 * {@link #eventHistoryEnabled()}: the journaling is unconditional for aggregates and
 * opt-in for process managers.
 *
 * <p>Two per-repository settings tune the machinery:
 * <ul>
 *     <li>{@link #useDoubleDispatchGuard()} — enables the history-backed double-dispatch
 *         guard, which rejects a signal already seen among the last
 *         {@link #eventHistoryDepth()} dispatches;
 *     <li>{@linkplain #eventHistoryDepth() eventHistoryDepth} — how many recent events
 *         the guard scans on each dispatch when enabled.
 * </ul>
 *
 * @param <I>
 *         the type of the entity identifiers
 * @param <E>
 *         the type of managed entities
 * @param <S>
 *         the type of the entity state
 */
@SuppressWarnings("resource" /* Accessing `Closeable` properties. */)
public abstract class SignalDispatchingRepository<I,
                                                  E extends SignalDispatchingEntity<I, S, ?>,
                                                  S extends EntityState<I>>
        extends EventDispatchingRepository<I, E, S>
        implements CommandDispatcherDelegate {

    /** The command routing schema used by this repository. */
    private final Supplier<CommandRouting<I>> commandRouting;

    /** Whether the opt-in double-dispatch guard is enabled for this repository. */
    private boolean doubleDispatchGuardEnabled = false;

    /** The window (in journal events) the opt-in double-dispatch guard scans. */
    private int eventHistoryDepth = SignalDispatchingEntity.DEFAULT_HISTORY_DEPTH;

    /**
     * The journal of the events emitted by the entities of this repository; created
     * lazily once the journal is first needed.
     */
    private @MonotonicNonNull EntityEventStorage<I> eventStorage;

    protected SignalDispatchingRepository() {
        super();
        this.commandRouting = memoize(() -> CommandRouting.newInstance(idClass()));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Registers with the {@code CommandBus} for dispatching commands
     * (via a {@linkplain io.spine.server.commandbus.DelegatingCommandDispatcher
     * delegating dispatcher}), and sets up the routing of the commands.
     *
     * <p>Before any of that, verifies that the configuration of this repository is
     * consistent — e.g., that the {@linkplain #useDoubleDispatchGuard() double-dispatch
     * guard}, if enabled, has the {@linkplain #eventHistoryEnabled() event journal} it
     * scans. The check precedes the registration side effects, so a misconfigured
     * repository is never left subscribed to the buses of the context.
     *
     * @param context
     *         the {@code BoundedContext} of this repository
     */
    @Override
    @OverridingMethodsMustInvokeSuper
    public void registerWith(BoundedContext context) {
        checkGuardHasJournal();
        super.registerWith(context);
        doSetupCommandRouting();
    }

    private void doSetupCommandRouting() {
        var cmdRouting = commandRouting();
        var entityClass = entityClass();
        CommandRoutingSetup.apply(entityClass, cmdRouting);
        setupCommandRouting(cmdRouting);
    }

    /**
     * Ensures the double-dispatch guard, if enabled, is backed by the event journal.
     *
     * <p>The guard scans the journal of the entity — with the journaling disabled it
     * could see only the events of the current instance, silently missing the duplicates
     * arriving after a cache eviction or a restart. Such a configuration is an error.
     */
    private void checkGuardHasJournal() {
        if (doubleDispatchGuardEnabled() && !eventHistoryEnabled()) {
            throw newIllegalStateException(
                    "The double-dispatch guard of the repository `%s` requires the event" +
                            " journal it scans, but the event journaling is disabled for" +
                            " this repository. Enable the journaling along with the guard.",
                    this);
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
    protected abstract void setupCommandRouting(CommandRouting<I> routing);

    /**
     * Obtains command routing schema used by this repository.
     */
    private CommandRouting<I> commandRouting() {
        return commandRouting.get();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Installs the event history loader and, when enabled, the double-dispatch guard
     * on the created entity.
     */
    @Override
    @OverridingMethodsMustInvokeSuper
    public E create(I id) {
        var entity = super.create(id);
        setUpEventHistoryReading(entity, id);
        return entity;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Installs the event history loader and, when enabled, the double-dispatch guard
     * on the reconstructed entity.
     */
    @Override
    @OverridingMethodsMustInvokeSuper
    protected E toEntity(EntityRecord record) {
        var entity = super.toEntity(record);
        setUpEventHistoryReading(entity, entity.id());
        return entity;
    }

    /**
     * Installs the event history loader and, when enabled, the double-dispatch guard
     * on a newly created or reconstructed entity.
     *
     * <p>The loader is installed unconditionally: whether this repository journals the
     * emitted events gates only the behavior of the installed loader — while the
     * journaling is off, reading through it fails fast rather than serving an empty
     * history. An entity created outside a repository has no loader and reads empty.
     *
     * @param entity
     *         the entity to set up
     * @param id
     *         the identifier of the entity
     */
    private void setUpEventHistoryReading(E entity, I id) {
        entity.setEventHistoryLoader(eventHistoryLoaderFor(id));
        if (doubleDispatchGuardEnabled()) {
            entity.enableDoubleDispatchGuard(eventHistoryDepth());
        }
    }

    /**
     * Creates a loader reading the journaled events of the entity with the given identifier.
     */
    private EventHistoryLoader eventHistoryLoaderFor(I id) {
        return (depth, startingFrom) -> {
            if (!eventHistoryEnabled()) {
                throw newIllegalStateException(
                        "The repository `%s` does not journal the events emitted by its" +
                                " entities, so their event history is not readable." +
                                " Enable the journaling for this repository — see" +
                                " `eventHistoryEnabled()` — before reading the history.",
                        this);
            }
            return eventStorage().historyBackward(id, depth, startingFrom);
        };
    }

    /**
     * Dispatches the command to a corresponding entity.
     *
     * <p>If there is no stored entity with such an ID,
     * a new entity is created and stored after it handles the passed command.
     *
     * @param command
     *         the command to dispatch
     */
    @Override
    public final DispatchOutcome dispatchCommand(CommandEnvelope command) {
        checkNotNull(command);
        var target = route(command);
        target.ifPresent(id -> inbox().send(command)
                                      .toHandler(id));
        return maybeSentToInbox(command, target);
    }

    private Optional<I> route(CommandEnvelope cmd) {
        var target = route(commandRouting(), cmd);
        target.ifPresent(id -> onCommandTargetSet(id, cmd));
        return target;
    }

    private void onCommandTargetSet(I id, CommandEnvelope cmd) {
        var lifecycle = lifecycleOf(id);
        var commandId = cmd.id();
        with(cmd.tenantId())
                .run(() -> lifecycle.onTargetAssignedToCommand(commandId));
    }

    /**
     * Enables the opt-in, history-backed double-dispatch guard for the entities of
     * this repository.
     *
     * <p>When enabled, each dispatch scans a bounded window of the entity's recent events —
     * including the events of the current delivery batch that have not reached the journal
     * yet — and rejects a signal already seen among them, however long ago it was dispatched.
     * This mechanism is distinct from the delivery layer's time-windowed deduplication. The
     * guard is <b>off by default</b> for performance: it adds a bounded history read to every
     * dispatch.
     *
     * <p>The guard scans the {@linkplain #eventStorage() event journal} of the entity, so it
     * requires the {@linkplain #eventHistoryEnabled() journaling to be enabled}: registering
     * a repository that enables the guard while leaving the journaling off fails with a
     * configuration error.
     */
    protected void useDoubleDispatchGuard() {
        this.doubleDispatchGuardEnabled = true;
    }

    /**
     * Tells whether the opt-in double-dispatch guard is enabled for this repository.
     *
     * @return {@code false} by default
     */
    protected boolean doubleDispatchGuardEnabled() {
        return doubleDispatchGuardEnabled;
    }

    /**
     * Returns the number of the most recent events scanned by the opt-in
     * double-dispatch guard when it is enabled.
     *
     * @return a positive integer value; the default is
     *         {@value SignalDispatchingEntity#DEFAULT_HISTORY_DEPTH}
     */
    protected int eventHistoryDepth() {
        return this.eventHistoryDepth;
    }

    /**
     * Sets the {@linkplain #eventHistoryDepth() event history depth} to the passed value.
     *
     * @param depth
     *         a positive number of recent events the double-dispatch guard scans
     */
    protected void setEventHistoryDepth(int depth) {
        checkArgument(depth > 0);
        this.eventHistoryDepth = depth;
    }

    /**
     * Tells whether this repository maintains the event history — the journal of the
     * events emitted by its entities.
     *
     * <p>Each concrete repository kind states its posture: the journaling is
     * unconditional for aggregates and opt-in for process managers.
     *
     * <p>While the journaling is disabled, the emitted events are not written to the
     * {@linkplain #eventStorage() journal}, and reading the
     * {@linkplain SignalDispatchingEntity#eventHistoryBackward(int) event history} of an
     * entity managed by this repository fails fast.
     */
    protected abstract boolean eventHistoryEnabled();

    /**
     * Creates the journal of the events emitted by the entities of this repository.
     *
     * <p>Mirrors {@link #createStorage()}: the default implementation uses the
     * {@linkplain #defaultStorageFactory() default storage factory} — the same one the default
     * {@code createStorage()} uses for the entity state. A repository that overrides
     * {@code createStorage()} to serve the entity state from a custom
     * {@link io.spine.server.storage.StorageFactory} or backend should override this method as
     * well, so the journal — feeding the double-dispatch guard and the
     * {@linkplain SignalDispatchingEntity#eventHistoryBackward(int) recent-history}
     * reads — is served by the same backend as the state, rather than silently falling
     * back to the default one.
     *
     * @return a new event journal
     */
    protected EntityEventStorage<I> createEventStorage() {
        return defaultStorageFactory().createEntityEventStorage(context().spec(), entityClass());
    }

    /**
     * Returns the journal of the events emitted by the entities of this repository,
     * creating it lazily via {@link #createEventStorage()} on the first access.
     *
     * <p>Unlike the opt-in {@linkplain #stateHistory() state history}, this accessor has
     * no fail-fast gate on {@link #eventHistoryEnabled()}: it exposes the journal — e.g.,
     * for the {@linkplain EntityEventStorage#truncate(com.google.protobuf.Timestamp)
     * time-based truncation} that bounds the journal growth as a periodic maintenance
     * operation. The entity history reads still fail fast, but through the loader
     * installed on the managed entities.
     *
     * <p>Synchronized for the same reason as {@code stateHistoryStorage()}: unlike the main
     * {@linkplain #storage() storage}, the journal is first touched when a signal is
     * dispatched, possibly by concurrent workers.
     *
     * @return the event journal of this repository
     */
    protected final synchronized EntityEventStorage<I> eventStorage() {
        if (eventStorage == null) {
            eventStorage = createEventStorage();
        }
        return eventStorage;
    }

    /**
     * {@inheritDoc}
     *
     * <p>When the {@linkplain #eventHistoryEnabled() journaling is enabled}, writes the
     * events the entity emitted to the {@linkplain #eventStorage() journal} before storing
     * its latest state, and marks them committed after. Under a batched delivery the
     * entity accumulates its events until {@code commitEvents()}, so the journal drops none.
     */
    @Override
    protected void doStore(E entity) {
        if (eventHistoryEnabled()) {
            var events = entity.uncommittedEventHistory().get();
            var journal = eventStorage();
            events.forEach(journal::write);
        }
        super.doStore(entity);
        entity.commitEvents();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Stores each entity individually via
     * {@link #store(io.spine.server.entity.Entity) store()}, so that the emitted events
     * are journaled — the bulk write of the parent class would bypass the journaling.
     */
    @Override
    public final void store(Collection<E> entities) {
        entities.forEach(this::store);
    }

    @OverridingMethodsMustInvokeSuper
    @Override
    public void close() {
        // Release every owned resource even if one close fails: the first failure is kept as
        // the primary exception and the later ones are attached to it as suppressed, so none of
        // them is lost. `super.close()` is called directly (not via the helper) so that the
        // `@OverridingMethodsMustInvokeSuper` check recognizes the super-call.
        RuntimeException failure = null;
        try {
            super.close();
        } catch (RuntimeException e) {
            failure = e;
        }
        failure = attemptClose(failure, this::closeEventStorage);
        if (failure != null) {
            throw failure;
        }
    }

    /**
     * Closes the event journal if it was created.
     *
     * <p>Synchronized to pair with {@link #eventStorage()}: the journal may have been
     * created by a dispatch worker, and the closing thread must observe that write.
     */
    private synchronized void closeEventStorage() {
        if (eventStorage != null && eventStorage.isOpen()) {
            eventStorage.close();
        }
    }
}
