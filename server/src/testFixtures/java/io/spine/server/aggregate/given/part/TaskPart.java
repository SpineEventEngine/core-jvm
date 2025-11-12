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

package io.spine.server.aggregate.given.part;

import io.spine.server.aggregate.AggregatePart;
import io.spine.server.aggregate.Apply;
import io.spine.server.command.Assign;
import io.spine.server.tuple.Pair;
import io.spine.test.aggregate.Task;
import io.spine.test.aggregate.command.AggCreateTask;
import io.spine.test.aggregate.event.AggTaskAssigned;
import io.spine.test.aggregate.event.AggTaskCreated;
import io.spine.test.aggregate.task.AggTask;
import io.spine.test.aggregate.task.AggTaskId;

/**
 * An aggregate part with {@link Task} state, which belongs to the aggregate
 * represented by {@link AnAggregateRoot}.
 */
public class TaskPart
        extends AggregatePart<AggTaskId, AggTask, AggTask.Builder, TaskRoot> {

    public TaskPart(TaskRoot root) {
        super(root);
    }

    @Assign
    Pair<AggTaskCreated, AggTaskAssigned> handle(AggCreateTask command) {
        var created = AggTaskCreated.newBuilder()
                .setTaskId(command.getTaskId())
                .build();
        var assigned = AggTaskAssigned.newBuilder()
                .setTaskId(command.getTaskId())
                .setNewAssignee(command.getAssignee())
                .build();
        return Pair.of(created, assigned);
    }

    @Apply
    private void apply(AggTaskCreated event) {
        builder().setId(event.getTaskId());
    }

    @Apply
    private void apply(AggTaskAssigned event) {
        builder().setAssignee(event.getNewAssignee());
    }
}
