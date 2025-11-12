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

package io.spine.server;

import com.google.common.collect.Sets;
import io.spine.base.Identifier;
import io.spine.core.Ack;
import io.spine.core.Command;
import io.spine.core.CommandValidationError;
import io.spine.grpc.MemoizingObserver;
import io.spine.server.given.transport.TestGrpcServer;
import io.spine.test.commandservice.CmdServDontHandle;
import io.spine.test.commandservice.command.CsSuspendProject;
import io.spine.testing.TestValues;
import io.spine.testing.client.TestActorRequestFactory;
import io.spine.testing.logging.mute.MuteLogging;
import io.spine.testing.server.model.ModelTests;
import io.spine.type.UnpublishedLanguageException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.core.Status.StatusCase.ERROR;
import static io.spine.grpc.StreamObservers.memoizingObserver;
import static io.spine.protobuf.Messages.isNotDefault;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("`CommandService` should")
class CommandServiceTest {

    private CommandService service;

    private final Set<BoundedContext> boundedContexts = Sets.newHashSet();
    private BoundedContext projectsContext;

    private BoundedContext customersContext;
    private final MemoizingObserver<Ack> responseObserver = memoizingObserver();

    @BeforeEach
    void setUp() {
        ModelTests.dropAllModels();
        // Create Projects Bounded Context with one repository.
        projectsContext = BoundedContext.multitenant("Projects").build();
        var projectRepo = new Given.ProjectAggregateRepository();
        projectsContext.register(projectRepo);
        boundedContexts.add(projectsContext);

        // Create Customers Bounded Context with one repository.
        customersContext = BoundedContext.multitenant("Customers").build();
        var customerRepo = new Given.CustomerAggregateRepository();
        customersContext.register(customerRepo);
        boundedContexts.add(customersContext);

        // Expose two Bounded Contexts via an instance of {@code CommandService}.
        var builder = CommandService.newBuilder();
        for (var context : boundedContexts) {
            builder.add(context);
        }
        service = builder.build();
    }

    @AfterEach
    void tearDown() throws Exception {
        for (var boundedContext : boundedContexts) {
            boundedContext.close();
        }
    }

    @Test
    @DisplayName("post commands to appropriate bounded context")
    void postCommandsToBc() {
        verifyPostsCommand(Given.ACommand.createProject());
        verifyPostsCommand(Given.ACommand.createCustomer());
    }

    @Test
    @DisplayName("never retrieve removed bounded contexts from builder")
    void notRetrieveRemovedBc() {
        var builder = CommandService.newBuilder()
                .add(projectsContext)
                .add(customersContext)
                .remove(projectsContext);

        // Create BoundedContext map.
        var service = builder.build();
        assertNotNull(service);

        assertTrue(builder.contains(customersContext));
        assertFalse(builder.contains(projectsContext));
    }

    private void verifyPostsCommand(Command cmd) {
        MemoizingObserver<Ack> observer = memoizingObserver();
        service.post(cmd, observer);

        assertNull(observer.getError());
        assertTrue(observer.isCompleted());
        var acked = observer.firstResponse();
        var messageId = acked.getMessageId();
        assertThat(Identifier.unpack(messageId))
                .isEqualTo(cmd.getId());
    }

    @Nested
    @DisplayName("return error status if command is")
    class ErrorStatus {

        private final TestActorRequestFactory factory = new TestActorRequestFactory(getClass());

        @Test
        @DisplayName("not supported")
        @MuteLogging
        void returnCommandUnsupportedError() {
            var unsupportedCmd = factory.createCommand(CmdServDontHandle.getDefaultInstance());

            service.post(unsupportedCmd, responseObserver);

            assertTrue(responseObserver.isCompleted());
            var result = responseObserver.firstResponse();
            assertNotNull(result);
            assertTrue(isNotDefault(result));
            var status = result.getStatus();
            assertThat(status.getStatusCase())
                    .isEqualTo(ERROR);
            var error = status.getError();
            assertThat(error.getType())
                    .isEqualTo(CommandValidationError.getDescriptor().getFullName());
        }

        @Test
        @DisplayName("marked as `internal_type`")
        @MuteLogging
        void internalCommand() {
            var internalCommand = factory.createCommand(CsSuspendProject.getDefaultInstance());

            service.post(internalCommand, responseObserver);

            assertTrue(responseObserver.isCompleted());
            var result = responseObserver.firstResponse();
            assertNotNull(result);
            assertTrue(isNotDefault(result));
            var status = result.getStatus();
            assertThat(status.getStatusCase())
                    .isEqualTo(ERROR);
            var error = status.getError();
            assertThat(error.getType())
                    .isEqualTo(UnpublishedLanguageException.class.getName());
        }
    }

    @Test
    @DisplayName("deploy to gRPC container")
    void deployToGrpcContainer() throws IOException {
        var grpcContainer = GrpcContainer.inProcess(TestValues.randomString())
                                         .addService(service)
                                         .build();
        grpcContainer.injectServer(new TestGrpcServer());
        assertTrue(grpcContainer.isScheduledForDeployment(service));

        grpcContainer.start();
        assertTrue(grpcContainer.isLive(service));

        grpcContainer.shutdown();
        assertFalse(grpcContainer.isLive(service));
    }
}
