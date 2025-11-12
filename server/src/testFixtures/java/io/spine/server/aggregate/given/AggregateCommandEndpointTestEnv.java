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

import io.spine.core.Command;
import io.spine.core.CommandContext;
import io.spine.server.aggregate.Aggregate;
import io.spine.server.aggregate.AggregateRepository;
import io.spine.server.aggregate.Apply;
import io.spine.server.command.Assign;
import io.spine.server.command.CommandHistory;
import io.spine.test.aggregate.AggProject;
import io.spine.test.aggregate.ProjectId;
import io.spine.test.aggregate.command.AggAddTask;
import io.spine.test.aggregate.command.AggCreateProject;
import io.spine.test.aggregate.command.AggStartProject;
import io.spine.test.aggregate.event.AggProjectCreated;
import io.spine.test.aggregate.event.AggProjectStarted;
import io.spine.test.aggregate.event.AggTaskAdded;

public final class AggregateCommandEndpointTestEnv {

    /** Prevent instantiation of this utility class. */
    private AggregateCommandEndpointTestEnv() {
    }

    public static class ProjectAggregate
            extends Aggregate<ProjectId, AggProject, AggProject.Builder> {

        // Needs to be `static` to share the state updates in scope of the test.
        private static final CommandHistory commandsHandled = new CommandHistory();

        private ProjectAggregate(ProjectId id) {
            super(id);
        }

        public static void assertHandled(Command expected) {
            commandsHandled.assertHandled(expected);
        }

        public static void clearCommandsHandled() {
            commandsHandled.clear();
        }

        @Assign
        AggProjectCreated handle(AggCreateProject msg, CommandContext context) {
            commandsHandled.add(msg, context);
            return AggProjectCreated.newBuilder()
                                    .setProjectId(msg.getProjectId())
                                    .setName(msg.getName())
                                    .build();
        }

        @Apply
        private void apply(AggProjectCreated event) {
            builder().setId(event.getProjectId())
                     .setName(event.getName());
        }

        @Assign
        AggTaskAdded handle(AggAddTask msg, CommandContext context) {
            commandsHandled.add(msg, context);
            return AggTaskAdded.newBuilder()
                               .setProjectId(msg.getProjectId())
                               .build();
        }

        @Apply
        private void apply(AggTaskAdded event) {
            builder().setId(event.getProjectId());
        }

        @Assign
        AggProjectStarted handle(AggStartProject msg, CommandContext context) {
            commandsHandled.add(msg, context);
            return AggProjectStarted.newBuilder()
                                    .setProjectId(msg.getProjectId())
                                    .build();
        }

        @Apply
        private void apply(AggProjectStarted event) {
        }
    }

    public static class ProjectAggregateRepository
            extends AggregateRepository<ProjectId, ProjectAggregate, AggProject> {
    }
}
