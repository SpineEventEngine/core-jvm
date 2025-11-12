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

import com.google.protobuf.Message;
import io.spine.base.Identifier;
import io.spine.core.Command;
import io.spine.core.CommandContext;
import io.spine.server.BoundedContextBuilder;
import io.spine.server.command.AbstractAssignee;
import io.spine.server.command.Assign;
import io.spine.server.commandbus.CommandBus;
import io.spine.test.commandbus.command.CmdBusAddTask;
import io.spine.test.commandbus.command.CmdBusRemoveTask;
import io.spine.test.commandbus.command.FirstCmdBusCreateProject;
import io.spine.test.commandbus.command.SecondCmdBusStartProject;
import io.spine.test.commandbus.event.CmdBusProjectCreated;
import io.spine.test.commandbus.event.CmdBusProjectStarted;
import io.spine.test.commandbus.event.CmdBusTaskAdded;
import io.spine.test.reflect.InvalidProjectName;
import io.spine.test.reflect.ProjectId;

import java.util.ArrayList;
import java.util.List;

import static io.spine.grpc.StreamObservers.noOpObserver;
import static io.spine.util.Exceptions.newIllegalStateException;
import static java.util.Collections.unmodifiableList;

public class SingleTenantCommandBusTestEnv {

    /** Prevents instantiation of this utility class. */
    private SingleTenantCommandBusTestEnv() {
    }

    /**
     * A {@code CommandAssignee}, which throws a rejection upon a command.
     */
    public static class FaultyAssignee extends AbstractAssignee {

        private FaultyAssignee() {
            super();
        }

        public static FaultyAssignee initializedAssignee() {
            var assignee = new FaultyAssignee();
            assignee.registerWith(BoundedContextBuilder.assumingTests().build());
            return assignee;
        }

        private final InvalidProjectName rejection = InvalidProjectName
                .newBuilder()
                .setProjectId(ProjectId.newBuilder()
                                      .setId(Identifier.newUuid())
                                      .build())
                .build();

        @SuppressWarnings("unused")     // does nothing, but throws a rejection.
        @Assign
        CmdBusTaskAdded handle(CmdBusAddTask msg, CommandContext context)
                throws InvalidProjectName {
            throw rejection;
        }

        @SuppressWarnings("unused")     // does nothing, but throws a rejection.
        @Assign
        CmdBusTaskAdded handle(CmdBusRemoveTask msg, CommandContext context) {
            throw newIllegalStateException("Command handling failed with unexpected exception");
        }

        public InvalidProjectName getThrowable() {
            return rejection;
        }
    }

    /**
     * A {@code CommandAssignee} that posts a nested command.
     */
    public static class CommandPostingAssignee extends AbstractAssignee {

        private final CommandBus commandBus;
        private final List<Message> handledCommands = new ArrayList<>();
        private final Command commandToPost;

        public CommandPostingAssignee(CommandBus commandBus, Command commandToPost) {
            super();
            this.commandBus = commandBus;
            this.commandToPost = commandToPost;
        }

        @Assign
        CmdBusProjectCreated handle(FirstCmdBusCreateProject command) {
            commandBus.post(commandToPost, noOpObserver());
            handledCommands.add(command);
            return CmdBusProjectCreated
                    .newBuilder()
                    .setProjectId(command.getId())
                    .build();
        }

        @Assign
        CmdBusProjectStarted handle(SecondCmdBusStartProject command) {
            handledCommands.add(command);
            return CmdBusProjectStarted
                    .newBuilder()
                    .setProjectId(command.getId())
                    .build();
        }

        public List<Message> handledCommands() {
            return unmodifiableList(handledCommands);
        }
    }
}
