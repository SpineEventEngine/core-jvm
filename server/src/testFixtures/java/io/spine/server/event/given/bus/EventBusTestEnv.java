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

package io.spine.server.event.given.bus;

import io.spine.base.CommandMessage;
import io.spine.client.ActorRequestFactory;
import io.spine.core.Command;
import io.spine.core.Event;
import io.spine.core.TenantId;
import io.spine.grpc.MemoizingObserver;
import io.spine.server.event.EventBus;
import io.spine.server.event.EventStreamQuery;
import io.spine.server.tenant.TenantAwareOperation;
import io.spine.test.event.ProjectId;
import io.spine.test.event.Task;
import io.spine.test.event.command.EBAddTasks;
import io.spine.test.event.command.EBArchiveProject;
import io.spine.test.event.command.EBCreateProject;
import io.spine.testing.client.TestActorRequestFactory;

import java.util.List;

import static io.spine.base.Identifier.newUuid;
import static io.spine.grpc.StreamObservers.memoizingObserver;
import static io.spine.testdata.Sample.builderForType;

/**
 * Test environment classes for the {@code server.event} package.
 */
public class EventBusTestEnv {

    private static final TenantId TENANT_ID = tenantId();
    static final ProjectId PROJECT_ID = projectId();

    public static final ActorRequestFactory requestFactory =
            new TestActorRequestFactory(EventBusTestEnv.class, TENANT_ID);

    /** Prevent instantiation of this utility class. */
    private EventBusTestEnv() {
    }

    private static ProjectId projectId() {
        return ProjectId.newBuilder()
                        .setId(newUuid())
                        .build();
    }

    private static TenantId tenantId() {
        var value = EventBusTestEnv.class.getName();
        var id = TenantId.newBuilder()
                .setValue(value)
                .build();
        return id;
    }

    public static EBCreateProject createProject() {
        EBCreateProject.Builder command = builderForType(EBCreateProject.class);
        return command.setProjectId(PROJECT_ID)
                      .build();
    }

    public static EBAddTasks addTasks(Task... tasks) {
        EBAddTasks.Builder builder = builderForType(EBAddTasks.class);
        builder.setProjectId(PROJECT_ID)
               .clearTask();
        for (var task : tasks) {
            builder.addTask(task);
        }
        var command = builder.build();
        return command;
    }

    public static Task newTask(boolean done) {
        Task.Builder task = builderForType(Task.class);
        return task.setDone(done)
                   .build();
    }

    /**
     * Returns an {@link EBArchiveProject} command with an unfilled required
     * {@link EBArchiveProject#getReason()} field.
     */
    public static EBArchiveProject invalidArchiveProject() {
        EBArchiveProject.Builder command = builderForType(EBArchiveProject.class);
        return command.setProjectId(PROJECT_ID)
                      .build();
    }

    public static Command command(CommandMessage message) {
        return requestFactory.command()
                             .create(message);
    }

    /**
     * Reads all events from the event bus event store for a tenant specified by
     * the {@link EventBusTestEnv#TENANT_ID}.
     */
    public static List<Event> readEvents(EventBus eventBus) {
        MemoizingObserver<Event> observer = memoizingObserver();
        var operation = new TenantAwareOperation(TENANT_ID) {
            @Override
            public void run() {
                eventBus.eventStore()
                        .read(allEventsQuery(), observer);
            }
        };
        operation.execute();

        var results = observer.responses();
        return results;
    }

    /**
     * Creates a new {@link EventStreamQuery} without any filters.
     */
    private static EventStreamQuery allEventsQuery() {
        return EventStreamQuery.newBuilder()
                               .build();
    }
}
