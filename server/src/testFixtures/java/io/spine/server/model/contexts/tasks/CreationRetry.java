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

package io.spine.server.model.contexts.tasks;

import io.spine.server.command.AbstractCommander;
import io.spine.server.command.Command;
import io.spine.test.model.contexts.tasks.TaskId;
import io.spine.test.model.contexts.tasks.commands.CreateTask;
import io.spine.test.model.contexts.tasks.rejections.TaskRejections;

import java.util.HashSet;
import java.util.Set;

/**
 * A {@link io.spine.server.command.Commander Commander} which retries the task
 * creation if it fails.
 */
public final class CreationRetry extends AbstractCommander {

    private static final Set<TaskId> rejectedTasks = new HashSet<>();

    @Command
    CreateTask on(TaskRejections.TaskAlreadyExists rejection) {
        var id = TaskId.generate();
        rejectedTasks.add(rejection.getId());
        return CreateTask.newBuilder()
                .setId(id)
                .setName(rejection.getName())
                .setDescription(rejection.getDescription())
                .build();
    }

    public static boolean retried(TaskId id) {
        return rejectedTasks.contains(id);
    }
}
