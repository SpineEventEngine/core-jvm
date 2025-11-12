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

import com.google.common.collect.ImmutableList;
import io.spine.base.EventMessage;
import io.spine.server.aggregate.Aggregate;
import io.spine.server.aggregate.Apply;
import io.spine.server.command.Assign;
import io.spine.test.client.tasks.CTask;
import io.spine.test.client.tasks.CTaskId;
import io.spine.test.client.tasks.command.ArchiveCTask;
import io.spine.test.client.tasks.command.CreateCTask;
import io.spine.test.client.tasks.command.DeleteCTask;
import io.spine.test.client.tasks.command.RestoreCTask;
import io.spine.test.client.tasks.command.UnarchiveCTask;
import io.spine.test.client.tasks.event.CTaskArchived;
import io.spine.test.client.tasks.event.CTaskCreated;
import io.spine.test.client.tasks.event.CTaskDeleted;
import io.spine.test.client.tasks.event.CTaskRestored;
import io.spine.test.client.tasks.event.CTaskUnarchived;

import java.util.List;

public final class TaskAggregate extends Aggregate<CTaskId, CTask, CTask.Builder> {

    /**
     * Handles a {@code CreateTask} command and emits a {@code TaskCreated} event.
     *
     * <p>The event is not declared in the signature on purpose. As for now, there is no reliable
     * way to tell which Bounded Context the {@code TaskCreated} event belongs to.
     */
    @Assign
    List<EventMessage> handle(CreateCTask cmd) {
        return ImmutableList.of(
                CTaskCreated.newBuilder()
                        .setId(cmd.getId())
                        .setName(cmd.getName())
                        .setAuthor(cmd.getAuthor())
                        .build()
        );
    }

    @Apply
    private void on(CTaskCreated event) {
        builder().setName(event.getName())
                 .setAuthor(event.getAuthor());
    }

    @Assign
    CTaskDeleted handle(DeleteCTask cmd) {
        var event = CTaskDeleted.newBuilder()
                .setId(cmd.getId())
                .build();
        return event;
    }

    @Apply
    private void on(CTaskDeleted event) {
        setDeleted(true);
    }

    @Assign
    CTaskRestored handle(RestoreCTask cmd) {
        var event = CTaskRestored.newBuilder()
                .setId(cmd.getId())
                .build();
        return event;
    }

    @Apply
    private void on(CTaskRestored event) {
        setDeleted(false);
    }

    @Assign
    CTaskArchived handle(ArchiveCTask cmd) {
        var event = CTaskArchived.newBuilder()
                .setId(cmd.getId())
                .build();
        return event;
    }

    @Apply
    private void on(CTaskArchived event) {
        setArchived(true);
    }

    @Assign
    CTaskUnarchived handle(UnarchiveCTask cmd) {
        var event = CTaskUnarchived.newBuilder()
                .setId(cmd.getId())
                .build();
        return event;
    }

    @Apply
    private void on(CTaskUnarchived event) {
        setArchived(false);
    }
}
