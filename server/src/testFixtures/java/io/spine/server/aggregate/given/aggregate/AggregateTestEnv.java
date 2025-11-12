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

package io.spine.server.aggregate.given.aggregate;

import io.spine.base.CommandMessage;
import io.spine.base.EntityState;
import io.spine.base.EventMessage;
import io.spine.core.Command;
import io.spine.core.Event;
import io.spine.core.TenantId;
import io.spine.protobuf.AnyPacker;
import io.spine.server.type.CommandEnvelope;
import io.spine.test.aggregate.command.AggAssignTask;
import io.spine.test.aggregate.command.AggCreateTask;
import io.spine.test.aggregate.command.AggReassignTask;
import io.spine.test.aggregate.task.AggTaskId;
import io.spine.test.storage.StateImported;
import io.spine.testing.client.TestActorRequestFactory;
import io.spine.testing.core.given.GivenUserId;
import io.spine.testing.server.TestEventFactory;

import static io.spine.base.Identifier.newUuid;
import static io.spine.testing.core.given.GivenVersion.withNumber;

public final class AggregateTestEnv {

    /** Prevent instantiation of this test environment. */
    private AggregateTestEnv() {
        // Do nothing.
    }

    private static AggTaskId newTaskId() {
        return AggTaskId.newBuilder()
                .setId(newUuid())
                .build();
    }

    public static TenantId newTenantId() {
        return TenantId.newBuilder()
                .setValue(newUuid())
                .build();
    }

    public static AggCreateTask createTask() {
        return AggCreateTask.newBuilder()
                .setTaskId(newTaskId())
                .build();
    }

    public static AggAssignTask assignTask() {
        return AggAssignTask.newBuilder()
                .setTaskId(newTaskId())
                .setAssignee(GivenUserId.generated())
                .build();
    }

    public static AggReassignTask reassignTask() {
        return AggReassignTask.newBuilder()
                .setTaskId(newTaskId())
                .setAssignee(GivenUserId.generated())
                .build();
    }

    public static Command command(CommandMessage commandMessage, TenantId tenantId) {
        return requestFactory(tenantId).command()
                .create(commandMessage);
    }

    public static Command command(CommandMessage commandMessage) {
        return requestFactory().command()
                .create(commandMessage);
    }

    public static CommandEnvelope env(CommandMessage commandMessage) {
        return CommandEnvelope.of(command(commandMessage));
    }

    public static CommandEnvelope env(Command command) {
        return CommandEnvelope.of(command);
    }

    public static Event event(EventMessage eventMessage, int versionNumber) {
        return eventFactory().createEvent(eventMessage, withNumber(versionNumber));
    }

    private static TestActorRequestFactory requestFactory(TenantId tenantId) {
        return new TestActorRequestFactory(AggregateTestEnv.class, tenantId);
    }

    public static TestActorRequestFactory requestFactory() {
        return new TestActorRequestFactory(AggregateTestEnv.class);
    }

    public static TestEventFactory eventFactory() {
        return TestEventFactory.newInstance(requestFactory());
    }

    public static EventMessage event(EntityState<?> state) {
        return StateImported
                .newBuilder()
                .setState(AnyPacker.pack(state))
                .build();
    }
}
