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

package io.spine.server.aggregate.given;

import com.google.protobuf.Timestamp;
import io.spine.base.Identifier;
import io.spine.core.Event;
import io.spine.test.aggregate.ProjectId;
import io.spine.testing.server.TestEventFactory;

import java.util.List;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.protobuf.util.Timestamps.add;
import static io.spine.base.Time.currentTime;
import static io.spine.protobuf.Durations2.seconds;
import static io.spine.server.aggregate.given.Given.EventMessage.projectCreated;
import static io.spine.server.aggregate.given.Given.EventMessage.taskAdded;

/**
 * Utilities for creating sequences of test {@link Event}s for the journal of an aggregate.
 */
public class StorageRecords {

    /** Prevents instantiation of this utility class. */
    private StorageRecords() {
    }

    /**
     * Returns several events sorted by timestamp ascending.
     * First event's timestamp is the current time.
     *
     * <p>The events are emitted on behalf of the aggregate with the passed identifier.
     */
    public static List<Event> sequenceFor(ProjectId id) {
        return sequenceFor(id, currentTime());
    }

    /**
     * Returns several events sorted by timestamp ascending.
     *
     * <p>The events are emitted on behalf of the aggregate with the passed identifier.
     *
     * @param start
     *         the timestamp of first event.
     */
    public static List<Event> sequenceFor(ProjectId id, Timestamp start) {
        var delta = seconds(10);
        var timestamp2 = add(start, delta);
        var timestamp3 = add(timestamp2, delta);

        var factory = TestEventFactory.newInstance(Identifier.pack(id), Given.class);

        var e1 = factory.createEvent(projectCreated(id, Given.projectName(id)), null, start);
        var e2 = factory.createEvent(taskAdded(id), null, timestamp2);
        var e3 = factory.createEvent(Given.EventMessage.projectStarted(id), null, timestamp3);

        return newArrayList(e1, e2, e3);
    }
}
