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
import io.spine.core.Command;
import io.spine.core.TenantId;
import io.spine.server.type.CommandEnvelope;
import io.spine.system.server.MemoizingWriteSide;
import io.spine.system.server.WriteSideFunction;
import io.spine.system.server.event.CommandReceived;
import io.spine.test.command.CmdCreateProject;
import io.spine.test.command.ProjectId;
import io.spine.testing.client.TestActorRequestFactory;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static io.spine.base.Identifier.newUuid;
import static org.junit.jupiter.api.Assertions.assertFalse;

@DisplayName("`CommandReceivedTap` should")
class CommandReceivedTapTest {

    private MemoizingWriteSide writeSide;
    private CommandReceivedTap filter;

    @BeforeEach
    void setUp() {
        initSingleTenant();
    }

    private void initSingleTenant() {
        writeSide = MemoizingWriteSide.singleTenant();
        filter = new CommandReceivedTap(systemFn());
    }

    private void initMultiTenant() {
        writeSide = MemoizingWriteSide.multiTenant();
        filter = new CommandReceivedTap(systemFn());
    }

    private WriteSideFunction systemFn() {
        return WriteSideFunction.delegatingTo(writeSide);
    }

    @Test
    @DisplayName("post `MarkCommandAsReceived` on command")
    void postSystemCommand() {
        var command = command(commandMessage(), null);
        postAndCheck(command);
    }

    @Test
    @DisplayName("post `MarkCommandAsReceived` to specific tenant")
    void postIfMultiTenant() {
        initMultiTenant();

        var expectedTenant = tenantId();
        var command = command(commandMessage(), expectedTenant);
        postAndCheck(command);

        var actualTenant = writeSide.lastSeenEvent().tenant();
        assertThat(actualTenant)
                .isEqualTo(expectedTenant);
    }

    private void postAndCheck(Command command) {
        var envelope = CommandEnvelope.of(command);

        Optional<?> ack = filter.filter(envelope);
        assertFalse(ack.isPresent());

        var systemEvent = (CommandReceived) writeSide.lastSeenEvent().message();
        assertThat(systemEvent.getId())
                .isEqualTo(envelope.id());
    }

    private static TenantId tenantId() {
        var tenant = TenantId.newBuilder()
                .setValue(CommandReceivedTapTest.class.getSimpleName())
                .build();
        return tenant;
    }

    private static Command command(CommandMessage message, @Nullable TenantId tenantId) {
        var requestFactory =
                tenantId == null
                ? new TestActorRequestFactory(CommandReceivedTapTest.class)
                : new TestActorRequestFactory(CommandReceivedTapTest.class, tenantId);
        var command = requestFactory.createCommand(message);
        return command;
    }

    private static CommandMessage commandMessage() {
        return CmdCreateProject.newBuilder()
                .setProjectId(ProjectId.newBuilder().setId(newUuid()))
                .build();
    }
}
