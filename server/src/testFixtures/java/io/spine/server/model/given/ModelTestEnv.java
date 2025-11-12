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

package io.spine.server.model.given;

import io.spine.core.External;
import io.spine.server.aggregate.Aggregate;
import io.spine.server.aggregate.Apply;
import io.spine.server.command.AbstractAssignee;
import io.spine.server.command.Assign;
import io.spine.server.command.Command;
import io.spine.server.procman.ProcessManager;
import io.spine.server.test.shared.EmptyProcess;
import io.spine.test.reflect.Project;
import io.spine.test.reflect.ProjectId;
import io.spine.test.reflect.command.RefCreateProject;
import io.spine.test.reflect.command.RefStartProject;
import io.spine.test.reflect.event.RefProjectCreated;
import io.spine.test.reflect.event.RefProjectStarted;

/**
 * Test environment for {@linkplain io.spine.server.model.ModelTest Model tests}.
 */
public class ModelTestEnv {

    /** Prevents instantiation of this utility class. */
    private ModelTestEnv() {
    }

    public static class MAggregate extends Aggregate<ProjectId, Project, Project.Builder> {

        private MAggregate(ProjectId id) {
            super(id);
        }

        @Assign
        RefProjectCreated on(RefCreateProject cmd) {
            return RefProjectCreated.getDefaultInstance();
        }

        @Apply
        private void event(RefProjectCreated evt) {
            builder().setId(evt.getProjectId());
        }

        @Assign
        RefProjectStarted on(RefStartProject cmd) {
            return RefProjectStarted.getDefaultInstance();
        }

        @Apply
        private void event(RefProjectStarted evt) {
            builder().setId(evt.getProjectId());
        }
    }

    public static class MCommandAssignee extends AbstractAssignee {

        @Assign
        RefProjectCreated on(RefCreateProject cmd) {
            return RefProjectCreated.getDefaultInstance();
        }
    }

    public static class MProcessManager
            extends ProcessManager<String, EmptyProcess, EmptyProcess.Builder> {

        private MProcessManager(String id) {
            super(id);
        }

        @Assign
        RefProjectCreated on(RefCreateProject cmd) {
            return RefProjectCreated.getDefaultInstance();
        }

        @Assign
        RefProjectStarted on(RefStartProject cmd) {
            return RefProjectStarted.getDefaultInstance();
        }
    }

    /**
     * A commander that declares an {@code external} command substitution method, which is
     * forbidden by the {@linkplain io.spine.server.command.model.CommanderClass model}.
     */
    public static class FaultyCommander
            extends ProcessManager<String, EmptyProcess, EmptyProcess.Builder> {

        private FaultyCommander(String id) {
            super(id);
        }

        @Command
        RefStartProject handle(@External RefCreateProject cmd) {
            return RefStartProject.getDefaultInstance();
        }
    }
}
