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

package io.spine.server.aggregate.given.aggregate;

import io.spine.core.CommandContext;
import io.spine.server.aggregate.Aggregate;
import io.spine.server.aggregate.Apply;
import io.spine.server.command.Assign;
import io.spine.test.aggregate.AggProject;
import io.spine.test.aggregate.ProjectId;
import io.spine.test.aggregate.Status;
import io.spine.test.aggregate.command.AggAddTask;
import io.spine.test.aggregate.command.AggCreateProject;
import io.spine.test.aggregate.event.AggProjectCreated;
import io.spine.test.aggregate.event.AggTaskAdded;

import static io.spine.server.aggregate.given.Given.EventMessage.projectCreated;
import static io.spine.server.aggregate.given.Given.EventMessage.taskAdded;

/**
 * The test environment class for checking raising and catching exceptions.
 */
public final class FaultyAggregate
        extends Aggregate<ProjectId, AggProject, AggProject.Builder> {

    public static final String BROKEN_HANDLER = "broken_handler";
    public static final String BROKEN_APPLIER = "broken_applier";

    private final boolean brokenHandler;
    private final boolean brokenApplier;

    public FaultyAggregate(ProjectId id, boolean brokenHandler, boolean brokenApplier) {
        super(id);
        this.brokenHandler = brokenHandler;
        this.brokenApplier = brokenApplier;
    }

    @Assign
    AggProjectCreated handle(AggCreateProject cmd, CommandContext ctx) {
        if (brokenHandler) {
            throw new IllegalStateException(BROKEN_HANDLER);
        }
        return projectCreated(cmd.getProjectId(), cmd.getName());
    }

    @Apply
    private void event(AggProjectCreated event) {
        if (brokenApplier) {
            throw new IllegalStateException(BROKEN_APPLIER);
        }
        builder().setStatus(Status.CREATED);
    }

    @Assign
    AggTaskAdded handle(AggAddTask cmd) {
        return taskAdded(cmd.getProjectId());
    }

    @Apply
    private void event(AggTaskAdded event) {
        /* This call to `state()` is prohibited from `Apply`-marked method. */
        var id = state().getId();
        builder().setId(id);
    }
}
