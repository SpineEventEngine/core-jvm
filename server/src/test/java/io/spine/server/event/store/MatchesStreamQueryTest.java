/*
 * Copyright 2023, TeamDev. All rights reserved.
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

package io.spine.server.event.store;

import com.google.common.testing.NullPointerTester;
import com.google.protobuf.Message;
import io.spine.base.FieldFilter;
import io.spine.core.Event;
import io.spine.protobuf.AnyPacker;
import io.spine.server.event.EventFilter;
import io.spine.server.event.EventStreamQuery;
import io.spine.test.event.ProjectCreated;
import io.spine.test.event.ProjectId;
import io.spine.testing.server.TestEventFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.spine.base.Identifier.newUuid;
import static io.spine.testing.DisplayNames.NOT_ACCEPT_NULLS;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("`MatchesStreamQuery` should")
class MatchesStreamQueryTest {

    private static final String FIELD_NAME = "spine.test.event.ProjectCreated.project_id";

    private static final TestEventFactory eventFactory =
            TestEventFactory.newInstance(MatchesStreamQueryTest.class);

    @Test
    @DisplayName(NOT_ACCEPT_NULLS)
    void passNullToleranceCheck() {
        var predicate = new MatchesStreamQuery(EventStreamQuery.getDefaultInstance());

        new NullPointerTester()
                .setDefault(Event.class, Event.getDefaultInstance())
                .testAllPublicInstanceMethods(predicate);
    }

    @Test
    @DisplayName("match proper records")
    void matchProperRecords() {
        var properValue = generate();
        var event = eventWith(properValue);
        var predicate = queryWith(FIELD_NAME, properValue);
        assertTrue(predicate.test(event));
    }

    @Test
    @DisplayName("not match improper records")
    void notMatchImproperRecords() {
        var properField = generate();
        var wrongValue = ProjectId.newBuilder()
                .setId("some-random-project")
                .build();
        var event = eventWith(wrongValue);
        var predicate = queryWith(FIELD_NAME, properField);
        assertFalse(predicate.test(event));
    }

    private static MatchesStreamQuery queryWith(String fieldPath, Message field) {
        var filter = FieldFilter.newBuilder()
                .setFieldPath(fieldPath)
                .addValue(AnyPacker.pack(field))
                .build();
        var eventFilter = EventFilter.newBuilder()
                .addEventFieldFilter(filter)
                .build();
        var query = EventStreamQuery.newBuilder()
                .addFilter(eventFilter)
                .build();
        var predicate = new MatchesStreamQuery(query);
        return predicate;
    }

    private static Event eventWith(ProjectId fieldValue) {
        var eventMsg = eventMessage(fieldValue);
        return eventFactory.createEvent(eventMsg);
    }

    private static ProjectCreated eventMessage(ProjectId value) {
        return ProjectCreated.newBuilder()
                .setProjectId(value)
                .build();
    }

    private static ProjectId generate() {
        return ProjectId.newBuilder()
                .setId(newUuid())
                .build();
    }
}
