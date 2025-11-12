/*
 * Copyright 2022, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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

package io.spine.server.command;

import com.google.common.collect.ImmutableSet;
import io.spine.core.Version;
import io.spine.server.BoundedContext;
import io.spine.server.command.model.AssigneeClass;
import io.spine.server.commandbus.CommandDispatcher;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.dispatch.DispatchOutcomeHandler;
import io.spine.server.event.EventBus;
import io.spine.server.type.CommandClass;
import io.spine.server.type.CommandEnvelope;
import io.spine.server.type.EventClass;

import static io.spine.server.command.model.AssigneeClass.asCommandAssigneeClass;

/**
 * The abstract base for non-aggregate classes that expose methods assigned to handle commands
 * and post their results to {@link EventBus}.
 *
 * <p>A command assignee is responsible for:
 * <ol>
 *     <li>Changing the state of the business model in response to a command. This is done
 *         by one of the command-handling methods to which the assignee dispatches the command.
 *     <li>Producing corresponding events.
 *     <li>Posting events to {@code EventBus}.
 * </ol>
 *
 * <p>Event messages are returned as values from command-handling methods.
 *
 * <p>A command assignee does not have its own state. So the state of the business
 * model it changes is external to it. Even though such behaviour may be needed in
 * some rare cases, using {@linkplain io.spine.server.aggregate.Aggregate aggregates}
 * is a preferred way of handling commands.
 *
 * <p>This class implements {@code CommandDispatcher} dispatching messages
 * to methods declared in the derived classes.
 *
 * @see io.spine.server.aggregate.Aggregate Aggregate
 * @see CommandDispatcher
 */
public abstract class AbstractAssignee
        extends AbstractCommandDispatcher
        implements Assignee {

    private final AssigneeClass<?> thisClass = asCommandAssigneeClass(getClass());

    /**
     * Dispatches the command to its receptor and posts resulting events to the {@link EventBus}.
     *
     * @param envelope
     *         the command to dispatch
     * @throws IllegalStateException
     *         if an exception occurred during command dispatching with this exception as the cause
     */
    @Override
    public DispatchOutcome dispatch(CommandEnvelope envelope) {
        var method = thisClass.receptorOf(envelope);
        var outcome = method.invoke(this, envelope);
        DispatchOutcomeHandler
                .from(outcome)
                .onEvents(this::postEvents)
                .onError(error -> onError(envelope, error))
                .onRejection(rejection -> onRejection(envelope, rejection))
                .handle();
        return outcome;
    }

    @Override
    public void registerWith(BoundedContext context) {
        super.registerWith(context);
        context.stand().registerTypeSupplier(this);
    }

    @Override
    public ImmutableSet<EventClass> producedEvents() {
        return thisClass.commandOutput();
    }

    @Override
    public ImmutableSet<CommandClass> messageClasses() {
        return thisClass.commands();
    }

    /**
     * Always returns {@linkplain Version#getDefaultInstance() empty} version.
     */
    @Override
    public Version version() {
        return Version.getDefaultInstance();
    }
}
