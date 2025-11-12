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

package io.spine.server.bc.given;

import com.google.common.collect.Lists;
import io.spine.core.CommandContext;
import io.spine.server.aggregate.Aggregate;
import io.spine.server.aggregate.Apply;
import io.spine.server.command.Assign;
import io.spine.test.bc.Project;
import io.spine.test.bc.ProjectId;
import io.spine.test.bc.command.BcAddTask;
import io.spine.test.bc.command.BcCreateProject;
import io.spine.test.bc.command.BcStartProject;
import io.spine.test.bc.event.BcProjectCreated;
import io.spine.test.bc.event.BcProjectStarted;
import io.spine.test.bc.event.BcTaskAdded;

import java.util.List;

public class ProjectAggregate
        extends Aggregate<ProjectId, Project, Project.Builder> {

    private ProjectAggregate(ProjectId id) {
        super(id);
    }

    @Assign
    BcProjectCreated handle(BcCreateProject cmd, CommandContext ctx) {
        return Given.EventMessage.projectCreated(cmd.getProjectId());
    }

    @Assign
    BcTaskAdded handle(BcAddTask cmd, CommandContext ctx) {
        return Given.EventMessage.taskAdded(cmd.getProjectId());
    }

    @Assign
    List<BcProjectStarted> handle(BcStartProject cmd, CommandContext ctx) {
        var message = Given.EventMessage.projectStarted(cmd.getProjectId());
        return Lists.newArrayList(message);
    }

    @Apply
    private void event(BcProjectCreated event) {
        builder().setId(event.getProjectId())
                 .setStatus(Project.Status.CREATED);
    }

    @Apply
    private void event(@SuppressWarnings("unused") BcTaskAdded event) {
        // NOP
    }

    @Apply
    private void event(BcProjectStarted event) {
        builder().setId(event.getProjectId())
                 .setStatus(Project.Status.STARTED);
    }
}
