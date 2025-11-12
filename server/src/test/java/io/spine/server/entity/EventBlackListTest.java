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

package io.spine.server.entity;

import io.spine.base.EventMessage;
import io.spine.base.Identifier;
import io.spine.core.Event;
import io.spine.test.entity.ProjectId;
import io.spine.test.entity.Task;
import io.spine.test.entity.TaskId;
import io.spine.test.entity.event.EntProjectCreated;
import io.spine.test.entity.event.EntProjectStarted;
import io.spine.test.entity.event.EntTaskAdded;
import io.spine.testing.TestValues;
import io.spine.testing.server.TestEventFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("`EventBlackList` should")
class EventBlackListTest {

    private static final TestEventFactory eventFactory =
            TestEventFactory.newInstance(EventBlackListTest.class);

    private EventBlackList blackList;

    @BeforeEach
    void setUp() {
        blackList = EventBlackList.discardEvents(EntTaskAdded.class, EntProjectStarted.class);
    }

    @Test
    @DisplayName("allow events of type not from the list")
    void allowArbitrary() {
        var event = projectCreated();
        var filtered = blackList.filter(event);
        assertTrue(filtered.isPresent());
        assertEquals(event, filtered.get());
    }

    @Test
    @DisplayName("not allow events of type from the list")
    void notAllowFromList() {
        var event = taskAdded(newProjectId());
        var filtered = blackList.filter(event);
        assertFalse(filtered.isPresent());
    }

    @Test
    @DisplayName("filter out events from bulk")
    void filterOut() {
        var projectCreated = projectCreated();
        var events = Stream.of(projectCreated,
                               taskAdded(projectCreated.getProjectId()))
                           .map(eventFactory::createEvent)
                           .collect(toList());
        Collection<Event> filtered = blackList.filter(events);
        assertEquals(1, filtered.size());
        assertTrue(events.contains(events.get(0)));
    }

    static EntProjectCreated projectCreated() {
        return EntProjectCreated.newBuilder()
                .setProjectId(newProjectId())
                .build();
    }

    static ProjectId newProjectId() {
        return ProjectId.newBuilder()
                .setId(Identifier.newUuid())
                .build();
    }

    static EventMessage taskAdded(ProjectId projectId) {
        return EntTaskAdded.newBuilder()
                .setProjectId(projectId)
                .setTask(Task.newBuilder()
                                 .setTaskId(TaskId.newBuilder()
                                                    .setId(TestValues.random(100))))
                .build();
    }
}
