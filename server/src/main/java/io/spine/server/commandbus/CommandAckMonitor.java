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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.grpc.stub.StreamObserver;
import io.spine.base.EventMessage;
import io.spine.core.Ack;
import io.spine.core.Command;
import io.spine.core.CommandId;
import io.spine.core.Status;
import io.spine.core.TenantId;
import io.spine.server.type.CommandEnvelope;
import io.spine.system.server.SystemWriteSide;
import io.spine.system.server.event.CommandAcknowledged;
import io.spine.system.server.event.CommandErrored;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static io.spine.core.Acks.toCommandId;
import static io.spine.core.ErrorCases.toError;
import static io.spine.system.server.WriteSideFunction.delegatingTo;
import static io.spine.util.Exceptions.newIllegalArgumentException;

/**
 * A {@link StreamObserver} for {@link io.spine.core.Command Command}
 * {@linkplain Ack acknowledgement}.
 *
 * <p>Posts a system event whenever a command is acknowledged or errored.
 */
final class CommandAckMonitor implements StreamObserver<Ack> {

    private final SystemWriteSide writeSide;
    private final ImmutableMap<CommandId, Command> commands;

    private CommandAckMonitor(Builder builder) {
        this.writeSide = delegatingTo(builder.systemWriteSide).get(builder.tenantId);
        this.commands = builder
                .commands
                .stream()
                .collect(toImmutableMap(Command::id, c -> c));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Posts either {@link CommandAcknowledged} or {@link CommandErrored} system event
     * depending on the value of the given {@code Ack}.
     */
    @Override
    public void onNext(Ack value) {
        postSystemEvent(value);
    }

    @Override
    public void onError(Throwable t) {
        throw new IllegalStateException(t);
    }

    @Override
    public void onCompleted() {
        // NO-OP.
    }

    private void postSystemEvent(Ack ack) {
        var status = ack.getStatus();
        var commandId = toCommandId(ack);
        var systemEvent = systemEventFor(status, commandId);
        var command = commands.get(commandId);
        checkState(command != null, "Unknown command ID encountered: `%s`.", commandId.value());
        var systemEventOrigin = CommandEnvelope.of(command).asMessageOrigin();
        writeSide.postEvent(systemEvent, systemEventOrigin);
    }

    @SuppressWarnings("EnumSwitchStatementWhichMissesCases") // Default values.
    private static EventMessage systemEventFor(Status status, CommandId commandId) {
        return switch (status.getStatusCase()) {
            case OK -> CommandAcknowledged.newBuilder()
                    .setId(commandId)
                    .build();
            case ERROR -> CommandErrored.newBuilder()
                    .setId(commandId)
                    .setError(status.getError())
                    .build();
            case ERROR_CASE -> erroredWithCase(status, commandId);
            default -> throw newIllegalArgumentException(
                    "Command `%s` has invalid status `%s`.",
                    commandId.getUuid(),
                    status.getStatusCase()
            );
        };
    }

    /**
     * Creates {@link CommandErrored} for the given status with an error case.
     *
     * <p>Such a status carries no error, so the event gets an error made of the error case.
     */
    private static CommandErrored erroredWithCase(Status status, CommandId commandId) {
        var errorCase = status.getErrorCase();
        return CommandErrored.newBuilder()
                .setId(commandId)
                .setError(toError(errorCase))
                .setErrorCase(errorCase)
                .build();
    }

    /**
     * Creates a new instance of {@code Builder} for {@code CommandAckMonitor} instances.
     *
     * @return new instance of {@code Builder}
     */
    static Builder newBuilder() {
        return new Builder();
    }

    /**
     * A builder for the {@code CommandAckMonitor} instances.
     */
    static final class Builder {

        private TenantId tenantId;
        private SystemWriteSide systemWriteSide;
        private ImmutableSet<Command> commands;

        /**
         * Prevents direct instantiation.
         */
        private Builder() {
        }

        /**
         * Sets the ID of a tenant who owns the observed commands.
         */
        @CanIgnoreReturnValue
        Builder setTenantId(TenantId tenantId) {
            this.tenantId = checkNotNull(tenantId);
            return this;
        }

        /**
         * Sets the {@link SystemWriteSide} to post system commands into.
         */
        @CanIgnoreReturnValue
        Builder setSystemWriteSide(SystemWriteSide systemWriteSide) {
            this.systemWriteSide = checkNotNull(systemWriteSide);
            return this;
        }

        /**
         * Sets the commands being posted into the command bus.
         *
         * <p>The resulting monitor is able to observe only outcomes of these commands.
         */
        @CanIgnoreReturnValue
        Builder setPostedCommands(ImmutableSet<Command> commands) {
            this.commands = checkNotNull(commands);
            return this;
        }

        /**
         * Creates a new instance of {@code CommandAckMonitor}.
         *
         * @return new instance of {@code CommandAckMonitor}
         */
        CommandAckMonitor build() {
            checkNotNull(tenantId);
            checkNotNull(systemWriteSide);
            checkNotNull(commands);

            return new CommandAckMonitor(this);
        }
    }
}
