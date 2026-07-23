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

package io.spine.server.entity

import com.google.errorprone.annotations.OverridingMethodsMustInvokeSuper
import io.spine.base.EntityState
import io.spine.server.BoundedContext
import io.spine.server.commandbus.CommandDispatcherDelegate
import io.spine.server.dispatch.DispatchOutcome
import io.spine.server.dispatch.DispatchOutcomes.maybeSentToInbox
import io.spine.server.entity.storage.EntityEventStorage
import io.spine.server.route.CommandRouting
import io.spine.server.route.setup.CommandRoutingSetup
import io.spine.server.tenant.TenantAwareRunner
import io.spine.server.type.CommandEnvelope
import io.spine.util.Exceptions.newIllegalStateException
import java.util.Optional

/**
 * Abstract base for repositories that dispatch signals — both events and commands —
 * to the entities they manage.
 *
 * The entities of such a repository emit events. The repository appends the emitted
 * events to the per-entity [event journal][eventStorage], kept append-only
 * for traceability. The journal serves the
 * [recent-history reads][SignalDispatchingEntity.eventHistoryBackward]
 * and the opt-in double-dispatch guard. Whether the journal is written is determined by
 * [eventHistoryEnabled]: the journaling is unconditional for aggregates and
 * opt-in for process managers.
 *
 * Two per-repository settings tune the machinery:
 *  - [useDoubleDispatchGuard] — enables the history-backed double-dispatch
 *    guard, which rejects a signal already seen among the last
 *    [eventHistoryDepth] dispatches;
 *  - [eventHistoryDepth] — how many recent events the guard scans on each
 *    dispatch when enabled.
 *
 * @param I The type of the entity identifiers.
 * @param E The type of managed entities.
 * @param S The type of the entity state.
 */
@Suppress("TooManyFunctions") // This base for signal-dispatching repositories has many functions.
public abstract class SignalDispatchingRepository<I : Any,
                                                  E : SignalDispatchingEntity<I, S, *>,
                                                  S : EntityState<I>>
protected constructor() :
    EventDispatchingRepository<I, E, S>(),
    CommandDispatcherDelegate {

    /** The command routing schema used by this repository. */
    private val commandRouting: CommandRouting<I> by lazy {
        CommandRouting.newInstance(idClass())
    }

    /** Whether the opt-in double-dispatch guard is enabled for this repository. */
    private var guardEnabled = false

    /** The window (in journal events) the opt-in double-dispatch guard scans. */
    private var historyDepth = SignalDispatchingEntity.DEFAULT_HISTORY_DEPTH

    /**
     * The journal of the events emitted by the entities of this repository; created
     * lazily once the journal is first needed.
     */
    private var eventStorage: EntityEventStorage<I>? = null

    /**
     * Registers with the `CommandBus` for dispatching commands
     * (via a [delegating dispatcher][io.spine.server.commandbus.DelegatingCommandDispatcher]),
     * and sets up the routing of the commands.
     *
     * Before any of that, verifies that the configuration of this repository is
     * consistent — e.g., that the [double-dispatch guard][useDoubleDispatchGuard],
     * if enabled, has the [event journal][eventHistoryEnabled] it
     * scans. The check precedes the registration side effects, so a misconfigured
     * repository is never left subscribed to the buses of the context.
     *
     * @param context The `BoundedContext` of this repository.
     */
    @OverridingMethodsMustInvokeSuper
    override fun registerWith(context: BoundedContext) {
        checkGuardHasJournal()
        super.registerWith(context)
        doSetupCommandRouting()
    }

    private fun doSetupCommandRouting() {
        CommandRoutingSetup.apply(entityClass(), commandRouting)
        setupCommandRouting(commandRouting)
    }

    /**
     * Ensures the double-dispatch guard, if enabled, is backed by the event journal.
     *
     * The guard scans the journal of the entity — with the journaling disabled it
     * could see only the events of the current instance, silently missing the duplicates
     * arriving after a cache eviction or a restart. Such a configuration is an error.
     */
    private fun checkGuardHasJournal() {
        if (doubleDispatchGuardEnabled() && !eventHistoryEnabled()) {
            throw newIllegalStateException(
                "The double-dispatch guard of the repository `%s` requires the event" +
                        " journal it scans, but the event journaling is disabled for" +
                        " this repository. Enable the journaling along with the guard.",
                this
            )
        }
    }

    /**
     * A callback for derived classes to customize routing schema for commands.
     *
     * Default routing returns the value of the first field of a command message.
     *
     * @param routing The routing schema to customize.
     */
    protected abstract fun setupCommandRouting(routing: CommandRouting<I>)

    /**
     * Creates an entity with the given ID, installing the event history loader and,
     * when enabled, the double-dispatch guard on it.
     */
    @OverridingMethodsMustInvokeSuper
    override fun create(id: I): E {
        val entity = super.create(id)
        setUpEventHistoryReading(entity, id)
        return entity
    }

    /**
     * Reconstructs an entity from the given record, installing the event history loader
     * and, when enabled, the double-dispatch guard on it.
     */
    @OverridingMethodsMustInvokeSuper
    override fun toEntity(record: EntityRecord): E {
        val entity = super.toEntity(record)
        setUpEventHistoryReading(entity, entity.id)
        return entity
    }

    /**
     * Installs the event history loader and, when enabled, the double-dispatch guard
     * on a newly created or reconstructed entity.
     *
     * The loader is installed unconditionally: whether this repository journals the
     * emitted events gates only the behavior of the installed loader — while the
     * journaling is off, reading through it fails fast rather than serving an empty
     * history. An entity created outside a repository has no loader and reads empty.
     *
     * @param entity The entity to set up.
     * @param id The identifier of the entity.
     */
    private fun setUpEventHistoryReading(entity: E, id: I) {
        entity.setEventHistoryLoader(eventHistoryLoaderFor(id))
        if (doubleDispatchGuardEnabled()) {
            entity.enableDoubleDispatchGuard(eventHistoryDepth())
        }
    }

    /**
     * Creates a loader reading the journaled events of the entity with the given identifier.
     */
    private fun eventHistoryLoaderFor(id: I): EventHistoryLoader =
        EventHistoryLoader { depth, startingFrom ->
            if (!eventHistoryEnabled()) {
                throw newIllegalStateException(
                    "The repository `%s` does not journal the events emitted by its" +
                            " entities, so their event history is not readable." +
                            " Enable the journaling for this repository — see" +
                            " `eventHistoryEnabled()` — before reading the history.",
                    this
                )
            }
            eventStorage().historyBackward(entityId = id, batchSize = depth, startingFrom)
        }

    /**
     * Dispatches the command to a corresponding entity.
     *
     * If there is no stored entity with such an ID,
     * a new entity is created and stored after it handles the passed command.
     *
     * @param command The command to dispatch.
     */
    final override fun dispatchCommand(command: CommandEnvelope): DispatchOutcome {
        val target = route(command)
        target.ifPresent { id ->
            inbox().send(command)
                .toHandler(id)
        }
        return maybeSentToInbox(command, target)
    }

    private fun route(cmd: CommandEnvelope): Optional<I> {
        val target = route(commandRouting, cmd)
        target.ifPresent { id -> onCommandTargetSet(id, cmd) }
        return target
    }

    private fun onCommandTargetSet(id: I, cmd: CommandEnvelope) {
        val lifecycle = lifecycleOf(id)
        val commandId = cmd.id()
        TenantAwareRunner.with(cmd.tenantId())
            .run { lifecycle.onTargetAssignedToCommand(commandId) }
    }

    /**
     * Enables the opt-in, history-backed double-dispatch guard for the entities of
     * this repository.
     *
     * When enabled, each dispatch scans a bounded window of the entity's recent events —
     * including the events of the current delivery batch that have not reached the journal
     * yet — and rejects a signal already seen among them, however long ago it was dispatched.
     * This mechanism is distinct from the delivery layer's time-windowed deduplication. The
     * guard is **off by default** for performance: it adds a bounded history read to every
     * dispatch.
     *
     * The guard scans the [event journal][eventStorage] of the entity, so it
     * requires the [journaling to be enabled][eventHistoryEnabled]: registering
     * a repository that enables the guard while leaving the journaling off fails with a
     * configuration error.
     */
    protected open fun useDoubleDispatchGuard() {
        guardEnabled = true
    }

    /**
     * Tells whether the opt-in double-dispatch guard is enabled for this repository.
     *
     * @return `false` by default.
     */
    protected open fun doubleDispatchGuardEnabled(): Boolean = guardEnabled

    /**
     * Returns the number of the most recent events scanned by the opt-in
     * double-dispatch guard when it is enabled.
     *
     * @return A positive integer value; the default is
     *   [SignalDispatchingEntity.DEFAULT_HISTORY_DEPTH].
     */
    protected open fun eventHistoryDepth(): Int = historyDepth

    /**
     * Sets the [event history depth][eventHistoryDepth] to the passed value.
     *
     * @param depth A positive number of recent events the double-dispatch guard scans.
     */
    protected open fun setEventHistoryDepth(depth: Int) {
        require(depth > 0)
        historyDepth = depth
    }

    /**
     * Tells whether this repository maintains the event history, on behalf of
     * the endpoints of the entity packages.
     */
    internal fun isEventHistoryEnabled(): Boolean = eventHistoryEnabled()

    /**
     * Tells whether this repository maintains the event history — the journal of the
     * events emitted by its entities.
     *
     * Each concrete repository kind states its posture: the journaling is
     * unconditional for aggregates and opt-in for process managers.
     *
     * While the journaling is disabled, the emitted events are not written to the
     * [journal][eventStorage], and reading the
     * [event history][SignalDispatchingEntity.eventHistoryBackward] of an
     * entity managed by this repository fails fast.
     */
    protected abstract fun eventHistoryEnabled(): Boolean

    /**
     * Creates the journal of the events emitted by the entities of this repository.
     *
     * Mirrors [createStorage]: the default implementation uses the
     * [default storage factory][Repository.defaultStorageFactory] — the same one the default
     * `createStorage()` uses for the entity state. A repository that overrides
     * `createStorage()` to serve the entity state from a custom
     * [io.spine.server.storage.StorageFactory] or backend should override this method as
     * well, so the journal — feeding the double-dispatch guard and the
     * [recent-history][SignalDispatchingEntity.eventHistoryBackward]
     * reads — is served by the same backend as the state, rather than silently falling
     * back to the default one.
     *
     * @return A new event journal.
     */
    protected open fun createEventStorage(): EntityEventStorage<I> =
        defaultStorageFactory().createEntityEventStorage(context().spec(), entityClass())

    /**
     * Returns the journal of the events emitted by the entities of this repository,
     * creating it lazily via [createEventStorage] on the first access.
     *
     * Unlike the opt-in [state history][stateHistory], this accessor has
     * no fail-fast gate on [eventHistoryEnabled]: it exposes the journal — e.g.,
     * for the [time-based truncation][EntityEventStorage.truncate] that bounds
     * the journal growth as a periodic maintenance
     * operation. The entity history reads still fail fast, but through the loader
     * installed on the managed entities.
     *
     * Synchronized for the same reason as `stateHistoryStorage()`: unlike the main
     * [storage], the journal is first touched when a signal is
     * dispatched, possibly by concurrent workers.
     *
     * @return The event journal of this repository.
     */
    @Synchronized
    protected fun eventStorage(): EntityEventStorage<I> =
        eventStorage ?: createEventStorage().also { eventStorage = it }

    /**
     * When the [journaling is enabled][eventHistoryEnabled], writes the
     * events the entity emitted to the [journal][eventStorage] before storing
     * its latest state, and marks them committed after. Under a batched delivery the
     * entity accumulates its events until `commitEvents()`, so the journal drops none.
     */
    override fun doStore(entity: E) {
        if (eventHistoryEnabled()) {
            val events = entity.uncommittedEventHistory().get()
            val journal = eventStorage()
            events.forEach { journal.write(it) }
        }
        super.doStore(entity)
        entity.commitEvents()
    }

    /**
     * Stores each entity individually via the single-entity [store], so that the
     * emitted events are journaled — the bulk write of the parent class would bypass
     * the journaling.
     */
    final override fun store(entities: Collection<E>) {
        entities.forEach { store(it) }
    }

    @OverridingMethodsMustInvokeSuper
    @Suppress("TooGenericExceptionCaught") // Accumulates any failure of a close step.
    override fun close() {
        // Release every owned resource even if one close fails: the first failure is kept as
        // the primary exception and the later ones are attached to it as suppressed, so none of
        // them is lost. `super.close()` is called directly (not via the helper) so that the
        // `@OverridingMethodsMustInvokeSuper` check recognizes the super-call.
        var failure: RuntimeException? = null
        try {
            super.close()
        } catch (e: RuntimeException) {
            failure = e
        }
        failure = attemptClose(failure) { closeEventStorage() }
        if (failure != null) {
            throw failure
        }
    }

    /**
     * Closes the event journal if it was created.
     *
     * Synchronized to pair with [eventStorage]: the journal may have been
     * created by a dispatch worker, and the closing thread must observe that write.
     */
    @Synchronized
    private fun closeEventStorage() {
        eventStorage?.let {
            if (it.isOpen) {
                it.close()
            }
        }
    }
}
