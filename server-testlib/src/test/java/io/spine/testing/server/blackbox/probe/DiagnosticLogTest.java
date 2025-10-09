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

package io.spine.testing.server.blackbox.probe;

import io.spine.base.Identifier;
import io.spine.core.CommandId;
import io.spine.core.EventId;
import io.spine.core.MessageId;
import io.spine.system.server.AggregateHistoryCorrupted;
import io.spine.system.server.CannotDispatchDuplicateCommand;
import io.spine.system.server.CannotDispatchDuplicateEvent;
import io.spine.system.server.ConstraintViolated;
import io.spine.system.server.RoutingFailed;
import io.spine.testing.server.blackbox.command.BbCreateProject;
import io.spine.testing.server.blackbox.event.BbProjectCreated;
import io.spine.type.TypeUrl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static io.spine.base.Errors.causeOf;
import static io.spine.base.Errors.fromThrowable;
import static io.spine.base.Identifier.newUuid;
import static io.spine.base.Identifier.pack;
import static io.spine.testing.server.blackbox.given.Given.someMessageId;

@DisplayName("`DiagnosticLog` should")
class DiagnosticLogTest extends DiagnosticLoggingTest {

    @Test
    @DisplayName("log `ConstraintViolated` event")
    void acceptConstraintViolated() {
        var entity = entity();
        DiagnosticLog.instance()
                     .on(ConstraintViolated.newBuilder()
                                 .setEntity(entity)
                                 .build());
        assertLogged(Identifier.toString(entity.getId()));
    }

    @Test
    @DisplayName("log `CannotDispatchDuplicateCommand` event")
    void acceptCannotDispatchDuplicateCommand() {
        var command = MessageId.newBuilder()
                .setId(pack(CommandId.generate()))
                .setTypeUrl(TypeUrl.of(BbCreateProject.class).value())
                .build();
        DiagnosticLog.instance()
                     .on(CannotDispatchDuplicateCommand.newBuilder()
                                 .setEntity(entity())
                                 .setDuplicateCommand(command)
                                 .build());
        assertLogged(command.getTypeUrl());
        assertLogged(command.asCommandId().getUuid());
    }

    @Test
    @DisplayName("log `CannotDispatchDuplicateEvent` event")
    void acceptCannotDispatchDuplicateEvent() {
        var event = MessageId.newBuilder()
                .setId(pack(EventId.newBuilder()
                                   .setValue(newUuid())
                                   .build()))
                .setTypeUrl(TypeUrl.of(BbProjectCreated.class).value())
                .build();
        DiagnosticLog.instance()
                     .on(CannotDispatchDuplicateEvent.newBuilder()
                                 .setEntity(entity())
                                 .setDuplicateEvent(event)
                                 .build());
        assertLogged(event.getTypeUrl());
        assertLogged(event.asEventId().getValue());
    }

    @Test
    @DisplayName("log `RoutingFailed` event")
    void acceptRoutingFailed() {
        var error = causeOf(new IllegalStateException("Test exception. Routing is fine."));
        DiagnosticLog.instance()
                     .on(RoutingFailed.newBuilder()
                                 .setError(error)
                                 .setHandledSignal(someMessageId())
                                 .build());
        assertLogged(error.getMessage());
    }

    @Test
    @DisplayName("log `AggregateHistoryCorrupted` event")
    void acceptAggregateHistoryCorrupted() {
        var error =
                fromThrowable(new IllegalStateException("Test exception. Aggregates are fine."));
        var entityId = entity();
        DiagnosticLog.instance()
                     .on(AggregateHistoryCorrupted.newBuilder()
                                 .setEntity(entityId)
                                 .setError(error)
                                 .build());
        assertLogged(Identifier.toString(entityId.getId()));
        assertLogged(error.getMessage());
    }

    @Override
    protected Logger logger() {
        return Logger.getLogger(DiagnosticLog.class.getName());
    }
}
