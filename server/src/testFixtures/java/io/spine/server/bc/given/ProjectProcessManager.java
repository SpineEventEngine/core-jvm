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

import io.spine.core.CommandContext;
import io.spine.core.EventContext;
import io.spine.core.Subscribe;
import io.spine.server.command.Assign;
import io.spine.server.procman.ProcessManager;
import io.spine.test.bc.BcEmptyProcess;
import io.spine.test.bc.ProjectId;
import io.spine.test.bc.command.BcCreateProject;
import io.spine.test.bc.event.BcProjectCreated;

public class ProjectProcessManager
        extends ProcessManager<ProjectId, BcEmptyProcess, BcEmptyProcess.Builder> {

    public ProjectProcessManager(ProjectId id) {
        super(id);
    }

    @Assign
    BcProjectCreated handle(BcCreateProject command, CommandContext ctx) {
        return BcProjectCreated.newBuilder()
                               .setProjectId(command.getProjectId())
                               .build();
    }

    @Subscribe
    @SuppressWarnings("unused")
    public void on(BcProjectCreated event, EventContext ctx) {
        // Do nothing, just watch.
    }
}
