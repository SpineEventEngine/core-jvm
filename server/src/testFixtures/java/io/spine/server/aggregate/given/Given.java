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

package io.spine.server.aggregate.given;

import com.google.protobuf.Timestamp;
import io.spine.core.Command;
import io.spine.core.UserId;
import io.spine.test.aggregate.ProjectId;
import io.spine.test.aggregate.command.AggAddTask;
import io.spine.test.aggregate.command.AggCancelProject;
import io.spine.test.aggregate.command.AggCreateProject;
import io.spine.test.aggregate.command.AggPauseProject;
import io.spine.test.aggregate.command.AggStartProject;
import io.spine.test.aggregate.event.AggProjectCancelled;
import io.spine.test.aggregate.event.AggProjectCreated;
import io.spine.test.aggregate.event.AggProjectPaused;
import io.spine.test.aggregate.event.AggProjectStarted;
import io.spine.test.aggregate.event.AggTaskAdded;
import io.spine.testdata.Sample;
import io.spine.testing.client.TestActorRequestFactory;
import io.spine.testing.core.given.GivenUserId;

import static io.spine.base.Time.currentTime;

public class Given {

    private Given() {
    }

    public static class EventMessage {

        private EventMessage() {
        }

        public static AggProjectCreated projectCreated(ProjectId id, String projectName) {
            return AggProjectCreated.newBuilder()
                                    .setProjectId(id)
                                    .setName(projectName)
                                    .build();
        }

        public static AggProjectPaused projectPaused(ProjectId id) {
            return AggProjectPaused.newBuilder()
                                   .setProjectId(id)
                                   .build();
        }

        public static AggProjectCancelled projectCancelled(ProjectId id) {
            return AggProjectCancelled.newBuilder()
                                      .setProjectId(id)
                                      .build();
        }

        public static AggTaskAdded taskAdded(ProjectId id) {
            return AggTaskAdded.newBuilder()
                               .setProjectId(id)
                               .build();
        }

        public static AggProjectStarted projectStarted(ProjectId id) {
            return AggProjectStarted.newBuilder()
                                    .setProjectId(id)
                                    .build();
        }
    }

    public static class ACommand {

        private static final UserId USER_ID = GivenUserId.newUuid();
        private static final ProjectId PROJECT_ID = Sample.messageOfType(ProjectId.class);

        private ACommand() {
        }

        public static Command createProject() {
            return createProject(currentTime());
        }

        public static Command createProject(ProjectId id) {
            return createProject(USER_ID, id, currentTime());
        }

        public static Command createProject(Timestamp when) {
            return createProject(USER_ID, PROJECT_ID, when);
        }

        public static Command createProject(UserId userId,
                                            ProjectId projectId,
                                            Timestamp when) {
            var command = CommandMessage.createProject(projectId);
            return create(command, userId, when);
        }

        public static Command addTask(ProjectId id) {
            var command = CommandMessage.addTask(id);
            return create(command, USER_ID, currentTime());
        }

        public static Command startProject(ProjectId id) {
            var command = CommandMessage.startProject(id);
            return create(command, USER_ID, currentTime());
        }

        /**
         * Creates a new {@code ACommand} with the given command message,
         * userId and timestamp using default
         * {@link io.spine.core.CommandId CommandId} instance.
         */
        public static Command create(io.spine.base.CommandMessage command,
                                     UserId userId,
                                     Timestamp when) {
            var result = new TestActorRequestFactory(userId).createCommand(command, when);
            return result;
        }
    }

    public static class CommandMessage {

        private CommandMessage() {
        }

        public static AggCreateProject createProject(ProjectId id) {
            var builder = AggCreateProject.newBuilder()
                    .setProjectId(id)
                    .setName(projectName(id));
            return builder.build();
        }

        public static AggPauseProject pauseProject(ProjectId id) {
            var builder = AggPauseProject.newBuilder()
                    .setProjectId(id);
            return builder.build();
        }

        public static AggCancelProject cancelProject(ProjectId id) {
            var builder = AggCancelProject.newBuilder()
                    .setProjectId(id);
            return builder.build();
        }

        public static AggAddTask addTask(ProjectId id) {
            var builder = AggAddTask.newBuilder()
                    .setProjectId(id);
            return builder.build();
        }

        public static AggStartProject startProject(ProjectId id) {
            var builder = AggStartProject.newBuilder()
                    .setProjectId(id);
            return builder.build();
        }
    }

    static String projectName(ProjectId id) {
        return "Project_" + id.getUuid();
    }
}
