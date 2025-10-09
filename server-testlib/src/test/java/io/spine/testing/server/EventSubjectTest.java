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

package io.spine.testing.server;

import com.google.common.truth.Subject;
import io.spine.base.EventMessage;
import io.spine.core.Event;
import io.spine.testing.server.given.entity.TuTaskId;
import io.spine.testing.server.given.entity.event.TuCommentAdded;
import io.spine.testing.server.given.entity.event.TuTaskCreated;
import org.junit.jupiter.api.DisplayName;

import static io.spine.base.Identifier.newUuid;
import static io.spine.testing.server.EventSubject.events;

@DisplayName("`EventSubject` should")
class EventSubjectTest extends EmittedMessageSubjectTest<EventSubject, Event, EventMessage> {

    private static final TestEventFactory events =
            TestEventFactory.newInstance(EventSubjectTest.class);

    @Override
    protected Subject.Factory<EventSubject, Iterable<Event>> subjectFactory() {
        return events();
    }

    @Override
    Event createMessage() {
        var taskId = generateTaskId();
        var event = TuCommentAdded.newBuilder()
                .setId(taskId)
                .build();
        return newEvent(event);
    }

    @Override
    Event createAnotherMessage() {
        var taskId = generateTaskId();
        var event = TuTaskCreated.newBuilder()
                .setId(taskId)
                .build();
        return newEvent(event);
    }

    private static Event newEvent(EventMessage msg) {
        return events.createEvent(msg);
    }

    private static TuTaskId generateTaskId() {
        return TuTaskId.newBuilder()
                .setValue(newUuid())
                .build();
    }
}
