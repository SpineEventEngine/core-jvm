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

package io.spine.server.entity.given;

import com.google.protobuf.FieldMask;
import io.spine.test.aggregate.AggProject;
import io.spine.test.aggregate.ProjectId;
import io.spine.test.aggregate.Status;
import io.spine.test.aggregate.Task;
import io.spine.test.aggregate.TaskId;
import io.spine.type.TypeUrl;

import static com.google.protobuf.util.FieldMaskUtil.fromFieldNumbers;
import static java.lang.String.format;

public class FieldMasksTestEnv {

    /** Prevents instantiation of this utility class. */
    private FieldMasksTestEnv() {
    }

    public static class Given {

        public static final TypeUrl TYPE = TypeUrl.of(AggProject.class);

        /** Prevents instantiation of this utility class. */
        private Given() {
        }

        public static AggProject newProject(String id) {
            var projectId = ProjectId.newBuilder()
                    .setUuid(id)
                    .build();
            var first = Task.newBuilder()
                    .setTaskId(TaskId.newBuilder()
                                       .setId(1)
                                       .build())
                    .setTitle("First Task")
                    .build();

            var second = Task.newBuilder()
                    .setTaskId(TaskId.newBuilder()
                                       .setId(2)
                                       .build())
                    .setTitle("Second Task")
                    .build();

            var project = AggProject.newBuilder()
                    .setId(projectId)
                    .setName(format("Test project : %s", id))
                    .addTask(first)
                    .addTask(second)
                    .setStatus(Status.CREATED)
                    .build();
            return project;
        }

        public static FieldMask fieldMask(int... fieldIndices) {
            return fromFieldNumbers(AggProject.class, fieldIndices);
        }
    }
}
