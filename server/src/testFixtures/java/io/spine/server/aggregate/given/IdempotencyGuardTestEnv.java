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

package io.spine.server.aggregate.given;

import io.spine.base.CommandMessage;
import io.spine.base.EventMessage;
import io.spine.base.Identifier;
import io.spine.client.CommandFactory;
import io.spine.core.Command;
import io.spine.core.Event;
import io.spine.core.TenantId;
import io.spine.test.aggregate.ProjectId;
import io.spine.test.aggregate.command.AggCreateProject;
import io.spine.test.aggregate.command.AggStartProject;
import io.spine.test.aggregate.event.AggProjectPaused;
import io.spine.test.aggregate.event.AggTaskStarted;
import io.spine.test.aggregate.task.AggTaskId;
import io.spine.testing.client.TestActorRequestFactory;
import io.spine.testing.server.TestEventFactory;

import static io.spine.base.Identifier.newUuid;

public final class IdempotencyGuardTestEnv {

    private static final TestEventFactory eventFactory =
            TestEventFactory.newInstance(IdempotencyGuardTestEnv.class);
    private static final CommandFactory commandFactory =
            new TestActorRequestFactory(IdempotencyGuardTestEnv.class).command();


    /** Prevents instantiation of this test environment. */
    private IdempotencyGuardTestEnv() {
    }

    public static TenantId newTenantId() {
        return TenantId.newBuilder()
                .setValue(newUuid())
                .build();
    }

    private static AggTaskId newTaskId() {
        return AggTaskId.newBuilder()
                .setId(Identifier.newUuid())
                .build();
    }

    public static AggCreateProject createProject(ProjectId projectId) {
        return AggCreateProject.newBuilder()
                .setProjectId(projectId)
                .build();
    }

    public static AggStartProject startProject(ProjectId projectId) {
        return AggStartProject.newBuilder()
                .setProjectId(projectId)
                .build();
    }

    public static AggTaskStarted taskStarted(ProjectId projectId) {
        return AggTaskStarted.newBuilder()
                .setTaskId(newTaskId())
                .setProjectId(projectId)
                .build();
    }

    public static AggProjectPaused projectPaused(ProjectId projectId) {
        return AggProjectPaused.newBuilder()
                .setProjectId(projectId)
                .build();
    }

    public static Command command(CommandMessage commandMessage) {
        return commandFactory.create(commandMessage);
    }

    public static Event event(EventMessage eventMessage) {
        return eventFactory.createEvent(eventMessage);
    }
}
