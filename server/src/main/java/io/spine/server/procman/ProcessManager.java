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

package io.spine.server.procman;

import com.google.common.collect.ImmutableSet;
import io.spine.annotation.Internal;
import io.spine.annotation.VisibleForTesting;
import io.spine.base.EntityState;
import io.spine.base.ProcessManagerState;
import io.spine.logging.WithLogging;
import io.spine.server.BoundedContext;
import io.spine.server.command.Assign;
import io.spine.server.command.AssigneeEntity;
import io.spine.server.command.Commander;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.entity.HasLifecycleColumns;
import io.spine.server.entity.HasVersionColumn;
import io.spine.server.entity.Transaction;
import io.spine.server.entity.TransactionalEntity;
import io.spine.server.event.EventReactor;
import io.spine.server.event.React;
import io.spine.server.procman.model.ProcessManagerClass;
import io.spine.server.query.Querying;
import io.spine.server.query.QueryingClient;
import io.spine.server.type.CommandEnvelope;
import io.spine.server.type.EventClass;
import io.spine.server.type.EventEnvelope;
import io.spine.validate.ValidatingBuilder;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.server.Ignored.ignored;
import static io.spine.server.procman.model.ProcessManagerClass.asProcessManagerClass;
import static io.spine.util.Exceptions.newIllegalStateException;
import static java.lang.String.format;

/**
 * A central processing unit used to maintain the state of the business process and determine
 * the next processing step based on intermediate results.
 *
 * <p>A process manager reacts to domain events in a cross-aggregate, eventually consistent manner.
 *
 * <p>Event- and command-handling methods are invoked by the {@link ProcessManagerRepository}
 * that manages instances of a process manager class.
 *
 * <p>For more information on Process Managers, please see:
 * <ul>
 * <li>
 *   <a href="http://www.enterpriseintegrationpatterns.com/patterns/messaging/ProcessManager.html">
 *       Process Manager Pattern</a>
 * <li><a href="http://web.archive.org/web/20161205130022/http://kellabyte.com/2012/05/30/clarifying-the-saga-pattern/">
 *     Clarifying the Saga pattern</a> — the difference between Process Manager and Saga
 * <li><a href="https://dzone.com/articles/are-sagas-and-workflows-same-t">
 *     Are Sagas and Workflows the same...</a>
 * <li><a href="https://msdn.microsoft.com/en-us/library/jj591569.aspx">
 *     CQRS Journey Guide: A Saga on Sagas</a>
 * </ul>
 *
 * @param <I>
 *         the type of the process manager IDs
 * @param <S>
 *         the type of the process manager state
 * @param <B>
 *         the type of the builders for the process manager state
 */
public abstract class ProcessManager<I,
                                     S extends ProcessManagerState<I>,
                                     B extends ValidatingBuilder<S>>
        extends AssigneeEntity<I, S, B>
        implements EventReactor,
                   Commander,
                   HasVersionColumn<I, S>,
                   HasLifecycleColumns<I, S>,
                   Querying,
                   WithLogging {

    /**
     * The context in which this process manager is executed.
     *
     * <p>May be {@code null} if the process manager is not yet attached to a context.
     *
     * @see ProcessManagerRepository#configure(ProcessManager)
     */
    private @MonotonicNonNull BoundedContext context = null;

    /**
     * Creates a new instance.
     */
    protected ProcessManager() {
        super();
    }

    /**
     * Creates a new instance.
     *
     * @param id
     *         an ID for the new instance
     */
    protected ProcessManager(I id) {
        super(id);
    }

    /**
     * Assigns the passed bounded context to this process manager.
     */
    final void injectContext(BoundedContext context) {
        checkNotNull(context);
        if (this.context != null) {
            throw newIllegalStateException(
                    "The process manager `%s` is already placed into the Bounded Context `%s`.",
                    toString(),
                    this.context.name()
            );
        }
        this.context = context;
    }

    /**
     * Returns a new instance of {@link QueryingClient} for querying the state of entities
     * of the specified type.
     *
     * @param type the entity state type
     * @return a new instance of {@code QueryingClient}
     * @param <P> the type of the entity state
     */
    @Override
    public <P extends EntityState<?>> QueryingClient<P> select(Class<P> type) {
        return new QueryingClient<>(context, type, toString());
    }

    @Internal
    @Override
    public final ProcessManagerClass<?> modelClass() {
        return asProcessManagerClass(getClass());
    }

    @Override
    protected ProcessManagerClass<?> thisClass() {
        return (ProcessManagerClass<?>) super.thisClass();
    }

    @Override
    public ImmutableSet<EventClass> producedEvents() {
        return thisClass().outgoingEvents();
    }

    /**
     * {@inheritDoc}
     *
     * <p>In {@code ProcessManager}, this method must be called from an event reactor, a rejection
     * reactor, or a command assignee.
     *
     * @throws IllegalStateException
     *         if the method is called from outside an event/rejection reactor or a command assignee
     * @apiNote Marked {@link VisibleForTesting} to allow package-local use of this method in tests.
     *         It does not affect the visibility for inheritors which stays {@code protected}
     *         {@linkplain io.spine.server.entity.TransactionalEntity#builder() as originally
     *         defined in parents}.
     *         See <a href="https://youtrack.jetbrains.com/issue/IDEA-204081">IDEA issue</a>
     *         for reason behind the warning.
     */
    @Override
    @VisibleForTesting
    protected final B builder() {
        return super.builder();
    }

    /**
     * {@inheritDoc}
     *
     * <p>The method is overridden to be accessible from the {@code procman} package.
     */
    @Override
    protected Transaction<I, ? extends TransactionalEntity<I, S, B>, S, B> tx() {
        return super.tx();
    }

    /**
     * Dispatches the command to the handling method.
     *
     * @param command
     *         the envelope with the command to dispatch
     * @return the list of events generated as the result of handling the command,
     *         <em>if</em> the process manager <em>handles</em> the event.
     *         Empty list, if the process manager substitutes the command
     */
    @Override
    protected DispatchOutcome dispatchCommand(CommandEnvelope command) {
        ProcessManagerClass<?> thisClass = thisClass();
        var commandClass = command.messageClass();

        if (thisClass.handlesCommand(commandClass)) {
            var method = thisClass.receptorOf(command);
            var outcome = method.invoke(this, command);
            return outcome;
        }

        if (thisClass.substitutesCommand(commandClass)) {
            var method = thisClass.commanderOf(command);
            var outcome = method.invoke(this, command);
            return outcome;
        }

        // We could not normally get here since the dispatching table is a union of handled and
        // substituted commands.
        throw newIllegalStateException(
                "ProcessManager `%s` neither handled nor transformed the command " +
                        "(id: `%s` class: `%s`).",
                this, command.id(), commandClass
        );
    }

    /**
     * Dispatches an event the handling method.
     *
     * @param event
     *         the envelope with the event
     * @return one of the following:
     *         <ul>
     *             <li>a list of produced events if the process manager chooses to react
     *                 on the event;
     *             <li>an empty list if the process manager chooses <em>NOT</em> to react
     *                 on the event;
     *             <li>an empty list if the process manager generates one or more commands
     *                 in response to the event.
     *         </ul>
     */
    DispatchOutcome dispatchEvent(EventEnvelope event) {
        ProcessManagerClass<?> thisClass = thisClass();
        var eventClass = event.messageClass();
        var reactorMethod = thisClass.reactorOf(event);
        if (thisClass.reactsOnEvent(eventClass) && reactorMethod.isPresent()) {
            var outcome = reactorMethod.get().invoke(this, event);
            return outcome;
        }

        var commanderMethod = thisClass.commanderOf(event);
        if (thisClass.producesCommandsOn(eventClass) && commanderMethod.isPresent()) {
            var outcome = commanderMethod.get().invoke(this, event);
            return outcome;
        }
        logger().atDebug().log(() -> format(
                "The process manager `%s` filtered out and ignored the event `%s` with ID `%s`.",
                     thisClass, event.messageClass(), event.id().value()));
        return ignored(thisClass, event);
    }

    @Override
    protected String missingTxMessage() {
        return "`ProcessManager` modification is not available this way." +
                " Please modify the state from a command handling (`@"
                + Assign.class.getSimpleName() + "`)" +
                " or event reacting (`@" + React.class.getSimpleName() + "`) method.";
    }
}
