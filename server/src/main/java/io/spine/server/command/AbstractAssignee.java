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

package io.spine.server.command;

import com.google.common.collect.ImmutableSet;
import io.spine.core.Version;
import io.spine.server.command.model.AssigneeClass;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.dispatch.DispatchOutcomeHandler;
import io.spine.server.type.CommandClass;
import io.spine.server.type.CommandEnvelope;

import static io.spine.server.command.model.AssigneeClass.asCommandAssigneeClass;

/**
 * The abstract base for non-aggregate classes that expose methods assigned to handle commands
 * and post their results to {@link io.spine.server.event.EventBus EventBus}.
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
 * @see io.spine.server.commandbus.CommandDispatcher CommandDispatcher
 */
public abstract class AbstractAssignee
        extends AbstractCommandDispatcher
        implements Assignee {

    private final AssigneeClass<?> thisClass = asCommandAssigneeClass(getClass());

    /**
     * Dispatches the command to its receptor and posts resulting events to
     * the {@link io.spine.server.event.EventBus EventBus}.
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
    public ImmutableSet<CommandClass> messageClasses() {
        return thisClass.commands();
    }

    /**
     * Always returns an {@linkplain Version#getDefaultInstance() empty} version.
     */
    @Override
    public Version version() {
        return Version.getDefaultInstance();
    }
}
