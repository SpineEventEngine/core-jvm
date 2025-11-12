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

import io.spine.test.procman.ProjectId;
import io.spine.test.procman.command.PmAddTask;
import io.spine.test.procman.command.PmArchiveProject;
import io.spine.test.procman.command.PmCreateProject;
import io.spine.test.procman.command.PmDeleteProject;
import io.spine.test.procman.command.PmDoNothing;
import io.spine.test.procman.command.PmStartProject;
import io.spine.test.procman.event.PmProjectCreated;
import io.spine.test.procman.event.PmProjectStarted;
import io.spine.test.procman.event.PmTaskAdded;
import io.spine.testdata.Sample;

import static io.spine.testdata.Sample.builderForType;

/**
 * Factory methods for command messages.
 */
public class GivenCommandMessage {

    public static final ProjectId ID = Sample.messageOfType(ProjectId.class);

    /** Prevents instantiation of this utility class. */
    private GivenCommandMessage() {
    }

    public static PmCreateProject createProject() {
        return ((PmCreateProject.Builder) builderForType(PmCreateProject.class))
                .setProjectId(ID)
                .build();
    }

    public static PmStartProject startProject() {
        return ((PmStartProject.Builder) builderForType(PmStartProject.class))
                .setProjectId(ID)
                .build();
    }

    public static PmAddTask addTask() {
        return ((PmAddTask.Builder) builderForType(PmAddTask.class))
                .setProjectId(ID)
                .build();
    }

    public static PmArchiveProject archiveProject() {
        return ((PmArchiveProject.Builder) builderForType(PmArchiveProject.class))
                .setProjectId(ID)
                .build();
    }

    public static PmDeleteProject deleteProject() {
        return ((PmDeleteProject.Builder) builderForType(PmDeleteProject.class))
                .setProjectId(ID)
                .build();
    }

    public static PmDoNothing doNothing() {
        return ((PmDoNothing.Builder) builderForType(PmDoNothing.class))
                .setProjectId(ID)
                .build();
    }

    public static PmProjectStarted projectStarted() {
        return ((PmProjectStarted.Builder) builderForType(PmProjectStarted.class))
                .setProjectId(ID)
                .build();
    }

    public static PmProjectCreated projectCreated() {
        return ((PmProjectCreated.Builder) builderForType(PmProjectCreated.class))
                .setProjectId(ID)
                .build();
    }

    public static PmTaskAdded taskAdded() {
        return ((PmTaskAdded.Builder) builderForType(PmTaskAdded.class))
                .setProjectId(ID)
                .build();
    }
}
