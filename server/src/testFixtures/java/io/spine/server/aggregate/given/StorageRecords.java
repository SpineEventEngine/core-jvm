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

package io.spine.server.aggregate.given;

import com.google.protobuf.Timestamp;
import io.spine.base.EventMessage;
import io.spine.base.Identifier;
import io.spine.core.Event;
import io.spine.core.EventId;
import io.spine.server.aggregate.AggregateEventRecord;
import io.spine.server.aggregate.AggregateEventRecordId;
import io.spine.test.aggregate.ProjectId;
import io.spine.test.aggregate.event.AggProjectCreated;
import io.spine.testdata.Sample;
import io.spine.testing.server.TestEventFactory;

import java.util.List;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.protobuf.util.Timestamps.add;
import static io.spine.base.Time.currentTime;
import static io.spine.protobuf.Durations2.seconds;
import static io.spine.server.aggregate.given.Given.EventMessage.projectCreated;
import static io.spine.server.aggregate.given.Given.EventMessage.taskAdded;

/**
 * Utilities for creating test instances and sequences of {@link AggregateEventRecord}.
 */
public class StorageRecords {

    private static final TestEventFactory eventFactory = TestEventFactory.newInstance(Given.class);

    /** Prevents instantiation of this utility class. */
    private StorageRecords() {
    }

    /** Creates new builder for an aggregate event record and sets the passed timestamp. */
    private static <I> AggregateEventRecord.Builder
    newRecordWith(EventId eventId, I aggregateId, Timestamp timestamp) {
        var recordId = AggregateEventRecordId.newBuilder()
                .setValue(eventId.getValue())
                .build();
        return AggregateEventRecord.newBuilder()
                .setId(recordId)
                .setAggregateId(Identifier.pack(aggregateId))
                .setTimestamp(timestamp);
    }

    /**
     * Creates a sample {@linkplain AggregateEventRecord record} with the passed timestamp.
     */
    public static <I> AggregateEventRecord create(I aggregateId, Timestamp timestamp) {
        EventMessage eventMessage = Sample.messageOfType(AggProjectCreated.class);
        var event = eventFactory.createEvent(eventMessage);
        return newRecordWith(event.getId(), aggregateId, timestamp)
                .setEvent(event)
                .build();
    }

    /**
     * Creates a record with the passed event and timestamp.
     */
    public static <I> AggregateEventRecord create(I aggregateId, Timestamp timestamp, Event event) {
        return newRecordWith(event.getId(), aggregateId, timestamp)
                .setEvent(event)
                .build();
    }

    /**
     * Returns several records sorted by timestamp ascending.
     * First record's timestamp is the current time.
     */
    public static List<AggregateEventRecord> sequenceFor(ProjectId id) {
        return sequenceFor(id, currentTime());
    }

    /**
     * Returns several records sorted by timestamp ascending.
     *
     * @param start
     *         the timestamp of first record.
     */
    public static List<AggregateEventRecord> sequenceFor(ProjectId id, Timestamp start) {
        var delta = seconds(10);
        var timestamp2 = add(start, delta);
        var timestamp3 = add(timestamp2, delta);

        var factory = TestEventFactory.newInstance(Given.class);

        var e1 = factory.createEvent(projectCreated(id, Given.projectName(id)), null, start);
        var record1 = create(id, start, e1);

        var e2 = factory.createEvent(taskAdded(id), null, timestamp2);
        var record2 = create(id, timestamp2, e2);

        var e3 = factory.createEvent(Given.EventMessage.projectStarted(id), null, timestamp3);
        var record3 = create(id, timestamp3, e3);

        return newArrayList(record1, record2, record3);
    }
}
