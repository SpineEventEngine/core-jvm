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

package io.spine.testing.server;

import com.google.errorprone.annotations.CheckReturnValue;
import com.google.protobuf.Any;
import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;
import io.spine.annotation.VisibleForTesting;
import io.spine.base.EventMessage;
import io.spine.base.Identifier;
import io.spine.core.Event;
import io.spine.core.Version;
import io.spine.server.event.EventFactory;
import io.spine.server.event.EventOrigin;
import io.spine.server.type.CommandEnvelope;
import io.spine.server.type.MessageEnvelope;
import io.spine.testing.client.TestActorRequestFactory;
import org.jspecify.annotations.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.protobuf.AnyPacker.pack;

/**
 * The factory or producing events for tests.
 */
@VisibleForTesting
@CheckReturnValue
public class TestEventFactory extends EventFactory {

    private TestEventFactory(MessageEnvelope<?, ?, ?> origin, Any producerId) {
        super(EventOrigin.fromAnotherMessage(origin), producerId);
    }

    private static Any toAny(Message producerId) {
        return producerId instanceof Any
               ? (Any) producerId
               : Identifier.pack(producerId);
    }

    public static TestEventFactory newInstance(Message producerId, Class<?> testSuiteClass) {
        var id = toAny(producerId);
        return newInstance(id, new TestActorRequestFactory(testSuiteClass));
    }

    public static TestEventFactory newInstance(Message producerId,
                                               TestActorRequestFactory requestFactory) {
        checkNotNull(requestFactory);
        var id = toAny(producerId);
        var cmd = CommandEnvelope.of(requestFactory.generateCommand());
        return new TestEventFactory(cmd, id);
    }

    public static TestEventFactory newInstance(TestActorRequestFactory requestFactory) {
        checkNotNull(requestFactory);
        Message producerId = requestFactory.actor();
        return newInstance(pack(producerId), requestFactory);
    }

    public static TestEventFactory newInstance(Class<?> testSuiteClass) {
        checkNotNull(testSuiteClass);
        return newInstance(new TestActorRequestFactory(testSuiteClass));
    }

    /**
     * Creates an event produced at the passed time.
     */
    public Event createEvent(EventMessage message, @Nullable Version version, Timestamp atTime) {
        checkNotNull(message);
        checkNotNull(atTime);
        var event = version != null
                    ? createEvent(message, version)
                    : createEvent(message);
        var context = event.context()
                .toBuilder()
                .setTimestamp(atTime)
                .build();
        var result = event.toBuilder()
                .setContext(context)
                .build();
        return result;
    }
}
