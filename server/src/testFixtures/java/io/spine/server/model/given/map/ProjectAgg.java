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

package io.spine.server.model.given.map;

import io.spine.model.contexts.projects.Project;
import io.spine.model.contexts.projects.ProjectId;
import io.spine.model.contexts.projects.command.SigCreateProject;
import io.spine.model.contexts.projects.command.SigStartProject;
import io.spine.model.contexts.projects.event.SigProjectCreated;
import io.spine.model.contexts.projects.event.SigProjectStarted;
import io.spine.model.contexts.projects.rejection.SigProjectAlreadyCompleted;
import io.spine.server.aggregate.Aggregate;
import io.spine.server.aggregate.Apply;
import io.spine.server.command.Assign;

/**
 * A test aggregate that throws {@link SigProjectAlreadyCompleted} rejection for any
 * handled command.
 */
public final class ProjectAgg extends Aggregate<ProjectId, Project, Project.Builder> {

    @Assign
    @SuppressWarnings("DoNotCallSuggester") // Does not apply to this test env. class
    SigProjectStarted handle(SigStartProject c) throws SigProjectAlreadyCompleted {
        throw SigProjectAlreadyCompleted
                .newBuilder()
                .setProject(c.getId())
                .build();
    }

    @Assign
    @SuppressWarnings("DoNotCallSuggester") // Does not apply to this test env. class
    SigProjectCreated handle(SigCreateProject c) throws SigProjectAlreadyCompleted {
        throw SigProjectAlreadyCompleted
                .newBuilder()
                .setProject(c.getId())
                .build();
    }

    @Apply
    private void on(SigProjectStarted e) {
        // do nothing.
    }

    @Apply
    private void on(SigProjectCreated e) {
        // do nothing.
    }
}
