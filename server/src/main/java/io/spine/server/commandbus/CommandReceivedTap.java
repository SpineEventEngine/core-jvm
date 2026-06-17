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

import io.spine.core.Ack;
import io.spine.core.Command;
import io.spine.server.type.CommandEnvelope;
import io.spine.system.server.WriteSideFunction;
import io.spine.system.server.event.CommandReceived;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A {@link CommandBus} filter that watches the commands as they appear in the bus.
 *
 * <p>The filter notifies the {@link io.spine.system.server.SystemContext SystemContext} about
 * new commands with a {@link CommandReceived} system event.
 *
 * <p>The filter never terminates the command processing, i.e. {@link #filter(CommandEnvelope)}
 * always returns an empty value.
 */
final class CommandReceivedTap implements CommandFilter {

    private final WriteSideFunction writeSideFunction;

    CommandReceivedTap(WriteSideFunction function) {
        this.writeSideFunction = checkNotNull(function);
    }

    @Override
    public Optional<Ack> filter(CommandEnvelope envelope) {
        var systemEvent = systemEvent(envelope.command());
        var tenantId = envelope.tenantId();
        var writeSide = writeSideFunction.get(tenantId);
        writeSide.postEvent(systemEvent, envelope.asMessageOrigin());
        return letPass();
    }

    private static CommandReceived systemEvent(Command domainCommand) {
        var result = CommandReceived.newBuilder()
                .setId(domainCommand.getId())
                .setPayload(domainCommand)
                .build();
        return result;
    }
}
