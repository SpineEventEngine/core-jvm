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

package io.spine.server.commandbus;

import com.google.common.testing.NullPointerTester;
import io.grpc.stub.StreamObserver;
import io.spine.core.Command;
import io.spine.grpc.StreamObservers;
import io.spine.server.command.AbstractAssignee;
import io.spine.server.commandbus.given.MultiTenantCommandBusTestEnv.AddTaskDispatcher;
import io.spine.server.type.CommandClass;
import io.spine.test.commandbus.command.CmdBusAddTask;
import io.spine.test.commandbus.command.CmdBusCreateProject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static io.spine.core.ErrorCase.TENANT_ERROR;
import static io.spine.core.ErrorCase.UNSUPPORTED_REQUEST;
import static io.spine.core.Status.StatusCase.ERROR_CASE;
import static io.spine.server.commandbus.Given.ACommand.addTask;
import static io.spine.server.commandbus.Given.ACommand.createProject;
import static io.spine.testing.DisplayNames.NOT_ACCEPT_NULLS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Multitenant `CommandBus` should")
@SuppressWarnings("DuplicateStringLiteralInspection") // Common test display names.
class MultiTenantCommandBusTest extends AbstractCommandBusTestSuite {

    MultiTenantCommandBusTest() {
        super(true);
    }

    @Test
    @DisplayName(NOT_ACCEPT_NULLS)
    void passNullToleranceCheck() {
        new NullPointerTester()
                .setDefault(Command.class, Command.getDefaultInstance())
                .setDefault(StreamObserver.class, StreamObservers.noOpObserver())
                .testAllPublicInstanceMethods(commandBus);
    }

    @Test
    @DisplayName("not accept default command on post")
    void notAcceptDefaultCommand() {
        assertThrows(IllegalArgumentException.class,
                     () -> commandBus.post(Command.getDefaultInstance(), observer));
    }

    @Test
    @DisplayName("verify tenant ID attribute if is multitenant")
    void requireTenantId() {
        commandBus.register(createProjectAssignee);
        var cmd = newCommandWithoutTenantId();

        commandBus.post(cmd, observer);

        checkCommandError(observer.firstResponse(), TENANT_ERROR, cmd);
    }

    @Test
    @DisplayName("state command not supported when there is neither assignee nor commander for it")
    void requireAssigneeOrCommander() {
        var command = addTask();
        commandBus.post(command, observer);

        checkCommandError(observer.firstResponse(), UNSUPPORTED_REQUEST, command);
    }

    @Nested
    @DisplayName("register")
    class Register {

        @Test
        @DisplayName("command dispatcher")
        void commandDispatcher() {
            commandBus.register(new AddTaskDispatcher());

            commandBus.post(addTask(), observer);

            assertTrue(observer.isCompleted());
        }

        @Test
        @DisplayName("command assignee")
        void commandAssignee() {
            var assignee = new CreateProjectAssignee();
            commandBus.register(assignee);
            assignee.registerWith(context);

            commandBus.post(createProject(), observer);

            assertTrue(observer.isCompleted());
        }
    }

    @Nested
    @DisplayName("unregister")
    class Unregister {

        @Test
        @DisplayName("command dispatcher")
        void commandDispatcher() {
            CommandDispatcher dispatcher = new AddTaskDispatcher();
            commandBus.register(dispatcher);
            commandBus.unregister(dispatcher);

            commandBus.post(addTask(), observer);

            assertEquals(ERROR_CASE, observer.firstResponse()
                                        .getStatus()
                                        .getStatusCase());
        }

        @Test
        @DisplayName("command assignee")
        void commandAssignee() {
            AbstractAssignee assignee = newCommandAssignee();

            commandBus.register(assignee);
            commandBus.unregister(assignee);

            commandBus.post(createProject(), observer);

            assertEquals(ERROR_CASE, observer.firstResponse()
                                        .getStatus()
                                        .getStatusCase());
        }

        CreateProjectAssignee newCommandAssignee() {
            var assignee = new CreateProjectAssignee();
            assignee.registerWith(context);
            return assignee;
        }
    }

    @Test
    @DisplayName("post command and return `OK` response")
    void postCommand() {
        commandBus.register(createProjectAssignee);

        var command = createProject();
        commandBus.post(command, observer);

        checkResult(command);
    }

    @Nested
    @DisplayName("when command is posted, invoke")
    class Invoke {

        @Test
        @DisplayName("command assignee")
        void commandAssignee() {
            commandBus.register(createProjectAssignee);

            commandBus.post(createProject(), observer);

            assertTrue(createProjectAssignee.wasHandlerInvoked());
        }

        @Test
        @DisplayName("command dispatcher")
        void commandDispatcher() {
            var dispatcher = new AddTaskDispatcher();
            commandBus.register(dispatcher);

            commandBus.post(addTask(), observer);

            assertTrue(dispatcher.wasDispatcherInvoked());
        }
    }

    @Test
    @DisplayName("expose supported classes")
    void exposeSupportedClasses() {
        commandBus.register(createProjectAssignee);
        commandBus.register(new AddTaskDispatcher());

        var cmdClasses = commandBus.registeredCommandClasses();

        assertTrue(cmdClasses.contains(CommandClass.from(CmdBusCreateProject.class)));
        assertTrue(cmdClasses.contains(CommandClass.from(CmdBusAddTask.class)));
    }
}
