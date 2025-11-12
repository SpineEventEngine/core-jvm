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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.protobuf.Message;
import io.spine.core.CommandContext;
import io.spine.core.Subscribe;
import io.spine.server.command.AbstractAssignee;
import io.spine.server.command.Assign;
import io.spine.server.commandbus.CommandDispatcher;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.procman.ProcessManager;
import io.spine.server.procman.ProcessManagerRepository;
import io.spine.server.type.CommandClass;
import io.spine.server.type.CommandEnvelope;
import io.spine.test.commandbus.Project;
import io.spine.test.commandbus.ProjectId;
import io.spine.test.commandbus.Task;
import io.spine.test.commandbus.command.CmdBusAddTask;
import io.spine.test.commandbus.command.CmdBusCreateProject;
import io.spine.test.commandbus.command.CmdBusStartProject;
import io.spine.test.commandbus.event.CmdBusProjectCreated;
import io.spine.test.commandbus.event.CmdBusProjectStarted;
import io.spine.test.commandbus.event.CmdBusTaskAdded;

import static io.spine.server.dispatch.DispatchOutcomes.successfulOutcome;

public class CommandDispatcherRegistryTestEnv {

    private CommandDispatcherRegistryTestEnv() {
    }

    /*
     * Test command dispatchers.
     ***************************/

    public static class NoCommandsProcessManager
            extends ProcessManager<ProjectId, Project, Project.Builder> {

        /** The event message we store for inspecting in delivery tests. */
        private static final Multimap<ProjectId, Message> messagesDelivered = HashMultimap.create();

        public NoCommandsProcessManager(ProjectId id) {
            super(id);
        }

        private void keep(Message commandOrEventMsg) {
            messagesDelivered.put(state().getId(), commandOrEventMsg);
        }

        @Subscribe
        void on(CmdBusProjectCreated event) {
            // Keep the event message for further inspection in tests.
            keep(event);

            handleProjectCreated(event.getProjectId());
        }

        private void handleProjectCreated(ProjectId projectId) {
            var newState = state().toBuilder()
                    .setId(projectId)
                    .setStatus(Project.Status.CREATED)
                    .build();
            builder().mergeFrom(newState);
        }

        @Subscribe
        void on(CmdBusTaskAdded event) {
            keep(event);

            var task = event.getTask();
            handleTaskAdded(task);
        }

        private void handleTaskAdded(Task task) {
            var newState = state().toBuilder()
                                      .addTask(task)
                                      .build();
            builder().mergeFrom(newState);
        }

        @Subscribe
        void on(CmdBusProjectStarted event) {
            keep(event);

            handleProjectStarted();
        }

        private void handleProjectStarted() {
            var newState = state().toBuilder()
                    .setStatus(Project.Status.STARTED)
                    .build();
            builder().mergeFrom(newState);
        }
    }

    public static class EmptyDispatcher implements CommandDispatcher {

        @Override
        public ImmutableSet<CommandClass> messageClasses() {
            return ImmutableSet.of();
        }

        @Override
        public DispatchOutcome dispatch(CommandEnvelope envelope) {
            return successfulOutcome(envelope);
        }
    }

    public static class NoCommandsDispatcherRepo
            extends ProcessManagerRepository<ProjectId, NoCommandsProcessManager, Project> {

    }

    public static class AllCommandDispatcher implements CommandDispatcher {

        @Override
        public ImmutableSet<CommandClass> messageClasses() {
            return CommandClass.setOf(CmdBusCreateProject.class,
                                      CmdBusStartProject.class,
                                      CmdBusAddTask.class);
        }

        @Override
        public DispatchOutcome dispatch(CommandEnvelope envelope) {
            return successfulOutcome(envelope);
        }
    }

    public static class CreateProjectDispatcher implements CommandDispatcher {

        @Override
        public ImmutableSet<CommandClass> messageClasses() {
            return CommandClass.setOf(CmdBusCreateProject.class);
        }

        @Override
        public DispatchOutcome dispatch(CommandEnvelope envelope) {
            return successfulOutcome(envelope);
        }
    }

    public static class AddTaskDispatcher implements CommandDispatcher {

        @Override
        public ImmutableSet<CommandClass> messageClasses() {
            return CommandClass.setOf(CmdBusAddTask.class);
        }

        @Override
        public DispatchOutcome dispatch(CommandEnvelope envelope) {
            return successfulOutcome(envelope);
        }
    }

    /*
     * Test command assignees.
     ************************/

    public static class CreateProjectAssignee extends AbstractAssignee {

        @Assign
        CmdBusProjectCreated handle(CmdBusCreateProject command, CommandContext ctx) {
            return CmdBusProjectCreated.getDefaultInstance();
        }
    }

    public static class AllCommandAssignee extends AbstractAssignee {

        @Assign
        CmdBusProjectCreated handle(CmdBusCreateProject command, CommandContext ctx) {
            return CmdBusProjectCreated.getDefaultInstance();
        }

        @Assign
        CmdBusTaskAdded handle(CmdBusAddTask command) {
            return CmdBusTaskAdded.getDefaultInstance();
        }

        @Assign
        CmdBusProjectStarted handle(CmdBusStartProject command) {
            return CmdBusProjectStarted.getDefaultInstance();
        }
    }

    public static class EmptyCommandAssignee extends AbstractAssignee {

        @Override
        public ImmutableSet<CommandClass> messageClasses() {
            return ImmutableSet.of();
        }
    }
}
