/*
 * Copyright 2022, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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

import io.grpc.stub.StreamObserver;
import io.spine.base.Error;
import io.spine.core.Ack;
import io.spine.core.Command;
import io.spine.core.CommandValidationError;
import io.spine.core.Event;
import io.spine.core.EventValidationError;
import io.spine.server.BoundedContext;
import io.spine.server.BoundedContextBuilder;
import io.spine.server.aggregate.given.aggregate.IgTestAggregate;
import io.spine.server.aggregate.given.aggregate.IgTestAggregateRepository;
import io.spine.server.type.CommandEnvelope;
import io.spine.server.type.EventEnvelope;
import io.spine.test.aggregate.ProjectId;
import io.spine.testing.server.model.ModelTests;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.core.CommandValidationError.DUPLICATE_COMMAND_VALUE;
import static io.spine.grpc.StreamObservers.noOpObserver;
import static io.spine.server.aggregate.given.IdempotencyGuardTestEnv.command;
import static io.spine.server.aggregate.given.IdempotencyGuardTestEnv.createProject;
import static io.spine.server.aggregate.given.IdempotencyGuardTestEnv.event;
import static io.spine.server.aggregate.given.IdempotencyGuardTestEnv.projectPaused;
import static io.spine.server.aggregate.given.IdempotencyGuardTestEnv.startProject;
import static io.spine.server.aggregate.given.IdempotencyGuardTestEnv.taskStarted;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("`IdempotencyGuard` should")
class IdempotencyGuardTest {

    private BoundedContext context;
    private IgTestAggregateRepository repository;
    private ProjectId projectId;

    @BeforeEach
    void setUp() {
        ModelTests.dropAllModels();
        context = BoundedContextBuilder.assumingTests().build();
        repository = new IgTestAggregateRepository();
        context.internalAccess()
               .register(repository);
        projectId = ProjectId.generate();
    }

    @AfterEach
    void tearDown() throws Exception {
        repository.close();
        context.close();
        ModelTests.dropAllModels();
    }

    @Nested
    @DisplayName("check commands and")

    class Commands {
        private IgTestAggregate aggregate() {
            return repository.loadAggregate(projectId);
        }

        @Test
        @DisplayName("throw `DuplicateCommandException` if command was handled since last snapshot")
        void throwExceptionForDuplicateCommand() {
            var createCommand = command(createProject(projectId));

            post(createCommand);

            var aggregate = repository.loadAggregate(projectId);
            var guard = new IdempotencyGuard(aggregate);
            var error = check(guard, createCommand);
            assertTrue(error.isPresent());
            var actualError = error.get();
            assertThat(actualError.getType())
                    .isEqualTo(CommandValidationError.class.getSimpleName());
            assertThat(actualError.getCode()).isEqualTo(DUPLICATE_COMMAND_VALUE);
        }

        @Test
        @DisplayName("not throw exception when command was handled but snapshot was made")
        void notThrowForCommandHandledAfterSnapshot() {
            repository.setSnapshotTrigger(1);
            var createCommand = command(createProject(projectId));
            post(createCommand);

            var aggregate = aggregate();

            var guard = new IdempotencyGuard(aggregate);
            var error = check(guard, createCommand);
            assertThat(error).isEmpty();
        }

        @Test
        @DisplayName("not throw exception if command was not handled")
        void notThrowForCommandNotHandled() {
            var createCommand = command(createProject(projectId));
            var aggregate = new IgTestAggregate(projectId);

            var guard = new IdempotencyGuard(aggregate);
            var error = guard.check(CommandEnvelope.of(createCommand));
            assertThat(error).isEmpty();
        }

        @Test
        @DisplayName("not throw exception if another command was handled")
        void notThrowIfAnotherCommandHandled() {
            var createCommand = command(createProject(projectId));
            var startCommand = command(startProject(projectId));

            post(createCommand);

            var aggregate = aggregate();

            var guard = new IdempotencyGuard(aggregate);
            var error = check(guard, startCommand);
            assertThat(error).isEmpty();
        }

        private void post(Command command) {
            var commandBus = context.commandBus();
            StreamObserver<Ack> noOpObserver = noOpObserver();
            commandBus.post(command, noOpObserver);
        }

        private Optional<Error> check(IdempotencyGuard guard, Command command) {
            var envelope = CommandEnvelope.of(command);
            return guard.check(envelope);
        }
    }

    @Nested
    @DisplayName("check events and")
    class Events {

        @BeforeEach
        void setUp() {
            context.commandBus()
                   .post(command(createProject(projectId)), noOpObserver());
        }

        @Test
        @DisplayName("throw `DuplicateEventException` if event was handled since last snapshot")
        void throwExceptionForDuplicateCommand() {
            var event = event(taskStarted(projectId));
            post(event);

            var aggregate = repository.loadAggregate(projectId);
            var guard = new IdempotencyGuard(aggregate);
            var error = check(guard, event);
            assertTrue(error.isPresent());
            var actualError = error.get();
            assertThat(actualError.getType()).isEqualTo(EventValidationError.class.getSimpleName());
        }

        @Test
        @DisplayName("not throw exception when event was handled but snapshot was made")
        void notThrowForCommandHandledAfterSnapshot() {
            repository.setSnapshotTrigger(1);

            var event = event(taskStarted(projectId));
            post(event);

            var aggregate = repository.loadAggregate(projectId);
            var guard = new IdempotencyGuard(aggregate);
            var error = check(guard, event);
            assertThat(error).isEmpty();
        }

        @Test
        @DisplayName("not throw exception if event was not handled")
        void notThrowForCommandNotHandled() {
            var event = event(taskStarted(projectId));
            var aggregate = new IgTestAggregate(projectId);

            var guard = new IdempotencyGuard(aggregate);
            var error = check(guard, event);
            assertThat(error).isEmpty();
        }

        @Test
        @DisplayName("not throw exception if another event was handled")
        void notThrowIfAnotherCommandHandled() {
            var taskEvent = event(taskStarted(projectId));
            var projectEvent = event(projectPaused(projectId));

            var eventBus = context.eventBus();
            eventBus.post(taskEvent);

            var aggregate = repository.loadAggregate(projectId);

            var guard = new IdempotencyGuard(aggregate);
            var error = check(guard, projectEvent);
            assertThat(error).isEmpty();
        }

        private void post(Event event) {
            context.eventBus()
                   .post(event);
        }

        private Optional<Error> check(IdempotencyGuard guard, Event event) {
            var envelope = EventEnvelope.of(event);
            return guard.check(envelope);
        }
    }
}
