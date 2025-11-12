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

package io.spine.system.server.given;

import com.google.protobuf.Empty;
import com.google.protobuf.StringValue;
import io.spine.base.EventMessage;
import io.spine.base.Identifier;
import io.spine.core.CommandId;
import io.spine.core.Event;
import io.spine.core.MessageId;
import io.spine.grpc.MemoizingObserver;
import io.spine.server.event.EventBus;
import io.spine.server.event.EventFilter;
import io.spine.server.event.EventStreamQuery;
import io.spine.system.server.EmptyEntityState;
import io.spine.system.server.event.EntityCreated;
import io.spine.system.server.event.EntityStateChanged;
import io.spine.testing.server.TestEventFactory;
import io.spine.type.TypeUrl;

import static io.spine.base.Identifier.newUuid;
import static io.spine.grpc.StreamObservers.memoizingObserver;
import static io.spine.option.EntityOption.Kind.ENTITY;
import static io.spine.protobuf.AnyPacker.pack;

/**
 * Test environment for testing interop between
 * {@link io.spine.system.server.SystemContext SystemContext} and
 * {@link io.spine.system.server.SystemSettings SystemSettings}.
 */
public class SystemContextSettingsTestEnv {

    private static final TestEventFactory events =
            TestEventFactory.newInstance(SystemContextSettingsTestEnv.class);

    private SystemContextSettingsTestEnv() {
    }

    public static MemoizingObserver<Event> postSystemEvent(EventBus systemBus, Event event) {
        systemBus.post(event);
        var filter = EventFilter.newBuilder()
                .setEventType(event.enclosedTypeUrl()
                                   .typeName()
                                   .value())
                .build();
        var query = EventStreamQuery.newBuilder()
                .addFilter(filter)
                .build();
        MemoizingObserver<Event> observer = memoizingObserver();
        systemBus.eventStore()
                 .read(query, observer);
        return observer;
    }

    public static Event entityStateChanged() {
        var eventMessage = EntityStateChanged.newBuilder()
                .setEntity(MessageId.newBuilder()
                                   .setId(Identifier.pack(42))
                                   .setTypeUrl(TypeUrl.of(EmptyEntityState.class)
                                                      .value()))
                .setOldState(pack(StringValue.of("0")))
                .setNewState(pack(StringValue.of("42")))
                .addSignalId(MessageId.newBuilder()
                                     .setId(Identifier.pack(CommandId.generate()))
                                     .setTypeUrl(TypeUrl.of(EntityStateChanged.class)
                                                        .value()))
                .build();
        var event = events.createEvent(eventMessage);
        return event;
    }

    public static EventMessage entityCreated() {
        var messageId = MessageId.newBuilder()
                .setTypeUrl(TypeUrl.of(Empty.class).value())
                .setId(Identifier.pack(newUuid()))
                .build();
        var event = EntityCreated.newBuilder()
                .setEntity(messageId)
                .setKind(ENTITY)
                .build();
        return event;
    }
}
