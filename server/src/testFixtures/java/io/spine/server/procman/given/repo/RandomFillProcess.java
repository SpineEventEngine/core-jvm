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

import io.spine.server.procman.ProcessManagerMigration;
import io.spine.test.procman.Project;
import io.spine.test.procman.ProjectId;
import io.spine.test.procman.Task;
import io.spine.test.procman.TaskId;

import static io.spine.base.Identifier.newUuid;
import static io.spine.test.procman.Project.Status.DONE;

/**
 * Fills the state of the migrated {@code ProcessManager} with some random values.
 */
public final class RandomFillProcess
        extends ProcessManagerMigration<ProjectId, TestProcessManager, Project, Project.Builder> {

    @Override
    public Project apply(Project project) {
        var newState = project
                .toBuilder()
                .setId(project.getId())
                .setName(newUuid())
                .setStatus(DONE)
                .addTask(task())
                .build();
        return newState;
    }

    private static Task task() {
        return Task.newBuilder()
                .setTaskId(taskId())
                .setTitle(newUuid())
                .setDescription(newUuid())
                .build();
    }

    private static TaskId taskId() {
        return TaskId.newBuilder()
                .setId(newUuid().hashCode())
                .build();
    }
}
