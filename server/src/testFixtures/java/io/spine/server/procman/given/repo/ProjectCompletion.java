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

package io.spine.server.procman.given.repo;

import io.spine.server.command.Assign;
import io.spine.server.procman.ProcessManager;
import io.spine.server.procman.ProcessManagerRepository;
import io.spine.server.route.CommandRouting;
import io.spine.test.procman.ProcessId;
import io.spine.test.procman.ProcessState;
import io.spine.test.procman.ProjectId;
import io.spine.test.procman.command.PmCompleteProject;
import io.spine.test.procman.command.PmCreateProject;
import io.spine.test.procman.event.PmProjectCreated;

import static io.spine.testdata.Sample.builderForType;

/**
 * A test environment for testing
 * {@linkplain ProcessManagerRepository#setupCommandRouting(CommandRouting) custom command
 * routing setup}.
 *
 * @see Repository#setupCommandRouting(CommandRouting)
 */
public class ProjectCompletion
        extends ProcessManager<ProcessId, ProcessState, ProcessState.Builder> {

    @Assign
    PmProjectCreated handle(PmCompleteProject command) {
        PmProjectCreated.Builder event = builderForType(PmProjectCreated.class);
        return event.setProjectId(command.getProjectId())
                    .build();
    }

    public static class Repository
            extends ProcessManagerRepository<ProcessId, ProjectCompletion, ProcessState> {

        private boolean callbackCalled;

        @Override
        protected void setupCommandRouting(CommandRouting<ProcessId> routing) {
            super.setupCommandRouting(routing);
            routing.route(PmCreateProject.class,
                          (command, context) -> toProcessId(command.getProjectId()));
            this.callbackCalled = true;
        }

        private static ProcessId toProcessId(ProjectId projectId) {
            return ProcessId
                    .newBuilder()
                    .setUuid(projectId.getId())
                    .build();
        }

        public boolean callbackCalled() {
            return callbackCalled;
        }

    }
}
