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

import io.spine.test.reflect.ProjectId;
import io.spine.test.reflect.ReflectRejections.InvalidProjectName;
import io.spine.test.reflect.command.RefCreateProject;
import io.spine.test.reflect.command.RefStartProject;
import io.spine.test.reflect.event.RefProjectCreated;

import static io.spine.base.Identifier.newUuid;

public class Given {

    /** Prevents instantiation of this utility class. */
    private Given() {
    }

    private static ProjectId newProjectId() {
        var uuid = newUuid();
        return ProjectId.newBuilder()
                .setId(uuid)
                .build();
    }

    public static class EventMessage {

        private static final ProjectId DUMMY_PROJECT_ID = newProjectId();
        private static final RefProjectCreated PROJECT_CREATED = projectCreated(DUMMY_PROJECT_ID);

        /** Prevents instantiation of this utility class. */
        private EventMessage() {
        }

        public static RefProjectCreated projectCreated() {
            return PROJECT_CREATED;
        }

        public static RefProjectCreated projectCreated(ProjectId id) {
            return RefProjectCreated.newBuilder()
                    .setProjectId(id)
                    .build();
        }
    }

    public static class CommandMessage {

        /** Prevents instantiation of this utility class. */
        private CommandMessage() {
        }

        public static RefCreateProject createProject() {
            return RefCreateProject.newBuilder()
                    .setProjectId(newProjectId())
                    .build();
        }

        public static RefStartProject startProject() {
            return RefStartProject.newBuilder()
                    .setProjectId(newProjectId())
                    .build();
        }
    }

    public static class RejectionMessage {

        private static final ProjectId DUMMY_PROJECT_ID = newProjectId();
        private static final InvalidProjectName INVALID_PROJECT_NAME =
                invalidProjectName(DUMMY_PROJECT_ID);

        /** Prevents instantiation of this utility class. */
        private RejectionMessage() {
        }

        public static InvalidProjectName invalidProjectName() {
            return INVALID_PROJECT_NAME;
        }

        private static InvalidProjectName invalidProjectName(ProjectId id) {
            var invalidProjectName = InvalidProjectName.newBuilder()
                    .setProjectId(id)
                    .build();
            return invalidProjectName;
        }
    }
}
