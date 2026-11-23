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

import io.spine.base.CommandMessage;
import io.spine.server.commandbus.given.CommandDispatcherRegistryTestEnv.AddTaskDispatcher;
import io.spine.server.commandbus.given.CommandDispatcherRegistryTestEnv.AllCommandDispatcher;
import io.spine.server.commandbus.given.CommandDispatcherRegistryTestEnv.AllCommandAssignee;
import io.spine.server.commandbus.given.CommandDispatcherRegistryTestEnv.CreateProjectDispatcher;
import io.spine.server.commandbus.given.CommandDispatcherRegistryTestEnv.CreateProjectAssignee;
import io.spine.server.commandbus.given.CommandDispatcherRegistryTestEnv.EmptyCommandAssignee;
import io.spine.server.commandbus.given.CommandDispatcherRegistryTestEnv.EmptyDispatcher;
import io.spine.server.commandbus.given.CommandDispatcherRegistryTestEnv.NoCommandsDispatcherRepo;
import io.spine.server.procman.ProcessManagerRepository;
import io.spine.server.type.CommandClass;
import io.spine.test.commandbus.command.CmdBusAddTask;
import io.spine.test.commandbus.command.CmdBusCreateProject;
import io.spine.test.commandbus.command.CmdBusStartProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("`CommandDispatcherRegistry` should")
class CommandDispatcherRegistryTest {

    /**
     * The object we test.
     */
    private CommandDispatcherRegistry registry;


    @BeforeEach
    void setUp() {
        registry = new CommandDispatcherRegistry();
    }

    @SafeVarargs
    private void assertSupported(Class<? extends CommandMessage>... cmdClasses) {
        var supportedClasses = registry.registeredMessageClasses();

        for (var cls : cmdClasses) {
            var cmdClass = CommandClass.from(cls);
            assertTrue(supportedClasses.contains(cmdClass));
        }
    }

    @SafeVarargs
    private void assertNotSupported(Class<? extends CommandMessage>... cmdClasses) {
        var supportedClasses = registry.registeredMessageClasses();

        for (var cls : cmdClasses) {
            var cmdClass = CommandClass.from(cls);
            assertFalse(supportedClasses.contains(cmdClass));
        }
    }

    @Nested
    @DisplayName("register")
    class Register {

        @Test
        @DisplayName("command dispatcher")
        void commandDispatcher() {
            registry.register(new AllCommandDispatcher());

            assertSupported(CmdBusCreateProject.class,
                            CmdBusAddTask.class,
                            CmdBusStartProject.class);
        }

        @Test
        @DisplayName("command assignee")
        void commandAssignee() {
            registry.register(new AllCommandAssignee());

            assertSupported(CmdBusCreateProject.class,
                            CmdBusAddTask.class,
                            CmdBusStartProject.class);
        }
    }

    @Nested
    @DisplayName("unregister")
    class Unregister {

        @Test
        @DisplayName("command dispatcher")
        void commandDispatcher() {
            CommandDispatcher dispatcher = new AllCommandDispatcher();

            registry.register(dispatcher);
            registry.unregister(dispatcher);

            assertNotSupported(CmdBusCreateProject.class,
                               CmdBusAddTask.class,
                               CmdBusStartProject.class);
        }

        @Test
        @DisplayName("command assignee")
        void commandAssignee() {
            var assignee = new AllCommandAssignee();

            registry.register(assignee);
            registry.unregister(assignee);

            assertNotSupported(CmdBusCreateProject.class,
                               CmdBusAddTask.class,
                               CmdBusStartProject.class);
        }

        @Test
        @DisplayName("all command dispatchers and assignees")
        void everything() {
            registry.register(new CreateProjectAssignee());
            registry.register(new AddTaskDispatcher());

            registry.unregisterAll();

            assertTrue(registry.registeredMessageClasses()
                               .isEmpty());
        }
    }

    @Nested
    @DisplayName("not accept empty")
    class NotAcceptEmpty {

        @Test
        @DisplayName("command dispatcher")
        void commandDispatcher() {
            assertThrows(IllegalArgumentException.class,
                         () -> registry.register(new EmptyDispatcher()));
        }

        @Test
        @DisplayName("command assignee")
        void commandAssignee() {
            assertThrows(IllegalArgumentException.class,
                         () -> registry.register(new EmptyCommandAssignee()));
        }

    }

    /**
     * Verifies if it's possible to pass a {@link ProcessManagerRepository}
     * that does not expose any command classes.
     */
    @Test
    @DisplayName("accept empty process manager repository dispatcher")
    void acceptEmptyProcessManagerRepository() {
        var pmRepo = new NoCommandsDispatcherRepo();
        registry.register(DelegatingCommandDispatcher.of(pmRepo));
    }

    @Test
    @DisplayName("state both dispatched and handled commands as supported")
    void supportDispatchedAndHandled() {
        registry.register(new CreateProjectAssignee());
        registry.register(new AddTaskDispatcher());

        assertSupported(CmdBusCreateProject.class, CmdBusAddTask.class);
    }

    @Test
    @DisplayName("state that no commands are supported when nothing is registered")
    void supportNothingWhenEmpty() {
        assertNotSupported(CmdBusCreateProject.class,
                           CmdBusAddTask.class,
                           CmdBusStartProject.class);
    }

    @Nested
    @DisplayName("not allow to override")
    class NotOverride {

        @Test
        @DisplayName("registered dispatcher by another dispatcher")
        void dispatcherByDispatcher() {
            registry.register(new AllCommandDispatcher());
            assertThrows(IllegalArgumentException.class,
                         () -> registry.register(new AllCommandDispatcher()));
        }

        @Test
        @DisplayName("registered assignee by another assignee")
        void assigneeByAssignee() {
            registry.register(new CreateProjectAssignee());
            assertThrows(IllegalArgumentException.class,
                         () -> registry.register(new CreateProjectAssignee()));
        }

        @Test
        @DisplayName("registered dispatcher by assignee")
        void dispatcherByAssignee() {
            registry.register(new CreateProjectDispatcher());
            assertThrows(IllegalArgumentException.class,
                         () -> registry.register(new CreateProjectAssignee()));
        }

        @Test
        @DisplayName("registered assignee by dispatcher")
        void assigneeByDispatcher() {
            registry.register(new CreateProjectAssignee());
            assertThrows(IllegalArgumentException.class,
                         () -> registry.register(new CreateProjectDispatcher()));
        }
    }
}
