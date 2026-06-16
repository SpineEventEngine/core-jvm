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
import io.spine.testing.server.model.ModelTests;
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
        ModelTests.dropAllModels();
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
