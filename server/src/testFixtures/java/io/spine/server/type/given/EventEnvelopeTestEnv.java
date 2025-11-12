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

package io.spine.server.type.given;

import com.google.protobuf.Message;
import io.spine.base.Identifier;
import io.spine.base.Time;
import io.spine.core.ActorContext;
import io.spine.core.CommandContext;
import io.spine.core.Event;
import io.spine.core.EventContext;
import io.spine.core.EventId;
import io.spine.core.Origin;
import io.spine.core.TenantId;
import io.spine.protobuf.AnyPacker;
import io.spine.protobuf.TypeConverter;
import io.spine.test.server.envelope.event.ProjectCreated;
import io.spine.test.server.envelope.ProjectId;
import io.spine.testing.core.given.GivenUserId;

import static io.spine.base.Identifier.newUuid;

public class EventEnvelopeTestEnv {

    /** Prevents instantiation of this utility class. */
    private EventEnvelopeTestEnv() {
    }

    public static Event event(Message eventMessage) {
        return event(eventMessage, GivenEvent.context());
    }

    public static Event event(Message eventMessage, EventContext eventContext) {
        var eventIdBuilder = EventId.newBuilder()
                .setValue(newUuid());
        return Event.newBuilder()
                .setId(eventIdBuilder)
                .setMessage(AnyPacker.pack(eventMessage))
                .setContext(eventContext)
                .build();
    }

    public static CommandContext commandContext() {
        return CommandContext.newBuilder()
                .setActorContext(actorContext())
                .build();
    }

    public static ActorContext actorContext() {
        var tenantId = TenantId.newBuilder()
                .setValue(Identifier.newUuid())
                .build();
        return ActorContext.newBuilder()
                .setTenantId(tenantId)
                .setActor(GivenUserId.newUuid())
                .setTimestamp(Time.currentTime())
                .build();
    }

    public static ProjectCreated eventMessage() {
        var projectId = ProjectId.newBuilder()
                .setId(newUuid())
                .build();
        return ProjectCreated.newBuilder()
                .setProjectId(projectId)
                .build();
    }

    @SuppressWarnings("deprecation") // For backward compatibility.
    public static EventContext eventContext(CommandContext commandContext) {
        return contextBuilder()
                .setCommandContext(commandContext)
                .build();
    }

    @SuppressWarnings("deprecation") // For backward compatibility.
    public static EventContext eventContext(EventContext eventContext) {
        return contextBuilder()
                .setEventContext(eventContext)
                .build();
    }

    public static EventContext eventContext(Origin previousMessage) {
        return contextBuilder()
                .setPastMessage(previousMessage)
                .build();
    }

    public static EventContext eventContext(ActorContext importContext) {
        return contextBuilder()
                .setImportContext(importContext)
                .build();
    }

    private static EventContext.Builder contextBuilder() {
        var producerId = TypeConverter.toAny(GivenUserId.newUuid());
        return EventContext.newBuilder()
                .setTimestamp(Time.currentTime())
                .setProducerId(producerId);
    }
}
