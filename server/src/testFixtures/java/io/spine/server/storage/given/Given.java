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

package io.spine.server.storage.given;

import io.spine.test.storage.StgProjectId;
import io.spine.test.storage.command.StgAddTask;
import io.spine.test.storage.command.StgCreateProject;
import io.spine.test.storage.command.StgStartProject;
import io.spine.test.storage.event.StgProjectCreated;

import static io.spine.base.Identifier.newUuid;

public class Given {

    /** Prevent instantiation of this utility class. */
    private Given() {
    }

    public static class EventMessage {

        private EventMessage() {
        }

        public static StgProjectCreated projectCreated(StgProjectId id) {
            return StgProjectCreated.newBuilder()
                    .setProjectId(id)
                    .build();
        }
    }

    public static class CommandMessage {

        private CommandMessage() {
        }

        public static StgCreateProject createProject() {
            return StgCreateProject.newBuilder()
                    .setProjectId(newProjectId())
                    .build();
        }

        public static StgCreateProject createProject(StgProjectId id) {
            return StgCreateProject.newBuilder()
                    .setProjectId(id)
                    .build();
        }

        public static StgAddTask addTask(StgProjectId id) {
            return StgAddTask.newBuilder()
                    .setProjectId(id)
                    .build();
        }

        public static StgStartProject startProject(StgProjectId id) {
            return StgStartProject.newBuilder()
                    .setProjectId(id)
                    .build();
        }

        private static StgProjectId newProjectId() {
            var uuid = newUuid();
            return StgProjectId.newBuilder()
                    .setId(uuid)
                    .build();
        }
    }
}
