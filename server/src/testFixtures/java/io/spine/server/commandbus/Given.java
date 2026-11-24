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

import com.google.protobuf.Timestamp;
import io.spine.core.Command;
import io.spine.core.TenantId;
import io.spine.core.UserId;
import io.spine.test.commandbus.ProjectId;
import io.spine.test.commandbus.Task;
import io.spine.test.commandbus.TaskId;
import io.spine.test.commandbus.command.CmdBusAddTask;
import io.spine.test.commandbus.command.CmdBusCreateProject;
import io.spine.test.commandbus.command.CmdBusCreateTask;
import io.spine.test.commandbus.command.CmdBusRemoveTask;
import io.spine.test.commandbus.command.CmdBusStartProject;
import io.spine.testing.client.TestActorRequestFactory;
import io.spine.testing.core.given.GivenUserId;

import static io.spine.base.Identifier.newUuid;
import static io.spine.base.Time.currentTime;
import static io.spine.testing.TestValues.random;

public class Given {

    private Given() {
        // Prevent construction from outside.
    }

    private static ProjectId newProjectId() {
        var uuid = newUuid();
        return ProjectId.newBuilder()
                .setId(uuid)
                .build();
    }

    public static class ACommand {

        private static final UserId USER_ID = GivenUserId.newUuid();
        private static final ProjectId PROJECT_ID = newProjectId();
        private static final TaskId TASK_ID = newTaskId();

        private ACommand() {
            // Prevent construction from outside.
        }

        /**
         * Creates a new {@code ACommand} with the given command message,
         * serId and timestamp using default {@code ACommand} instance.
         */
        private static Command create(io.spine.base.CommandMessage command, UserId userId,
                                      Timestamp when) {
            var generatedTenantId = TenantId.newBuilder()
                    .setValue(newUuid())
                    .build();
            var factory = new TestActorRequestFactory(userId, generatedTenantId);
            var result = factory.createCommand(command, when);
            return result;
        }

        public static Command withMessage(io.spine.base.CommandMessage message) {
            return create(message, USER_ID, currentTime());
        }

        public static Command createTask(boolean startTask) {
            return createTask(TASK_ID, USER_ID, startTask);
        }

        public static Command addTask() {
            return addTask(USER_ID, PROJECT_ID, currentTime());
        }

        static Command createTask(TaskId taskId, UserId userId, boolean startTask) {
            var command = CommandMessage.createTask(taskId, userId, startTask);
            return create(command, userId, currentTime());
        }

        static Command addTask(UserId userId, ProjectId projectId, Timestamp when) {
            var command = CommandMessage.addTask(projectId);
            return create(command, userId, when);
        }

        static Command removeTask() {
            var command = CommandMessage.removeTask(PROJECT_ID);
            return create(command, USER_ID, currentTime());
        }

        /** Creates a new {@code ACommand} with default properties (current time etc). */
        public static Command createProject() {
            return createProject(currentTime());
        }

        static Command createProject(Timestamp when) {
            return createProject(USER_ID, PROJECT_ID, when);
        }

        static Command createProject(UserId userId, ProjectId projectId, Timestamp when) {
            var command = CommandMessage.createProjectMessage(projectId);
            return create(command, userId, when);
        }

        public static Command startProject() {
            return startProject(USER_ID, PROJECT_ID, currentTime());
        }

        static Command startProject(UserId userId, ProjectId projectId, Timestamp when) {
            var command = CommandMessage.startProject(projectId);
            return create(command, userId, when);
        }

        private static TaskId newTaskId() {
            var id = random(1, 100);
            return TaskId.newBuilder()
                    .setId(id)
                    .build();
        }
    }

    public static class CommandMessage {

        private CommandMessage() {
            // Prevent construction from outside.
        }

        static CmdBusCreateTask createTask(TaskId taskId, UserId userId, boolean startTask) {
            var task = Task.newBuilder()
                    .setTaskId(taskId)
                    .setAssignee(userId)
                    .build();
            return CmdBusCreateTask.newBuilder()
                    .setTaskId(taskId)
                    .setTask(task)
                    .setStart(startTask)
                    .build();
        }

        static CmdBusAddTask addTask(String projectId) {
            return addTask(ProjectId.newBuilder()
                                   .setId(projectId)
                                   .build());
        }

        static CmdBusAddTask addTask(ProjectId id) {
            return CmdBusAddTask.newBuilder()
                    .setProjectId(id)
                    .build();
        }

        static CmdBusRemoveTask removeTask(ProjectId projectId) {
            return CmdBusRemoveTask.newBuilder()
                    .setProjectId(projectId)
                    .build();
        }

        public static CmdBusCreateProject createProjectMessage() {
            return createProjectMessage(newProjectId());
        }

        static CmdBusCreateProject createProjectMessage(ProjectId id) {
            return CmdBusCreateProject
                    .newBuilder()
                    .setProjectId(id)
                    .build();
        }

        static CmdBusCreateProject createProjectMessage(String projectId) {
            return createProjectMessage(ProjectId.newBuilder()
                                                .setId(projectId)
                                                .build());
        }

        static CmdBusStartProject startProject(ProjectId id) {
            return CmdBusStartProject
                    .newBuilder()
                    .setProjectId(id)
                    .build();
        }
    }
}
