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
import io.spine.annotation.Internal
import io.spine.annotation.VisibleForTesting
import io.spine.base.EntityState
import io.spine.base.ProcessManagerState
import io.spine.logging.WithLogging
import io.spine.server.BoundedContext
import io.spine.server.Ignored.ignored
import io.spine.server.command.Commander
import io.spine.server.dispatch.DispatchOutcome
import io.spine.server.entity.SignalDispatchingEntity
import io.spine.server.entity.Transaction
import io.spine.server.entity.TransactionalEntity
import io.spine.server.event.EventReactor
import io.spine.server.procman.model.ProcessManagerClass
import io.spine.server.procman.model.ProcessManagerClass.asProcessManagerClass
import io.spine.server.query.Querying
import io.spine.server.query.QueryingClient
import io.spine.server.type.CommandEnvelope
import io.spine.server.type.EventClass
import io.spine.server.type.EventEnvelope
import io.spine.util.Exceptions.newIllegalStateException
import io.spine.validation.ValidatingBuilder

/**
 * A central processing unit used to maintain the state of the business process and determine
 * the next processing step based on intermediate results.
 *
 * A process manager reacts to domain events in a cross-aggregate, eventually consistent manner.
 *
 * Event- and command-handling methods are invoked by the [ProcessManagerRepository]
 * that manages instances of a process manager class.
 *
 * When the repository [journals][ProcessManagerRepository.recordEventHistory]
 * the emitted events, a process manager may consult its recent event history via the
 * inherited [eventHistoryBackward] and [eventHistoryContains].
 *
 * For more information on Process Managers, please see:
 *  - [Process Manager Pattern](http://www.enterpriseintegrationpatterns.com/patterns/messaging/ProcessManager.html)
 *  - [Clarifying the Saga pattern](http://web.archive.org/web/20161205130022/http://kellabyte.com/2012/05/30/clarifying-the-saga-pattern/)
 *    — the difference between Process Manager and Saga
 *  - [Are Sagas and Workflows the same...](https://dzone.com/articles/are-sagas-and-workflows-same-t)
 *  - [CQRS Journey Guide: A Saga on Sagas](https://msdn.microsoft.com/en-us/library/jj591569.aspx)
 *
 * @param I The type of the process manager IDs.
 * @param S The type of the process manager state.
 * @param B The type of the builders for the process manager state.
 */
public abstract class ProcessManager<I : Any,
                                     S : ProcessManagerState<I>,
                                     B : ValidatingBuilder<S>> :
    SignalDispatchingEntity<I, S, B>,
    EventReactor,
    Commander,
    Querying,
    WithLogging {

    /**
     * The context in which this process manager is executed.
     *
     * May be `null` if the process manager is not yet attached to a context.
     */
    private var context: BoundedContext? = null

    /**
     * Creates a new instance.
     */
    protected constructor() : super()

    /**
     * Creates a new instance.
     *
     * @param id An ID for the new instance.
     */
    protected constructor(id: I) : super(id)

    /**
     * Assigns the passed bounded context to this process manager.
     */
    @JvmName("injectContext") // Keeps the JVM name for the Java repository caller.
    internal fun injectContext(context: BoundedContext) {
        val current = this.context
        if (current != null) {
            throw newIllegalStateException(
                "The process manager `%s` is already placed into the Bounded Context `%s`.",
                toString(),
                current.name()
            )
        }
        this.context = context
    }

    /**
     * Returns a new instance of [QueryingClient] for querying the state of entities
     * of the specified type.
     *
     * @param P The type of the entity state.
     * @param type The entity state type.
     * @return A new instance of `QueryingClient`.
     */
    override fun <P : EntityState<*>> select(type: Class<P>): QueryingClient<P> {
        val context = checkNotNull(context) {
            "The process manager `$this` is not attached to a Bounded Context yet."
        }
        return QueryingClient(context, type, toString())
    }

    @Internal
    final override fun modelClass(): ProcessManagerClass<*> = asProcessManagerClass(javaClass)

    override fun thisClass(): ProcessManagerClass<*> =
        super.thisClass() as ProcessManagerClass<*>

    override fun producedEvents(): ImmutableSet<EventClass> = thisClass().outgoingEvents()

    /**
     * Obtains the builder of the process manager state.
     *
     * In `ProcessManager`, this method must be called from an event reactor, a rejection
     * reactor, or a command assignee.
     *
     * Marked [VisibleForTesting] to allow package-local use of this method in tests.
     * It does not affect the visibility for inheritors that stay `protected`
     * as originally defined in parents.
     *
     * @throws IllegalStateException If the method is called from outside an event/rejection
     *   reactor or a command assignee.
     */
    @VisibleForTesting
    final override fun builder(): B = super.builder()

    /**
     * Obtains the active transaction of this process manager.
     *
     * The method is overridden to be accessible from the `procman` package.
     */
    override fun tx(): Transaction<I, out TransactionalEntity<I, S, B>, S, B> = super.tx()

    /**
     * Dispatches the command to the handling method.
     *
     * @param command The envelope with the command to dispatch.
     * @return The outcome with the events generated as the result of handling the command,
     *   *if* the process manager *handles* the command; the outcome of the substitution,
     *   if the process manager substitutes the command.
     */
    override fun dispatchCommand(command: CommandEnvelope): DispatchOutcome {
        val duplicate = detectDuplicate(command)
        if (duplicate != null) {
            return duplicate
        }
        val thisClass: ProcessManagerClass<*> = thisClass()
        val commandClass = command.messageClass()
        return when {
            thisClass.handlesCommand(commandClass) ->
                thisClass.receptorOf(command)
                    .invoke(this, command)

            thisClass.substitutesCommand(commandClass) ->
                thisClass.commanderOf(command)
                    .invoke(this, command)

            else ->
                // We could not normally get here since the dispatching table is a union
                // of handled and substituted commands.
                throw newIllegalStateException(
                    "ProcessManager `%s` neither handled nor transformed the command " +
                            "(id: `%s` class: `%s`).",
                    this,
                    command.id(),
                    commandClass
                )
        }
    }

    /**
     * Dispatches an event to the handling method.
     *
     * @param event The envelope with the event.
     * @return One of the following:
     *  - the outcome with produced events if the process manager chooses to react
     *    on the event;
     *  - an empty outcome if the process manager chooses *NOT* to react on the event;
     *  - an empty outcome if the process manager generates one or more commands
     *    in response to the event.
     */
    override fun dispatchEvent(event: EventEnvelope): DispatchOutcome {
        val duplicate = detectDuplicate(event)
        if (duplicate != null) {
            return duplicate
        }
        val thisClass: ProcessManagerClass<*> = thisClass()
        val eventClass = event.messageClass()
        val reactorMethod = thisClass.reactorOf(event)
        return when {
            thisClass.reactsOnEvent(eventClass) && reactorMethod.isPresent ->
                reactorMethod.get().invoke(this, event)

            else -> {
                val commanderMethod = thisClass.commanderOf(event)
                if (thisClass.producesCommandsOn(eventClass) && commanderMethod.isPresent) {
                    commanderMethod.get().invoke(this, event)
                } else {
                    logger().atDebug().log {
                        "The process manager `$thisClass` filtered out and ignored the" +
                                " event `${event.messageClass()}` with ID `${event.id().value()}`."
                    }
                    ignored(thisClass, event)
                }
            }
        }
    }

    override fun missingTxMessage(): String =
        "`ProcessManager` modification is not available this way." +
                " Please modify the state from a command handling (`@Assign`)" +
                " or event reacting (`@React`) method."
}
