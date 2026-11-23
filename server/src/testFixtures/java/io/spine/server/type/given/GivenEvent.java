/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package io.spine.server.type.given;

import io.spine.base.EventMessage;
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
