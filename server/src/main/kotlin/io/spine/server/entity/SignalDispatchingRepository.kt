/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package io.spine.server.entity

import com.google.errorprone.annotations.OverridingMethodsMustInvokeSuper
import io.spine.base.EntityState
import io.spine.base.RejectionThrowable
import io.spine.core.Event
import io.spine.server.BoundedContext
import io.spine.server.commandbus.CommandDispatcherDelegate
import io.spine.server.dispatch.DispatchOutcome
import io.spine.server.dispatch.DispatchOutcomes.maybeSentToInbox
import io.spine.server.entity.storage.EntityEventStorage
import io.spine.server.event.reject
import io.spine.server.route.CommandRouting
import io.spine.server.route.setup.CommandRoutingSetup
import io.spine.server.tenant.TenantAwareRunner
import io.spine.server.type.CommandEnvelope
import io.spine.server.type.SignalEnvelope
import io.spine.util.Exceptions.newIllegalStateException
import io.spine.util.runKeepingFailure
import io.spine.util.throwIfAny
import java.util.Optional

/**
 * Abstract base for repositories that dispatch signals — both events and commands —
 * to the entities they manage.
 *
 * The entities of such a repository emit events. The repository appends the emitted
 * events to the per-entity [event journal][eventStorage], kept append-only
 * for traceability. The journal serves the
 * [recent-history reads][SignalDispatchingEntity.eventHistoryBackward].
 * Whether the journal is written is determined by [eventHistoryEnabled]: the journaling
 * is unconditional for aggregates and opt-in for process managers.
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

    /**
     * The journal of the events emitted by the entities of this repository; created
     * lazily once the journal is first needed.
     */
    private var eventStorage: EntityEventStorage<I>? = null

    /**
     * Registers this repository with the given context, as the parent class does, and sets up
     * the routing of the commands it dispatches.
     *
     * @param context The `BoundedContext` of this repository.
     */
    @OverridingMethodsMustInvokeSuper
    override fun registerWith(context: BoundedContext) {
        super.registerWith(context)
        doSetupCommandRouting()
    }

    private fun doSetupCommandRouting() {
        CommandRoutingSetup.apply(entityClass(), commandRouting)
        setupCommandRouting(commandRouting)
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
     * Creates an entity with the given ID, installing the event history loader on it.
     */
    @OverridingMethodsMustInvokeSuper
    override fun create(id: I): E {
        val entity = super.create(id)
        setUpEventHistoryReading(entity, id)
        return entity
    }

    /**
     * Reconstructs an entity from the given record, installing the event history loader on it.
     */
    @OverridingMethodsMustInvokeSuper
    override fun toEntity(record: EntityRecord): E {
        val entity = super.toEntity(record)
        setUpEventHistoryReading(entity, entity.id)
        return entity
    }

    /**
     * Installs the event history loader on a newly created or reconstructed entity.
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
     * Filters the passed events using the [event filter][eventFilter] of this repository,
     * and posts the result to the `EventBus` of its context.
     */
    internal fun postEvents(events: Collection<Event>) {
        val filtered = eventFilter().filter(events)
        context().eventBus().post(filtered)
    }

    /**
     * If the passed signal is a command and the thrown cause is a rejection,
     * posts the rejection to the `EventBus` of the context of this repository.
     *
     * Otherwise, does nothing.
     */
    internal fun postIfCommandRejected(signal: SignalEnvelope<*, *, *>, cause: Throwable) {
        if (signal is CommandEnvelope && cause is RejectionThrowable) {
            val rejection = reject(signal.outerObject(), cause)
            postEvents(rejection.toSet())
        }
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
     * [storage factory][Repository.storageFactory] of the context — the same one the default
     * `createStorage()` uses for the entity state. A repository that overrides
     * `createStorage()` to serve the entity state from a custom
     * [io.spine.server.storage.StorageFactory] or backend should override this method as
     * well, so the journal — feeding the
     * [recent-history reads][SignalDispatchingEntity.eventHistoryBackward] — is served
     * by the same backend as the state, rather than silently falling back to the default one.
     *
     * @return A new event journal.
     */
    protected open fun createEventStorage(): EntityEventStorage<I> =
        storageFactory().createEntityEventStorage(context().spec(), entityClass())

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
            val events = entity.uncommittedEventHistory().list()
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
        var failure: Throwable? = null
        try {
            super.close()
        } catch (e: Throwable) {
            failure = e
        }
        failure = runKeepingFailure(failure) { closeEventStorage() }
        throwIfAny(failure)
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
