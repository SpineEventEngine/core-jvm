/*
 * Copyright 2025, TeamDev. All rights reserved.
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

package io.spine.server.commandbus.given.caffetteria;

import io.spine.core.Ack;
import io.spine.server.commandbus.CommandFilter;
import io.spine.server.type.CommandEnvelope;
import io.spine.test.commandbus.command.CmdBusAllocateTable;
import io.spine.test.commandbus.command.CmdBusEntryDenied;

import java.util.Optional;

import static io.spine.protobuf.AnyPacker.unpack;

public final class BeachCustomerFilter implements CommandFilter {

    @Override
    public Optional<Ack> filter(CommandEnvelope envelope) {
        var command =
                unpack(envelope.command()
                               .getMessage(), CmdBusAllocateTable.class);
        var withOwnFood = command.getVisitors()
                                 .getBringOwnFood();
        if (!withOwnFood) {
            return letPass();
        }
        var rejection = CmdBusEntryDenied.newBuilder()
                .setId(command.getCaffetteria())
                .setVisitorCount(command.getVisitors()
                                        .getCount())
                .setReason("This caffetteria doesn't serve clients who bring their own food.")
                .build();
        return reject(envelope, rejection);
    }
}
