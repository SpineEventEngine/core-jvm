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

package io.spine.server.type;

import com.google.protobuf.Message;
import io.spine.core.Event;
import io.spine.core.EventContext;
import io.spine.core.Origin;
import io.spine.server.type.given.GivenEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static io.spine.server.type.given.EventEnvelopeTestEnv.actorContext;
import static io.spine.server.type.given.EventEnvelopeTestEnv.commandContext;
import static io.spine.server.type.given.EventEnvelopeTestEnv.event;
import static io.spine.server.type.given.EventEnvelopeTestEnv.eventContext;
import static io.spine.server.type.given.EventEnvelopeTestEnv.eventMessage;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("`EventEnvelope` should")
class EventEnvelopeTest extends MessageEnvelopeTest<Event, EventEnvelope, EventClass> {

    @Override
    protected Event outerObject() {
        Message eventMessage = eventMessage();
        var event = event(eventMessage);
        return event;
    }

    @Override
    protected EventEnvelope toEnvelope(Event obj) {
        return EventEnvelope.of(obj);
    }

    @Override
    protected EventClass getMessageClass(Event obj) {
        return EventClass.of(obj);
    }

    @Nested
    @DisplayName("obtain actor context from")
    class ObtainActorContext {

        @Test
        @DisplayName("command context")
        void fromCommandContext() {
            var commandContext = commandContext();
            var context = eventContext(commandContext);
            var envelope = envelope(context);
            assertEquals(commandContext.actorContext(), envelope.actorContext());
        }

        @Test
        @DisplayName("command context of the origin event")
        void fromCommandContextOfOrigin() {
            var commandContext = commandContext();
            var originContext = eventContext(commandContext);
            var context = eventContext(originContext);
            var envelope = envelope(context);
            assertEquals(commandContext.actorContext(), envelope.actorContext());
        }

        @Test
        @DisplayName("origin message info")
        void fromPastMessage() {
            var actor = actorContext();
            var pastMessage = Origin.newBuilder()
                    .setMessage(GivenEvent.arbitrary().messageId())
                    .setActorContext(actor)
                    .buildPartial();
            var context = eventContext(pastMessage);
            var envelope = envelope(context);
            assertEquals(actor, envelope.actorContext());
        }

        @Test
        @DisplayName("import context")
        void fromImportContext() {
            var context = eventContext(actorContext());
            var envelope = envelope(context);
            var expectedImportContext = context.getImportContext();
            assertEquals(expectedImportContext, envelope.actorContext());
        }

        private EventEnvelope envelope(EventContext context) {
            var event = event(eventMessage(), context);
            return toEnvelope(event);
        }
    }
}
