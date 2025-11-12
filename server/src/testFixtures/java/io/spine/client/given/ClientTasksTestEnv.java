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

package io.spine.client.given;

import io.spine.test.client.tasks.CTask;
import io.spine.test.client.tasks.CTaskId;
import io.spine.test.client.tasks.command.ArchiveCTask;
import io.spine.test.client.tasks.command.CreateCTask;
import io.spine.test.client.tasks.command.DeleteCTask;
import io.spine.test.client.tasks.command.RestoreCTask;
import io.spine.test.client.tasks.command.UnarchiveCTask;
import io.spine.testing.core.given.GivenUserId;

/**
 * Test environment for the routines interacting
 * with {@linkplain io.spine.test.client.ClientTestContext#tasks() Tasks} bounded context.
 */
public final class ClientTasksTestEnv {

    /**
     * Prevents direct instantiation of this test environment.
     */
    private ClientTasksTestEnv() {
    }

    public static CreateCTask createCTask(String name) {
        var cmd = CreateCTask.newBuilder()
                .setId(CTaskId.generate())
                .setName(name)
                .setAuthor(GivenUserId.generated())
                .build();
        return cmd;
    }

    public static DeleteCTask deleteCTask(CTaskId id) {
        var cmd = DeleteCTask.newBuilder()
                .setId(id)
                .build();
        return cmd;
    }

    public static RestoreCTask restoreCTask(CTaskId id) {
        var cmd = RestoreCTask.newBuilder()
                .setId(id)
                .build();
        return cmd;
    }

    public static ArchiveCTask archiveCTask(CTaskId id) {
        var cmd = ArchiveCTask.newBuilder()
                .setId(id)
                .build();
        return cmd;
    }

    public static UnarchiveCTask unarchiveCTask(CTaskId id) {
        var cmd = UnarchiveCTask.newBuilder()
                .setId(id)
                .build();
        return cmd;
    }

    public static CTask stateAfter(CreateCTask createTask) {
        var expectedState = CTask.newBuilder()
                .setId(createTask.getId())
                .setName(createTask.getName())
                .setAuthor(createTask.getAuthor())
                .build();
        return expectedState;
    }
}
