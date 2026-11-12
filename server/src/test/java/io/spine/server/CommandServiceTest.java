/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package io.spine.server;

import io.spine.base.Identifier;
import io.spine.core.Ack;
import io.spine.core.Command;
import io.spine.grpc.MemoizingObserver;
import io.spine.server.given.transport.TestGrpcServer;
import io.spine.test.commandservice.CmdServDontHandle;
import io.spine.test.commandservice.command.CsSuspendProject;
import io.spine.testing.TestValues;
import io.spine.testing.client.TestActorRequestFactory;
import io.spine.testing.logging.mute.MuteLogging;
import io.spine.testing.server.TestApplications;
import io.spine.testing.server.model.ModelTests;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.core.ErrorCase.UNSUPPORTED_REQUEST;
import static io.spine.core.Status.StatusCase.ERROR_CASE;
import static io.spine.grpc.StreamObservers.memoizingObserver;
import static io.spine.protobuf.Messages.isNotDefault;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("`CommandService` should")
class CommandServiceTest {

    private Application application;
    private CommandService service;
    private MemoizingObserver<Ack> responseObserver;

    @BeforeEach
    void setUp() {
        ModelTests.dropAllModels();
        responseObserver = memoizingObserver();
        // Create the Projects and Customers Bounded Contexts with one repository each.
        var projects = BoundedContextBuilder
                .assumingTests("Projects", /* multiTenant = */ true)
                .add(new Given.ProjectAggregateRepository());
        var customers = BoundedContextBuilder
                .assumingTests("Customers", /* multiTenant = */ true)
                .add(new Given.CustomerAggregateRepository());
        application = TestApplications.newBuilder()
                .add(projects)
                .add(customers)
                .build();

        // Expose the two Bounded Contexts via an instance of {@code CommandService}.
        service = CommandService.of(application);
    }

    @AfterEach
    void tearDown() {
        application.close();
    }

    @Test
    @DisplayName("post commands to appropriate bounded context")
    void postCommandsToBc() {
        verifyPostsCommand(Given.ACommand.createProject());
        verifyPostsCommand(Given.ACommand.createCustomer());
    }

    private void verifyPostsCommand(Command cmd) {
        application.tenantIndex()
                   .keep(cmd.tenant());
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
                    .isEqualTo(ERROR_CASE);
            assertThat(status.getErrorCase())
                    .isEqualTo(UNSUPPORTED_REQUEST);
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
                    .isEqualTo(ERROR_CASE);
            assertThat(status.getErrorCase())
                    .isEqualTo(UNSUPPORTED_REQUEST);
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
