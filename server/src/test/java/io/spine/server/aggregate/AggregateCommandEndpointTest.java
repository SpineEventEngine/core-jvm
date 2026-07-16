/*
 * Copyright 2026, TeamDev. All rights reserved.
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

package io.spine.server.aggregate;

import com.google.common.truth.Truth;
import io.spine.core.Command;
import io.spine.core.Subscribe;
import io.spine.server.BoundedContext;
import io.spine.server.BoundedContextBuilder;
import io.spine.server.aggregate.given.AggregateCommandEndpointTestEnv.ProjectAggregate;
import io.spine.server.aggregate.given.AggregateCommandEndpointTestEnv.ProjectAggregateRepository;
import io.spine.server.event.AbstractEventSubscriber;
import io.spine.server.type.CommandEnvelope;
import io.spine.test.aggregate.AggProject;
import io.spine.test.aggregate.ProjectId;
import io.spine.test.aggregate.command.AggCreateProject;
import io.spine.test.aggregate.event.AggProjectCreated;
import io.spine.testing.server.model.ModelTests;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static io.spine.server.aggregate.given.Given.ACommand.addTask;
import static io.spine.server.aggregate.given.Given.ACommand.createProject;
import static io.spine.server.aggregate.given.Given.ACommand.startProject;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("`AggregateCommandEndpoint` should")
class AggregateCommandEndpointTest {

    private BoundedContext context;
    private AggregateRepository<ProjectId, ProjectAggregate, AggProject> repository;

    private ProjectId projectId;
    private Subscriber subscriber;

    @BeforeEach
    void setUp() {
        ModelTests.dropAllModels();
        context = BoundedContextBuilder.assumingTests()
                                       .build();
        projectId = ProjectId.generate();

        // Create a subscriber of ProjectCreated event.
        subscriber = new Subscriber();
        context.eventBus()
               .register(subscriber);

        repository = new ProjectAggregateRepository();
        context.internalAccess()
               .register(repository);
    }

    @AfterEach
    void tearDown() throws Exception {
        ProjectAggregate.clearCommandsHandled();
        context.close();
    }

    @Test
    @DisplayName("post events on command dispatching")
    void postEventsOnCommandDispatching() {
        var cmd = CommandEnvelope.of(createProject(projectId));

        repository.dispatchCommand(cmd);
        var msg = subscriber.remembered;
        assertThat(msg.getProjectId())
                .isEqualTo(projectId);
    }

    @Test
    @DisplayName("store aggregate on command dispatching")
    void storeAggregateOnCommandDispatching() {
        var cmd = CommandEnvelope.of(createProject(projectId));
        var msg = (AggCreateProject) cmd.message();

        repository.dispatchCommand(cmd);
        var optional = repository.find(projectId);
        assertTrue(optional.isPresent());

        var aggregate = optional.get();
        assertThat(aggregate.id()).isEqualTo(projectId);

        Truth.assertThat(aggregate.state().getName())
             .isEqualTo(msg.getName());
    }

    @Test
    @DisplayName("dispatch command")
    void dispatchCommand() {
        assertDispatches(createProject());
    }

    @Test
    @DisplayName("dispatch several commands")
    void dispatchSeveralCommands() {
        assertDispatches(createProject(projectId));
        assertDispatches(addTask(projectId));
        assertDispatches(startProject(projectId));
    }

    /*
     * Utility methods.
     ****************************/

    @SuppressWarnings("CheckReturnValue")
    // ignore ID of the aggregate returned by the repository
    private void assertDispatches(Command cmd) {
        var envelope = CommandEnvelope.of(cmd);
        repository.dispatchCommand(envelope);
        ProjectAggregate.assertHandled(cmd);
    }

    private static class Subscriber extends AbstractEventSubscriber {

        private AggProjectCreated remembered;

        @Subscribe
        void on(AggProjectCreated msg) {
            remembered = msg;
        }
    }
}
