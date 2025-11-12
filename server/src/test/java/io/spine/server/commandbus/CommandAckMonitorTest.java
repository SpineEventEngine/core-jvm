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

package io.spine.server.commandbus;

import com.google.common.collect.ImmutableSet;
import com.google.common.testing.NullPointerTester;
import io.spine.base.Error;
import io.spine.base.Identifier;
import io.spine.core.Ack;
import io.spine.core.Command;
import io.spine.core.CommandId;
import io.spine.core.TenantId;
import io.spine.server.entity.rejection.CannotModifyArchivedEntity;
import io.spine.server.event.RejectionFactory;
import io.spine.system.server.MemoizingWriteSide;
import io.spine.system.server.NoOpSystemWriteSide;
import io.spine.system.server.event.CommandAcknowledged;
import io.spine.system.server.event.CommandErrored;
import io.spine.system.server.event.CommandRejected;
import io.spine.test.commandbus.ProjectId;
import io.spine.test.commandbus.command.CmdBusStartProject;
import io.spine.testing.client.TestActorRequestFactory;
import io.spine.testing.core.given.GivenCommandContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.google.common.testing.NullPointerTester.Visibility.PACKAGE;
import static com.google.common.truth.Truth.assertThat;
import static io.spine.base.Identifier.newUuid;
import static io.spine.protobuf.AnyPacker.pack;
import static io.spine.server.bus.MessageIdExtensions.acknowledge;
import static io.spine.server.bus.MessageIdExtensions.causedError;
import static io.spine.server.bus.MessageIdExtensions.reject;
import static io.spine.testing.Assertions.assertIllegalState;
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
        @DisplayName("error, post `CommandErrored`")
        void onError() {
            var ack = errorAck(commandId);
            monitor.onNext(ack);

            var lastSeenEvent = writeSide.lastSeenEvent().message();

            assertThat(lastSeenEvent)
                    .isInstanceOf(CommandErrored.class);

            var actualEvent = (CommandErrored) lastSeenEvent;

            assertThat(actualEvent.getId())
                    .isEqualTo(commandId);
            assertThat(actualEvent.getError())
                    .isEqualTo(ack.getStatus().getError());
        }

        @Test
        @DisplayName("rejection, post `CommandRejected`")
        void onRejection() {
            var ack = rejectionAck(commandId);
            monitor.onNext(ack);

            var lastSeenEvent = writeSide.lastSeenEvent().message();
            assertThat(lastSeenEvent).isInstanceOf(CommandRejected.class);

            var actualEvent = (CommandRejected) lastSeenEvent;
            assertThat(actualEvent.getId())
                    .isEqualTo(commandId);
            assertThat(actualEvent.getRejectionEvent())
                    .isEqualTo(ack.getStatus().getRejection());
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
        var error = Error.newBuilder()
                .setCode(42)
                .setMessage("Wrong question")
                .build();
        return causedError(commandId, error);
    }

    private static Ack rejectionAck(CommandId commandId) {
        var projectId = ProjectId.newBuilder()
                .setId(commandId.getUuid())
                .build();
        var commandMessage = CmdBusStartProject.newBuilder()
                .setProjectId(projectId)
                .build();
        var command = Command.newBuilder()
                .setId(commandId)
                .setMessage(pack(commandMessage))
                .setContext(GivenCommandContext.withRandomActor())
                .build();

        var exception = throwableCausedByRejection();
        var rejection = RejectionFactory.reject(command, exception);
        return reject(commandId, rejection);
    }

    private static RuntimeException throwableCausedByRejection() {
        var entityId = Identifier.pack(CommandAckMonitorTest.class.getSimpleName());
        var rejection = CannotModifyArchivedEntity.newBuilder()
                .setEntityId(entityId)
                .build();
        return new RuntimeException(rejection);
    }
}
