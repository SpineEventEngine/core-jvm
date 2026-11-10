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
    }
}
