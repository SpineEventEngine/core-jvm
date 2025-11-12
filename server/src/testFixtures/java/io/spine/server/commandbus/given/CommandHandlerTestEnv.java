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

package io.spine.server.commandbus.given;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.spine.base.EventMessage;
import io.spine.core.Command;
import io.spine.core.CommandContext;
import io.spine.logging.WithLogging;
import io.spine.server.command.AbstractAssignee;
import io.spine.server.command.Assign;
import io.spine.server.command.CommandHistory;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.event.EventDispatcher;
import io.spine.server.tuple.Pair;
import io.spine.server.type.CommandEnvelope;
import io.spine.server.type.EventClass;
import io.spine.server.type.EventEnvelope;
import io.spine.test.commandbus.ProjectId;
import io.spine.test.commandbus.command.CmdBusAddTask;
import io.spine.test.commandbus.command.CmdBusCreateProject;
import io.spine.test.commandbus.command.CmdBusCreateTask;
import io.spine.test.commandbus.command.CmdBusStartProject;
import io.spine.test.commandbus.event.CmdBusProjectCreated;
import io.spine.test.commandbus.event.CmdBusProjectStarted;
import io.spine.test.commandbus.event.CmdBusTaskAdded;
import io.spine.test.commandbus.event.CmdBusTaskAssigned;
import io.spine.test.commandbus.event.CmdBusTaskStarted;

import java.util.List;
import java.util.Optional;

import static com.google.common.collect.Lists.newLinkedList;
import static io.spine.server.dispatch.DispatchOutcomes.successfulOutcome;

public class CommandHandlerTestEnv {

    /** Prevents instantiation of this utility class. */
    private CommandHandlerTestEnv() {
    }

    public static final class EventCatcher implements EventDispatcher {

        private final List<EventEnvelope> dispatched = newLinkedList();

        @Override
        public ImmutableSet<EventClass> messageClasses() {
            return EventClass.setOf(
                    CmdBusProjectStarted.class,
                    CmdBusTaskAssigned.class,
                    CmdBusTaskStarted.class
            );
        }

        @Override
        public ImmutableSet<EventClass> domesticEventClasses() {
            return eventClasses();
        }

        @Override
        public ImmutableSet<EventClass> externalEventClasses() {
            return EventClass.emptySet();
        }

        @Override
        public DispatchOutcome dispatch(EventEnvelope event) {
            dispatched.add(event);
            return successfulOutcome(event);
        }

        public List<EventEnvelope> dispatched() {
            return ImmutableList.copyOf(dispatched);
        }
    }

    public static class TestCommandAssignee extends AbstractAssignee implements WithLogging {

        private final ImmutableList<EventMessage> eventsOnStartProjectCmd =
                createEventsOnStartProjectCmd();

        private final CommandHistory commandsHandled = new CommandHistory();

        public void assertHandled(Command expected) {
            commandsHandled.assertHandled(expected);
        }

        @SuppressWarnings("CheckReturnValue")
        // Can ignore the returned ID of the command handler in these tests.
        public void handle(Command cmd) {
            var commandEnvelope = CommandEnvelope.of(cmd);
            dispatch(commandEnvelope);
        }

        public ImmutableList<EventMessage> getEventsOnStartProjectCmd() {
            return eventsOnStartProjectCmd;
        }

        @Assign
        CmdBusProjectCreated handle(CmdBusCreateProject msg, CommandContext context) {
            commandsHandled.add(msg, context);
            return CmdBusProjectCreated.newBuilder()
                    .setProjectId(msg.getProjectId())
                    .build();
        }

        @Assign
        CmdBusTaskAdded handle(CmdBusAddTask msg, CommandContext context) {
            commandsHandled.add(msg, context);
            return CmdBusTaskAdded.newBuilder()
                    .setProjectId(msg.getProjectId())
                    .build();
        }

        @Assign
        List<EventMessage> handle(CmdBusStartProject msg, CommandContext context) {
            commandsHandled.add(msg, context);
            return eventsOnStartProjectCmd;
        }

        @Assign
        Pair<CmdBusTaskAssigned, Optional<CmdBusTaskStarted>>
        handle(CmdBusCreateTask msg, CommandContext context) {
            commandsHandled.add(msg, context);
            return createEventsOnCreateTaskCmd(msg);
        }

        private ImmutableList<EventMessage> createEventsOnStartProjectCmd() {
            var id = ProjectId.newBuilder()
                    .setId(id())
                    .build();
            var startedEvent = CmdBusProjectStarted.newBuilder()
                    .setProjectId(id)
                    .build();
            var startedAgainEvent = CmdBusProjectStarted.newBuilder()
                    .setProjectId(id)
                    .build();
            return ImmutableList.of(startedEvent, startedAgainEvent);
        }

        private static Pair<CmdBusTaskAssigned, Optional<CmdBusTaskStarted>>
        createEventsOnCreateTaskCmd(CmdBusCreateTask msg) {
            var taskId = msg.getTaskId();
            var cmdTaskAssigned = CmdBusTaskAssigned.newBuilder()
                    .setTaskId(taskId)
                    .build();
            var cmdTaskStarted = msg.getStart()
                                 ? CmdBusTaskStarted.newBuilder()
                                         .setTaskId(taskId)
                                         .build()
                                 : null;
            return Pair.withNullable(cmdTaskAssigned, cmdTaskStarted);
        }
    }
}
