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

package io.spine.server.procman

import com.google.common.collect.ImmutableSet
import com.google.errorprone.annotations.OverridingMethodsMustInvokeSuper
import io.spine.annotation.Internal
import io.spine.annotation.VisibleForTesting
import io.spine.base.ProcessManagerState
import io.spine.core.Command
import io.spine.grpc.StreamObservers.noOpObserver
import io.spine.option.EntityOption.Kind.PROCESS_MANAGER
import io.spine.server.delivery.Inbox
import io.spine.server.delivery.InboxLabel
import io.spine.server.dispatch.DispatchOutcome
import io.spine.server.dispatch.DispatchOutcomes.sentToInbox
import io.spine.server.entity.EntityLifecycleMonitor
import io.spine.server.entity.EntityRecord
import io.spine.server.entity.EventProducingRepository
import io.spine.server.entity.SignalDispatchingRepository
import io.spine.server.entity.TransactionListener
import io.spine.server.event.EventBus
import io.spine.server.procman.model.ProcessManagerClass
import io.spine.server.procman.model.ProcessManagerClass.asProcessManagerClass
import io.spine.server.route.CommandRouting
import io.spine.server.route.EventRoute
import io.spine.server.route.EventRouting
import io.spine.server.type.CommandClass
import io.spine.server.type.EventClass
import io.spine.server.type.EventEnvelope
import io.spine.server.type.SignalEnvelope
import io.spine.util.Exceptions.newIllegalStateException
import io.spine.validation.ValidatingBuilder

/**
 * The abstract base for Process Managers repositories.
 *
 * Three per-repository settings tune the optional machinery:
 *  - [recordEventHistory] — journals the events emitted by the process
 *    managers into the per-entity [event journal][eventStorage]. It is
 *    **off by default**: the emitted events are durable in the
 *    [EventStore][io.spine.server.event.EventStore] regardless, and the
 *    per-entity journal is a deliberate diagnostics and history index. While the
 *    journaling is off, reading the event history of a managed process manager
 *    fails fast.
 *  - [useDoubleDispatchGuard] — enables the history-backed double-dispatch
 *    guard, which rejects a signal already seen among the last
 *    [eventHistoryDepth] dispatches. It is **off by default**, and
 *    requires the event journal it scans — enable it together with
 *    [recordEventHistory].
 *  - [recordStateHistory] — records the state history of the process
 *    managers on each dispatch. This is an opt-in feature shared by all entity
 *    repositories (see [io.spine.server.entity.AbstractEntityRepository]).
 *    It is **off by default**.
 *
 * @param I The type of IDs of process managers.
 * @param P The type of process managers.
 * @param S The type of process manager state messages.
 *
 * @see ProcessManager
 */
@Suppress("TooManyFunctions") // This base for process manager repositories has many functions.
public abstract class ProcessManagerRepository<I : Any,
                                               P : ProcessManager<I, S, *>,
                                               S : ProcessManagerState<I>>
protected constructor() :
    SignalDispatchingRepository<I, P, S>(),
    EventProducingRepository {

    /** Whether this repository journals the events emitted by its process managers. */
    private var eventHistoryEnabled = false

    /**
     * Enables journaling the events emitted by the process managers of this repository.
     *
     * Unlike aggregates, whose journal is written unconditionally, process managers
     * journal their events on an opt-in basis: the emitted events are durable in the
     * [EventStore][io.spine.server.event.EventStore] once posted, and the per-entity
     * journal is a deliberate diagnostics and history index. When the journaling is
     * enabled, the repository appends the emitted events to the
     * [journal][eventStorage] on each successful dispatch, and the process
     * managers may consult their recent event history.
     *
     * The journal is also required by the
     * [double-dispatch guard][useDoubleDispatchGuard].
     */
    protected fun recordEventHistory() {
        eventHistoryEnabled = true
    }

    /**
     * Tells whether this repository maintains the event history.
     *
     * For process managers, the journaling is opt-in and off by default — see
     * [recordEventHistory].
     */
    final override fun eventHistoryEnabled(): Boolean = eventHistoryEnabled

    /**
     * Obtains class information of process managers managed by this repository.
     */
    @Suppress("UNCHECKED_CAST")
    private fun processManagerClass(): ProcessManagerClass<P> =
        entityModelClass() as ProcessManagerClass<P>

    @Internal
    final override fun toModelClass(cls: Class<P>): ProcessManagerClass<P> =
        asProcessManagerClass(cls)

    final override fun eventBus(): EventBus = context().eventBus()

    /**
     * Sets up the inbox with the endpoints reacting upon events and handling commands.
     */
    final override fun setupInbox(builder: Inbox.Builder<I>) {
        builder.addEventEndpoint(InboxLabel.REACT_UPON_EVENT) { e -> PmEventEndpoint.of(this, e) }
            .addCommandEndpoint(InboxLabel.HANDLE_COMMAND) { c -> PmCommandEndpoint.of(this, c) }
    }

    /**
     * Replaces default routing with the one that takes the target ID from the first field
     * of an event message.
     *
     * @param routing The routing to customize.
     */
    @OverridingMethodsMustInvokeSuper
    override fun setupEventRouting(routing: EventRouting<I>) {
        super.setupEventRouting(routing)
        routing.replaceDefault(EventRoute.byFirstMessageField(idClass()))
    }

    /**
     * Does nothing by default: a Process Manager may handle only events,
     * in which case no command routing is needed.
     *
     * @param routing The routing schema to customize.
     */
    override fun setupCommandRouting(routing: CommandRouting<I>) {
        // Do nothing.
    }

    /**
     * Ensures there is at least one receptor declared by the class of the managed
     * process manager:
     *  - a command handler;
     *  - a domestic or external event reactor;
     *  - a domestic or external rejection reactor;
     *  - a commander.
     */
    final override fun checkDispatchesMessages() {
        if (!dispatchesCommands() && !dispatchesEvents()) {
            throw newIllegalStateException(
                "Process managers of the repository `%s` have no command handlers, " +
                        "and do not react to any events.",
                this
            )
        }
    }

    /**
     * Obtains a set of event classes to which process managers of this repository react.
     *
     * @return A set of event classes or an empty set if process managers do not react
     *   to events.
     */
    final override fun messageClasses(): ImmutableSet<EventClass> =
        processManagerClass().events()

    /**
     * Obtains classes of domestic events to which the process managers managed by this
     * repository react.
     *
     * @return A set of event classes or an empty set if process managers do not react
     *   to domestic events.
     */
    final override fun domesticEventClasses(): ImmutableSet<EventClass> =
        processManagerClass().domesticEvents()

    /**
     * Obtains classes of external events to which the process managers managed by this
     * repository react.
     *
     * @return A set of event classes or an empty set if process managers do not react
     *   to external events.
     */
    final override fun externalEventClasses(): ImmutableSet<EventClass> =
        processManagerClass().externalEvents()

    /**
     * Obtains a set of classes of commands handled by process managers of this repository.
     *
     * @return A set of command classes or an empty set if process managers do not
     *   handle commands.
     */
    final override fun commandClasses(): ImmutableSet<CommandClass> =
        processManagerClass().commands()

    override fun outgoingEvents(): ImmutableSet<EventClass> =
        processManagerClass().outgoingEvents()

    @Internal
    final override fun onRoutingFailed(envelope: SignalEnvelope<*, *, *>, cause: Throwable) {
        super.onRoutingFailed(envelope, cause)
        postIfCommandRejected(envelope, cause)
    }

    override fun canDispatch(envelope: EventEnvelope): Boolean =
        processManagerClass().reactorOf(envelope).isPresent ||
                processManagerClass().commanderOf(envelope).isPresent

    /**
     * Sends the given event to the `Inbox`es of respective entities.
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
     * Begins a transaction for modifying the given process manager.
     */
    @Suppress("UNCHECKED_CAST")
    @VisibleForTesting
    protected open fun beginTransactionFor(manager: P): PmTransaction<*, *, *> {
        val tx = PmTransaction(manager as ProcessManager<I, S, ValidatingBuilder<S>>)
        val listener: TransactionListener<I> =
            EntityLifecycleMonitor.newInstance(this, manager.id)
        tx.setListener(listener)
        return tx
    }

    /**
     * Begins a transaction for the given process manager on behalf of the endpoint.
     *
     * Delegates to [beginTransactionFor], honoring its overrides.
     */
    internal fun openTransactionFor(manager: P): PmTransaction<*, *, *> =
        beginTransactionFor(manager)

    /**
     * Posts the passed commands to the `CommandBus`.
     */
    internal fun postCommands(commands: Collection<Command>) {
        val bus = context().commandBus()
        bus.post(commands, noOpObserver())
    }

    /**
     * Reconstructs a process manager from the given record and
     * [configures][configure] it.
     */
    final override fun toEntity(record: EntityRecord): P {
        val result = super.toEntity(record)
        configure(result)
        return result
    }

    @OverridingMethodsMustInvokeSuper
    override fun create(id: I): P {
        val procman = super.create(id)
        lifecycleOf(id).onEntityCreated(PROCESS_MANAGER)
        configure(procman)
        return procman
    }

    /**
     * A callback method for configuring a recently created `ProcessManager` instance
     * before it is returned by the repository as the result of creating a new process
     * manager instance or finding an existing one.
     *
     * The default implementation attaches the process manager to the bounded context
     * so that it can perform querying. Overriding repositories may use this method for
     * injecting other dependencies that process managers need to have.
     *
     * @param processManager The process manager to configure.
     */
    @OverridingMethodsMustInvokeSuper
    protected open fun configure(processManager: P) {
        processManager.injectContext(context())
    }

    /**
     * Finds a process manager by the passed ID or creates a new one, exposing
     * the inherited method to this package.
     */
    final override fun findOrCreate(id: I): P = super.findOrCreate(id)

    /**
     * Finds or creates a process manager on behalf of the endpoint.
     */
    internal fun findOrCreateProcess(id: I): P = findOrCreate(id)
}
