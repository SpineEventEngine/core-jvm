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

import io.spine.core.ActorContext;
import io.spine.core.CommandContext;
import io.spine.core.Event;
import io.spine.core.EventContext;
import io.spine.core.TenantId;
import io.spine.testing.core.given.GivenUserId;

import static io.spine.base.Time.currentTime;
import static io.spine.protobuf.AnyPacker.pack;
import static io.spine.server.type.given.GivenEvent.message;

public class EventsTestEnv {

    /** Prevents instantiation of this utility class. */
    private EventsTestEnv() {
    }

    public static CommandContext commandContext(TenantId id) {
        var actorContext = ActorContext.newBuilder()
                .setTenantId(id)
                .setActor(GivenUserId.newUuid())
                .setTimestamp(currentTime())
                .build();
        var result = CommandContext.newBuilder()
                .setActorContext(actorContext)
                .build();
        return result;
    }

    public static TenantId tenantId() {
        var value = EventsTestEnv.class.getName();
        return TenantId.newBuilder()
                .setValue(value)
                .build();
    }

    public static Event event(EventContext context) {
        return Event.newBuilder()
                .setId(GivenEvent.someId())
                .setMessage(pack(message()))
                .setContext(context)
                .build();
    }
}
