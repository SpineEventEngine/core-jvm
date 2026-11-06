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

import com.google.common.collect.ImmutableSet;
import com.google.common.testing.NullPointerTester;
import io.spine.core.Ack;
import io.spine.core.Command;
import io.spine.core.CommandId;
import io.spine.core.ErrorCase;
import io.spine.core.TenantId;
import io.spine.system.server.MemoizingWriteSide;
import io.spine.system.server.NoOpSystemWriteSide;
import io.spine.system.server.event.CommandAcknowledged;
import io.spine.test.commandbus.ProjectId;
import io.spine.test.commandbus.command.CmdBusStartProject;
import io.spine.testing.client.TestActorRequestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.google.common.testing.NullPointerTester.Visibility.PACKAGE;
import static com.google.common.truth.Truth.assertThat;
import static io.spine.base.Identifier.newUuid;
import static io.spine.server.bus.MessageIdExtensions.acknowledge;
import static io.spine.server.bus.MessageIdExtensions.causedError;
import static io.spine.testing.Assertions.assertIllegalState;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SuppressWarnings("InnerClassMayBeStatic")
@DisplayName("`CommandAckMonitor` should")
class CommandAckMonitorTest {

    private CommandId commandId;
    private Command mockCommand;

    @BeforeEach
    void setUp() {
        var requests = new TestActorRequestFactory(CommandAckMonitorTest.class);
        var command = CmdBusStartProject.newBuilder()
                .setProjectId(ProjectId.newBuilder().setId(newUuid()))
                .build();
        mockCommand = requests.command().create(command);
        commandId = mockCommand.id();
    }

    @Test
    @DisplayName("not accept null arguments in `Builder`")
    void rejectNulls() {
        new NullPointerTester()
                .testInstanceMethods(CommandAckMonitor.newBuilder(), PACKAGE);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored") // Builder method invocations.
    @Nested
    @DisplayName("not build without")
    class RequireParameter {

        private CommandAckMonitor.Builder builder;

        @BeforeEach
        void setUp() {
            builder = CommandAckMonitor.newBuilder();
        }

        @Test
        @DisplayName("delegate `StreamObserver`")
        void delegate() {
            builder.setTenantId(TenantId.getDefaultInstance())
                   .setSystemWriteSide(NoOpSystemWriteSide.INSTANCE);
            assertFailsToBuild();
        }

        @Test
        @DisplayName("tenant ID")
        void tenant() {
            builder.setSystemWriteSide(NoOpSystemWriteSide.INSTANCE);
            assertFailsToBuild();
        }

        @Test
        @DisplayName("system write side")
        void system() {
            builder.setTenantId(TenantId.getDefaultInstance());
            assertFailsToBuild();
        }

        private void assertFailsToBuild() {
            assertThrows(NullPointerException.class, builder::build);
        }
    }

    @Nested
    @DisplayName("if `Ack` contains")
    class PostSystemEvent {

        private CommandAckMonitor monitor;
        private MemoizingWriteSide writeSide;

        @BeforeEach
        void setUp() {
            writeSide = MemoizingWriteSide.singleTenant();
            monitor = CommandAckMonitor.newBuilder()
                    .setSystemWriteSide(writeSide)
                    .setTenantId(TenantId.getDefaultInstance())
                    .setPostedCommands(ImmutableSet.of(mockCommand))
                    .build();
        }

        @Test
        @DisplayName("OK marker, post `CommandAcknowledged`")
        void onOk() {
            var ack = okAck(commandId);
            monitor.onNext(ack);

            var lastSeenEvent = writeSide.lastSeenEvent().message();

            assertThat(lastSeenEvent)
                    .isInstanceOf(CommandAcknowledged.class);

            var actualEvent = (CommandAcknowledged) lastSeenEvent;
            assertThat(actualEvent.getId())
                    .isEqualTo(commandId);
        }

        @Test
        @DisplayName("error case, post nothing")
        void onError() {
            var ack = errorAck(commandId);
            monitor.onNext(ack);

            assertFalse(writeSide.hasSeenEvents());
        }
    }

    @Test
    @DisplayName("re-throw errors passed to `onError` as `IllegalStateException`")
    void rethrowOnError() {
        var writeSide = MemoizingWriteSide.singleTenant();
        var monitor = CommandAckMonitor.newBuilder()
                .setSystemWriteSide(writeSide)
                .setTenantId(TenantId.getDefaultInstance())
                .setPostedCommands(ImmutableSet.of(mockCommand))
                .build();
        var error = new RuntimeException("The command Ack monitor test error.");
        var exception = assertIllegalState(() -> monitor.onError(error));
        assertThat(exception)
             .hasCauseThat()
             .isEqualTo(error);
    }

    private static Ack okAck(CommandId commandId) {
        return acknowledge(commandId);
    }

    private static Ack errorAck(CommandId commandId) {
        return causedError(commandId, ErrorCase.INVALID_REQUEST);
    }
}
