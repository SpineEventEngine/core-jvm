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

import io.spine.base.EventMessage;
import io.spine.core.Enrichment;
import io.spine.core.Event;
import io.spine.core.EventContext;
import io.spine.core.EventId;
import io.spine.core.Version;
import io.spine.core.Versions;
import io.spine.test.core.given.EtProjectCreated;
import io.spine.testing.server.TestEventFactory;

import static io.spine.base.Identifier.newUuid;
import static io.spine.base.Time.currentTime;
import static io.spine.protobuf.TypeConverter.toAny;
import static io.spine.testing.TestValues.random;
import static io.spine.testing.time.Past.minutesAgo;

public final class GivenEvent {

    public static final TestEventFactory eventFactory =
            TestEventFactory.newInstance(toAny(GivenEvent.class.getSimpleName()),
                                         GivenEvent.class);

    /** Prevent instantiation of this utility class. */
    private GivenEvent() {
    }

    public static EventContext context() {
        var event = eventFactory.createEvent(message(), someVersion());
        return event.context();
    }

    public static Event occurredMinutesAgo(int minutesAgo) {
        var result = eventFactory.createEvent(message(), someVersion(), minutesAgo(minutesAgo));
        return result;
    }

    public static Event withMessage(EventMessage message) {
        var event = eventFactory.createEvent(message, someVersion());
        return event;
    }

    public static Event withMessageAndVersion(EventMessage message, Version version) {
        var event = eventFactory.createEvent(message, version);
        return event;
    }

    private static Version someVersion() {
        return Versions.newVersion(random(1, 100), currentTime());
    }

    public static Event arbitrary() {
        return withMessage(message());
    }

    public static Event withVersion(Version version) {
        var event = eventFactory.createEvent(message(), version);
        return event;
    }

    public static Event withDisabledEnrichmentOf(EventMessage message) {
        var event = withMessage(message);
        var builder = event.toBuilder()
                .setContext(event.context()
                                    .toBuilder()
                                    .setEnrichment(Enrichment.newBuilder()
                                                           .setDoNotEnrich(true)));
        return builder.build();
    }

    public static EventMessage message() {
        return EtProjectCreated
                .newBuilder()
                .setId(newUuid())
                .build();
    }

    public static EventId someId() {
        var result = EventId
                .newBuilder()
                .setValue(newUuid())
                .build();
        return result;
    }
}
