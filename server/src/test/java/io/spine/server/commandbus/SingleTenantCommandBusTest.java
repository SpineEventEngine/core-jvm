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

package io.spine.server.commandbus;

import io.spine.base.CommandMessage;
import io.spine.core.Ack;
import io.spine.core.Command;
import io.spine.grpc.MemoizingObserver;
import io.spine.server.commandbus.given.SingleTenantCommandBusTestEnv.FaultyAssignee;
import io.spine.testing.client.TestActorRequestFactory;
import io.spine.testing.logging.mute.MuteLogging;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static io.spine.core.ErrorCase.INVALID_REQUEST;
import static io.spine.core.ErrorCase.TENANT_ERROR;
import static io.spine.grpc.StreamObservers.memoizingObserver;
import static io.spine.server.commandbus.Given.ACommand.createProject;
import static io.spine.server.commandbus.Given.ACommand.removeTask;
import static io.spine.server.tenant.TenantAwareOperation.isTenantSet;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Single-tenant `CommandBus` should")
class SingleTenantCommandBusTest extends AbstractCommandBusTestSuite {

    private static final TestActorRequestFactory requestFactory =
            new TestActorRequestFactory(SingleTenantCommandBusTest.class);

    SingleTenantCommandBusTest() {
        super(false);
    }

    @Override
    @BeforeEach
    public void setUp() {
        super.setUp();
        commandBus.register(createProjectAssignee);
    }

    @Test
    @DisplayName("post command and do not set current tenant")
    void postCommandWithoutTenant() {
        commandBus.post(newCommandWithoutTenantId(), observer);

        assertFalse(isTenantSet());
    }

    @Nested
    @DisplayName("reject")
    class Reject {

        @Test
        @DisplayName("invalid command")
        void invalidCmd() {
            var cmd = newCommandWithoutContext();

            commandBus.post(cmd, observer);

            checkCommandError(observer.firstResponse(), INVALID_REQUEST, cmd);
        }

        @Test
        @DisplayName("multi-tenant command in single tenant context")
        void multiTenantCmdIfSingleTenant() {
            // Create a multi-tenant command.
            var cmd = createProject();

            commandBus.post(cmd, observer);

            checkCommandError(observer.firstResponse(), TENANT_ERROR, cmd);
        }
    }

    @MuteLogging
    @Test
    @DisplayName("do not propagate dispatching errors")
    void doNotPropagateExceptions() {
        var faultyAssignee = FaultyAssignee.initializedAssignee();
        commandBus.register(faultyAssignee);

        var remoteTaskCommand = clearTenantId(removeTask());
        MemoizingObserver<Ack> observer = memoizingObserver();
        commandBus.post(remoteTaskCommand, observer);

        var ack = observer.firstResponse();
        assertTrue(ack.getStatus()
                      .hasOk());
    }

    @Test
    @DisplayName("create validator once")
    void createValidatorOnce() {
        var validator = commandBus.validator();
        assertNotNull(validator);
        assertSame(validator, commandBus.validator());
    }

    @Override
    protected Command newCommand() {
        CommandMessage commandMessage = Given.CommandMessage.createProjectMessage();
        return requestFactory.createCommand(commandMessage);
    }
}
