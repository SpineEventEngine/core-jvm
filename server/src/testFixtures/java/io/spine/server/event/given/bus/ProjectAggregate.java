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

package io.spine.server.event.given.bus;

import com.google.common.collect.ImmutableList;
import io.spine.core.CommandContext;
import io.spine.server.aggregate.Aggregate;
import io.spine.server.aggregate.Apply;
import io.spine.server.command.Assign;
import io.spine.test.event.EBProjectCreated;
import io.spine.test.event.EBTaskAdded;
import io.spine.test.event.Project;
import io.spine.test.event.ProjectId;
import io.spine.test.event.Task;
import io.spine.test.event.command.EBAddTasks;
import io.spine.test.event.command.EBCreateProject;

import java.util.List;

public final class ProjectAggregate extends Aggregate<ProjectId, Project, Project.Builder> {

    @Assign
    EBProjectCreated on(EBCreateProject command, CommandContext ctx) {
        var event = projectCreated(command.getProjectId());
        return event;
    }

    @Assign
    List<EBTaskAdded> on(EBAddTasks command, CommandContext ctx) {
        ImmutableList.Builder<EBTaskAdded> events = ImmutableList.builder();

        for (var task : command.getTaskList()) {
            var event = taskAdded(command.getProjectId(), task);
            events.add(event);
        }

        return events.build();
    }

    @Apply
    private void event(EBProjectCreated event) {
        builder().setId(event.getProjectId())
                 .setStatus(Project.Status.CREATED);
    }

    @Apply
    private void event(EBTaskAdded event) {
        builder().setId(event.getProjectId())
                 .addTask(event.getTask());
    }

    private static EBProjectCreated projectCreated(ProjectId projectId) {
        return EBProjectCreated.newBuilder()
                .setProjectId(projectId)
                .build();
    }

    private static EBTaskAdded taskAdded(ProjectId projectId, Task task) {
        return EBTaskAdded.newBuilder()
                .setProjectId(projectId)
                .setTask(task)
                .build();
    }
}
