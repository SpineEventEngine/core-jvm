/*
 * Copyright 2026, TeamDev. All rights reserved.
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

package io.spine.server.delivery;

import io.spine.server.type.EventEnvelope;

/**
 * The part of {@link Inbox} responsible for processing incoming
 * {@link io.spine.server.type.EventEnvelope events}.
 *
 * @param <I>
 *         the type of identifier of inbox target entities
 */
final class InboxOfEvents<I> extends InboxPart<I, EventEnvelope> {

    InboxOfEvents(Inbox.Builder<I> builder) {
        super(builder, builder.eventEndpoints());
    }

    @Override
    protected void setRecordPayload(EventEnvelope envelope, InboxMessage.Builder builder) {
        var event = envelope.outerObject();
        builder.setEvent(event);
    }

    @Override
    protected String extractUuidFrom(EventEnvelope envelope) {
        return envelope.id()
                       .getValue();
    }

    @Override
    protected EventEnvelope asEnvelope(InboxMessage message) {
        return EventEnvelope.of(message.getEvent());
    }

    @Override
    protected InboxMessageStatus determineStatus(EventEnvelope message, InboxLabel label) {
        if (label == InboxLabel.CATCH_UP) {
            return InboxMessageStatus.TO_CATCH_UP;
        }
        return super.determineStatus(message, label);
    }
}
