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

package io.spine.server.enrich.given;

import io.spine.server.aggregate.Aggregate;
import io.spine.server.aggregate.Apply;
import io.spine.server.command.Assign;
import io.spine.server.enrich.given.command.EitAssignTask;
import io.spine.server.enrich.given.command.EitCreateTask;
import io.spine.server.enrich.given.event.EitTaskAssigned;
import io.spine.server.enrich.given.event.EitTaskCreated;

final class EitTaskAggregate extends Aggregate<EitTaskId, EitTask, EitTask.Builder> {

    @Assign
    EitTaskCreated handle(EitCreateTask cmd) {
        return EitTaskCreated
                .newBuilder()
                .setTask(cmd.getTask())
                .setName(cmd.getName())
                .setDescription(cmd.getDescription())
                .build();
    }

    @Apply
    private void event(EitTaskCreated event) {
        builder().setId(event.getTask())
                 .setName(event.getName())
                 .setDescription(event.getDescription());
    }

    @Assign
    EitTaskAssigned handle(EitAssignTask cmd) {
        return EitTaskAssigned
                .newBuilder()
                .setTask(cmd.getTask())
                .setAssignee(cmd.getAssignee())
                .build();
    }
}
