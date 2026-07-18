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

package io.spine.server.procman;

import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.OverridingMethodsMustInvokeSuper;
import io.spine.annotation.Internal;
import io.spine.annotation.VisibleForTesting;
import io.spine.base.ProcessManagerState;
import io.spine.core.Command;
import io.spine.server.commandbus.CommandBus;
import io.spine.server.delivery.Inbox;
import io.spine.server.delivery.InboxLabel;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.entity.EntityLifecycleMonitor;
import io.spine.server.entity.EntityRecord;
import io.spine.server.entity.EventProducingRepository;
import io.spine.server.entity.SignalDispatchingRepository;
import io.spine.server.entity.TransactionListener;
import io.spine.server.event.EventBus;
import io.spine.server.procman.model.ProcessManagerClass;
import io.spine.server.route.CommandRouting;
import io.spine.server.route.EventRoute;
import io.spine.server.route.EventRouting;
import io.spine.server.type.CommandClass;
import io.spine.server.type.EventClass;
import io.spine.server.type.EventEnvelope;
import io.spine.server.type.SignalEnvelope;

import java.util.Collection;
import java.util.Set;

import static io.spine.grpc.StreamObservers.noOpObserver;
import static io.spine.option.EntityOption.Kind.PROCESS_MANAGER;
import static io.spine.server.dispatch.DispatchOutcomes.sentToInbox;
import static io.spine.server.procman.model.ProcessManagerClass.asProcessManagerClass;
import static io.spine.util.Exceptions.newIllegalStateException;

/**
 * The abstract base for Process Managers repositories.
 *
 * @param <I>
 *         the type of IDs of process managers
 * @param <P>
 *         the type of process managers
 * @param <S>
 *         the type of process manager state messages
 * @see ProcessManager
 */
public abstract class ProcessManagerRepository<I,
                                               P extends ProcessManager<I, S, ?>,
                                               S extends ProcessManagerState<I>>
        extends SignalDispatchingRepository<I, P, S>
        implements EventProducingRepository {

    protected ProcessManagerRepository() {
        super();
    }

    /**
     * Obtains class information of process managers managed by this repository.
     */
    private ProcessManagerClass<P> processManagerClass() {
        return (ProcessManagerClass<P>) entityModelClass();
    }

    @Internal
    @Override
    protected final ProcessManagerClass<P> toModelClass(Class<P> cls) {
        return asProcessManagerClass(cls);
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
        builder.addEventEndpoint(InboxLabel.REACT_UPON_EVENT,
                                 e -> PmEventEndpoint.of(this, e))
               .addCommandEndpoint(InboxLabel.HANDLE_COMMAND,
                                   c -> PmCommandEndpoint.of(this, c));
    }

    /**
     * Replaces default routing with the one that takes the target ID from the first field
     * of an event message.
     *
     * @param routing
     *         the routing to customize
     */
    @Override
    @OverridingMethodsMustInvokeSuper
    protected void setupEventRouting(EventRouting<I> routing) {
        super.setupEventRouting(routing);
        routing.replaceDefault(EventRoute.byFirstMessageField(idClass()));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Does nothing by default: a Process Manager may handle only events,
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
     * <p>Ensures there is at least one receptor declared by the class of the managed
     * process manager:
     *
     * <ul>
     *     <li>a command handler;
     *     <li>a domestic or external event reactor;
     *     <li>a domestic or external rejection reactor;
     *     <li>a commander.
     * </ul>
     */
    @Override
    protected final void checkDispatchesMessages() {
        if (!dispatchesCommands() && !dispatchesEvents()) {
            throw newIllegalStateException(
                    "Process managers of the repository `%s` have no command handlers, " +
                            "and do not react to any events.", this);
        }
    }

    /**
     * Obtains a set of event classes to which process managers of this repository react.
     *
     * @return a set of event classes or an empty set if process managers do not react to events
     */
    @Override
    public final ImmutableSet<EventClass> messageClasses() {
        return processManagerClass().events();
    }

    /**
     * Obtains classes of domestic events to which the process managers managed by this repository
     * react.
     *
     * @return a set of event classes or an empty set if process managers do not react to
     *         domestic events
     */
    @Override
    public final ImmutableSet<EventClass> domesticEventClasses() {
        return processManagerClass().domesticEvents();
    }

    /**
     * Obtains classes of external events to which the process managers managed by this repository
     * react.
     *
     * @return a set of event classes or an empty set if process managers do not react to
     *         external events
     */
    @Override
    public final ImmutableSet<EventClass> externalEventClasses() {
        return processManagerClass().externalEvents();
    }

    /**
     * Obtains a set of classes of commands handled by process managers of this repository.
     *
     * @return a set of command classes or an empty set if process managers do not handle commands
     */
    @Override
    public final ImmutableSet<CommandClass> commandClasses() {
        return processManagerClass().commands();
    }

    @Override
    public ImmutableSet<EventClass> outgoingEvents() {
        return processManagerClass().outgoingEvents();
    }

    @Internal
    @Override
    protected final void onRoutingFailed(SignalEnvelope<?, ?, ?> envelope, Throwable cause) {
        super.onRoutingFailed(envelope, cause);
        postIfCommandRejected(envelope, cause);
    }

    @Override
    public boolean canDispatch(EventEnvelope envelope) {
        return processManagerClass().reactorOf(envelope).isPresent()
                || processManagerClass().commanderOf(envelope).isPresent();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Sends the given event to the {@code Inbox}es of respective entities.
     */
    @Override
    protected final DispatchOutcome dispatchTo(Set<I> ids, EventEnvelope event) {
        ids.forEach(id -> inbox().send(event)
                                 .toReactor(id));
        return sentToInbox(event, ids);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})   // to avoid massive generic-related issues.
    @VisibleForTesting
    protected PmTransaction<?, ?, ?> beginTransactionFor(P manager) {
        @SuppressWarnings("RedundantExplicitVariableType")  /* Because of the wildcard generic. */
        PmTransaction<I, S, ?> tx = new PmTransaction<>((ProcessManager<I, S, ?>) manager);
        TransactionListener listener =
                EntityLifecycleMonitor.newInstance(this, manager.id());
        tx.setListener(listener);
        return tx;
    }

    /**
     * Posts the passed commands to {@link CommandBus}.
     */
    final void postCommands(Collection<Command> commands) {
        var bus = context().commandBus();
        bus.post(commands, noOpObserver());
    }

    /**
     * Creates and {@linkplain #configure(ProcessManager) configures} an instance of
     * the process manager by the passed record.
     */
    @Override
    protected final P toEntity(EntityRecord record) {
        var result = super.toEntity(record);
        configure(result);
        return result;
    }

    @OverridingMethodsMustInvokeSuper
    @Override
    public P create(I id) {
        var procman = super.create(id);
        lifecycleOf(id).onEntityCreated(PROCESS_MANAGER);
        configure(procman);
        return procman;
    }

    /**
     * A callback method for configuring a recently created {@code ProcessManager} instance
     * before it is returned by the repository as the result of creating a new process manager
     * instance or finding an existing one.
     *
     * <p>The default implementation attaches the process manager to the bounded context
     * so that it can perform querying. Overriding repositories may use this method for
     * injecting other dependencies that process managers need to have.
     *
     * @param processManager
     *         the process manager to configure
     */
    @OverridingMethodsMustInvokeSuper
    protected void configure(P processManager) {
        processManager.injectContext(context());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Overrides to expose the method to the package.
     */
    @Override
    protected final P findOrCreate(I id) {
        return super.findOrCreate(id);
    }
}
