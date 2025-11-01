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

package io.spine.server.aggregate;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.Any;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Durations;
import io.spine.base.AggregateState;
import io.spine.base.Time;
import io.spine.core.ActorContext;
import io.spine.core.Event;
import io.spine.core.EventContext;
import io.spine.core.EventId;
import io.spine.core.MessageId;
import io.spine.core.Origin;
import io.spine.protobuf.AnyPacker;
import io.spine.server.BoundedContextBuilder;
import io.spine.server.ContextSpec;
import io.spine.server.ServerEnvironment;
import io.spine.server.aggregate.given.StorageRecords;
import io.spine.server.aggregate.given.repo.GivenAggregate;
import io.spine.server.aggregate.given.repo.ProjectAggregateRepository;
import io.spine.server.event.NoReaction;
import io.spine.server.storage.AbstractStorageTest;
import io.spine.test.aggregate.AggProject;
import io.spine.test.aggregate.IntegerProject;
import io.spine.test.aggregate.LongProject;
import io.spine.test.aggregate.ProjectId;
import io.spine.test.aggregate.StringProject;
import io.spine.testdata.Sample;
import io.spine.testing.TestValues;
import io.spine.testing.core.given.GivenCommandContext;
import io.spine.testing.server.TestEventFactory;
import io.spine.testing.server.model.ModelTests;
import io.spine.type.TypeUrl;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Lists.newLinkedList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.protobuf.util.Timestamps.add;
import static io.spine.base.Identifier.newUuid;
import static io.spine.base.Time.currentTime;
import static io.spine.core.Versions.increment;
import static io.spine.core.Versions.zero;
import static io.spine.protobuf.Durations2.seconds;
import static io.spine.protobuf.Messages.isDefault;
import static io.spine.server.aggregate.given.StorageRecords.sequenceFor;
import static io.spine.server.aggregate.given.aggregate.AggregateTestEnv.event;
import static io.spine.testing.TestValues.nullRef;
import static io.spine.testing.core.given.GivenEnrichment.withOneAttribute;
import static java.lang.Integer.MAX_VALUE;
import static java.util.Collections.reverse;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AggregateStorageTest
        extends AbstractStorageTest<ProjectId,
                                    AggregateHistory,
                                    AggregateStorage<ProjectId, AggProject>> {

    private final ProjectId id = Sample.messageOfType(ProjectId.class);

    private final TestEventFactory eventFactory =
            TestEventFactory.newInstance(AggregateStorageTest.class);
    private AggregateStorage<ProjectId, AggProject> storage;

    private static Snapshot newSnapshot(Timestamp time) {
        return Snapshot.newBuilder()
                       .setState(Any.getDefaultInstance())
                       .setTimestamp(time)
                       .build();
    }

    private static EventId newEventId() {
        return EventId.newBuilder()
                      .setValue(newUuid())
                      .build();
    }

    @Override
    @BeforeEach
    public void setUpAbstractStorageTest() {
        super.setUpAbstractStorageTest();
        ModelTests.dropAllModels();
        storage = storage();
    }

    @Override
    protected AggregateHistory newStorageRecord(ProjectId id) {
        var records = sequenceFor(id);
        var expectedEvents = records.stream()
                .map(AggregateStorageTest::toEvent)
                .collect(toList());
        var record = AggregateHistory.newBuilder()
                .addAllEvent(expectedEvents)
                .build();
        return record;
    }

    private static @Nullable Event toEvent(@Nullable AggregateEventRecord record) {
        return record != null
               ? record.getEvent()
               : null;
    }

    @Override
    protected ProjectId newId() {
        return ProjectId.generate();
    }

    @Override
    protected AggregateStorage<ProjectId, AggProject> newStorage() {
        return newStorage(TestAggregate.class);
    }

    /**
     * Creates the storage for the specified ID and aggregate class.
     *
     * <p>The created storage should be closed manually.
     *
     * @param aggregateClass
     *         the aggregate class
     * @param <I>
     *         the type of aggregate IDs
     * @return a new storage instance
     */
    <I, S extends AggregateState<I>> AggregateStorage<I, S>
    newStorage(Class<? extends Aggregate<I, S, ?>> aggregateClass) {
        var spec = ContextSpec.singleTenant("`AggregateStorage` tests");
        var result =
                ServerEnvironment.instance()
                                 .storageFactory()
                                 .createAggregateStorage(spec,aggregateClass);
        return result;
    }

    @Nested
    @DisplayName("being empty, return")
    class BeingEmptyReturn {

        @Test
        @DisplayName("iterator over empty collection on reading history")
        void emptyHistory() {
            var iterator = historyBackward();

            assertFalse(iterator.hasNext());
        }

        @Test
        @DisplayName("empty `AggregateHistory` on reading record")
        void absentRecord() {
            var record = storage.read(id);

            assertFalse(record.isPresent());
        }
    }

    @Nested
    @DisplayName("not accept `null`")
    class NotAcceptNull {

        @Test
        @DisplayName("request ID when reading history")
        void idForReadHistory() {
            assertThrows(NullPointerException.class,
                         () -> storage.historyBackward(nullRef(), 10));
        }

        @Test
        @DisplayName("event for writing")
        void event() {
            assertThrows(NullPointerException.class,
                         () -> storage.writeEvent(id, nullRef()));
        }

        @Test
        @DisplayName("event ID for writing")
        void eventId() {
            assertThrows(NullPointerException.class,
                         () -> storage.writeEvent(nullRef(), Event.getDefaultInstance()));
        }

        @Test
        @DisplayName("snapshot for writing")
        void snapshot() {
            assertThrows(NullPointerException.class,
                         () -> storage.writeSnapshot(id, nullRef()));
        }

        @Test
        @DisplayName("snapshot ID for writing")
        void snapshotId() {
            assertThrows(NullPointerException.class,
                         () -> storage.writeSnapshot(nullRef(), Snapshot.getDefaultInstance()));
        }
    }

    @Nested
    @DisplayName("write and read single event by ID of type")
    class WriteAndReadEvent {

        @Test
        @DisplayName("`Message`")
        void byMessageId() {
            writeAndReadEventTest(id, storage);
        }

        @Test
        @DisplayName("`String`")
        void byStringId() {
            AggregateStorage<String, ?> storage = newStorage(TestAggregateWithIdString.class);
            var id = newUuid();
            writeAndReadEventTest(id, storage);
        }

        @Test
        @DisplayName("`Long`")
        void byLongId() {
            AggregateStorage<Long, ?> storage = newStorage(TestAggregateWithIdLong.class);
            var id = 10L;
            writeAndReadEventTest(id, storage);
        }

        @Test
        @DisplayName("`Integer`")
        void byIntegerId() {
            AggregateStorage<Integer, ?> storage = newStorage(TestAggregateWithIdInteger.class);
            var id = 10;
            writeAndReadEventTest(id, storage);
        }

        private <I> void writeAndReadEventTest(I id, AggregateStorage<I, ?> storage) {
            var expectedEvent = eventFactory.createEvent(event(AggProject.getDefaultInstance()));

            storage.writeEvent(id, expectedEvent);

            var optional = storage.read(id, MAX_VALUE);
            assertTrue(optional.isPresent());
            var events = optional.get();
            assertEquals(1, events.getEventCount());
            var actualEvent = events.getEvent(0);
            assertEquals(expectedEvent, actualEvent);

            close(storage);
        }
    }

    @Test
    @DisplayName("write and read one record")
    void writeAndReadRecord() {
        var expected = StorageRecords.create(id, currentTime());

        storage.writeEventRecord(id, expected);

        var iterator = historyBackward();
        assertTrue(iterator.hasNext());
        var actual = iterator.next();
        assertEquals(expected, actual);
        assertFalse(iterator.hasNext());
    }

    /**
     *  This test is not applicable to the aggregate storage, as several records may be stored
     *  by the same aggregate ID. That's why it is disabled.
     */
    @SuppressWarnings("RefusedBequest")
    @Override
    @Test
    @Disabled
    @DisplayName("re-write record if writing by the same ID")
    protected void rewriteRecord() {
    }

    @Test
    @Override
    protected void immutableIndex() {
        var aggregate = givenAggregate().withUncommittedEvents();
        storage.writeState(aggregate);
        assertIndexImmutability();
    }

    @Test
    @Override
    protected void indexCountingAllIds() {
        var given = givenAggregate();
        var batchSize = 5;
        List<ProjectId> expectedIds = new ArrayList<>(batchSize);
        for(var index = 0; index < batchSize; index++) {
            var id = Sample.messageOfType(ProjectId.class);
            var aggregate = given.withUncommittedEvents(id);
            storage.writeState(aggregate);
            expectedIds.add(id);
        }

        var index = storage.index();
        var actualIds = ImmutableList.copyOf(index);
        assertThat(actualIds).containsExactlyElementsIn(expectedIds);
    }

    @Nested
    @DisplayName("write records and return them")
    class WriteRecordsAndReturn {

        @Test
        @DisplayName("sorted by timestamp descending")
        void sortedByTimestamp() {
            var records = sequenceFor(id);

            writeAll(id, records);

            var iterator = historyBackward();
            List<AggregateEventRecord> actual = newArrayList(iterator);
            reverse(records); // expected records should be in a reverse order
            assertEquals(records, actual);
        }

        @Test
        @DisplayName("sorted by version descending")
        void sortedByVersion() {
            var eventsNumber = 5;
            List<AggregateEventRecord> records = newLinkedList();
            var timestamp = currentTime();
            var currentVersion = zero();
            for (var i = 0; i < eventsNumber; i++) {
                var state = AggProject.getDefaultInstance();
                var event = eventFactory.createEvent(event(state), currentVersion, timestamp);
                var record = StorageRecords.create(id, timestamp, event);
                records.add(record);
                currentVersion = increment(currentVersion);
            }
            writeAll(id, records);

            var iterator = historyBackward();
            List<AggregateEventRecord> actual = newArrayList(iterator);
            reverse(records); // expected records should be in a reverse order
            assertEquals(records, actual);
        }

        @Test
        @DisplayName("sorted by version rather than by timestamp")
        void sortByVersionFirstly() {
            var state = AggProject.getDefaultInstance();
            var minVersion = zero();
            var maxVersion = increment(minVersion);
            var minTimestamp = currentTime();
            var maxTimestamp = add(minTimestamp, Durations.fromHours(1));

            // The first event is an event, which is the oldest, i.e. with the minimal version.
            var expectedFirst = eventFactory.createEvent(event(state), minVersion, maxTimestamp);
            var expectedSecond = eventFactory.createEvent(event(state), maxVersion, minTimestamp);

            storage.writeEvent(id, expectedSecond);
            storage.writeEvent(id, expectedFirst);

            var record = readRecord(id);
            var events = record.getEventList();
            assertTrue(events.indexOf(expectedFirst) < events.indexOf(expectedSecond));
        }
    }

    @Test
    @DisplayName("write and read snapshot")
    void writeAndReadSnapshot() {
        var expected = newSnapshot(currentTime());

        storage.writeSnapshot(id, expected);

        var iterator = historyBackward();
        assertTrue(iterator.hasNext());
        var actual = iterator.next();
        assertEquals(expected, actual.getSnapshot());
        assertFalse(iterator.hasNext());
    }

    @Nested
    @DisplayName("write records and load history")
    class WriteRecordsAndLoadHistory {

        @Test
        @DisplayName("if there are no snapshots available")
        void withNoSnapshots() {
            testWriteRecordsAndLoadHistory(currentTime());
        }

        @Test
        @DisplayName("till last snapshot available")
        void tillLastSnapshot() {
            var delta = seconds(10);
            var time1 = currentTime();
            var time2 = add(time1, delta);
            var time3 = add(time2, delta);

            storage.writeEventRecord(id, StorageRecords.create(id, time1));
            storage.writeSnapshot(id, newSnapshot(time2));

            testWriteRecordsAndLoadHistory(time3);
        }
    }

    @Test
    @DisplayName("continue reading history if snapshot was not found in first batch")
    void continueReadHistoryIfSnapshotNotFound() {
        var currentVersion = zero();
        var snapshot = Snapshot.newBuilder()
                                    .setVersion(currentVersion)
                                    .build();
        storage.writeSnapshot(id, snapshot);

        var eventsAfterSnapshot = 10;
        for (var i = 0; i < eventsAfterSnapshot; i++) {
            currentVersion = increment(currentVersion);
            var state = AggProject.getDefaultInstance();
            var event = eventFactory.createEvent(event(state), currentVersion);
            storage.writeEvent(id, event);
        }

        var optionalStateRecord = storage.read(id, 1);

        assertTrue(optionalStateRecord.isPresent());
        var stateRecord = optionalStateRecord.get();
        assertEquals(snapshot, stateRecord.getSnapshot());
        assertEquals(eventsAfterSnapshot, stateRecord.getEventCount());
    }

    @Nested
    @DisplayName("not store enrichment")
    class NotStoreEnrichment {

        @Test
        @DisplayName("for `EventContext`")
        void forEventContext() {
            var context = ActorContext.newBuilder().buildPartial();
            var messageId = MessageId.newBuilder()
                    .setId(AnyPacker.pack(newEventId()))
                    .setTypeUrl(TypeUrl.of(NoReaction.class)
                                       .value())
                    .buildPartial();
            var origin = Origin.newBuilder()
                    .setActorContext(context)
                    .setMessage(messageId)
                    .build();
            var enrichedContext = EventContext.newBuilder()
                    .setEnrichment(withOneAttribute())
                    .setTimestamp(Time.currentTime())
                    .setProducerId(AnyPacker.pack(TestValues.newUuidValue()))
                    .setPastMessage(origin)
                    .build();
            var event = Event.newBuilder()
                    .setId(newEventId())
                    .setContext(enrichedContext)
                    .setMessage(AnyPacker.pack(TestValues.newUuidValue()))
                    .build();
            storage.writeEvent(id, event);

            var record = readRecord(id);
            var loadedContext = record.getEvent(0).context();
            assertTrue(isDefault(loadedContext.getEnrichment()));
        }

        @SuppressWarnings("deprecation") // For backward compatibility.
        @Test
        @DisplayName("for origin of `EventContext` type")
        void forEventContextOrigin() {
            var origin = EventContext.newBuilder()
                    .setEnrichment(withOneAttribute())
                    .setTimestamp(Time.currentTime())
                    .setProducerId(AnyPacker.pack(TestValues.newUuidValue()))
                    .setCommandContext(GivenCommandContext.withRandomActor())
                    .build();
            var context = EventContext.newBuilder()
                    .setEventContext(origin)
                    .setTimestamp(Time.currentTime())
                    .setProducerId(AnyPacker.pack(TestValues.newUuidValue()))
                    .build();
            var event = Event.newBuilder()
                    .setId(newEventId())
                    .setContext(context)
                    .setMessage(AnyPacker.pack(TestValues.newUuidValue()))
                    .build();
            storage.writeEvent(id, event);
            var record = readRecord(id);
            var loadedOrigin = record.getEvent(0)
                                     .context()
                                     .getEventContext();
            assertTrue(isDefault(loadedOrigin.getEnrichment()));
        }
    }

    private AggregateHistory readRecord(ProjectId id) {
        var optional = storage.read(id);
        assertTrue(optional.isPresent());
        return optional.get();
    }

    void testWriteRecordsAndLoadHistory(Timestamp firstRecordTime) {
        var records = sequenceFor(id, firstRecordTime);

        writeAll(id, records);

        var events = readRecord(id);
        var expectedEvents = records.stream()
                .map(AggregateStorageTest::toEvent)
                .collect(Collectors.toList());
        var actualEvents = events.getEventList();
        assertEquals(expectedEvents, actualEvents);
    }

    protected void writeAll(ProjectId id, Iterable<AggregateEventRecord> records) {
        for (var record : records) {
            storage.writeEventRecord(id, record);
        }
    }

    private Iterator<AggregateEventRecord> historyBackward() {
        return storage.historyBackward(id, MAX_VALUE);
    }

    public static class TestAggregate extends Aggregate<ProjectId, AggProject, AggProject.Builder> {

        protected TestAggregate(ProjectId id) {
            super(id);
        }
    }

    private static class TestAggregateWithIdString
            extends Aggregate<String, StringProject, StringProject.Builder> {

        private TestAggregateWithIdString(String id) {
            super(id);
        }
    }

    private static class TestAggregateWithIdInteger
            extends Aggregate<Integer, IntegerProject, IntegerProject.Builder> {

        private TestAggregateWithIdInteger(Integer id) {
            super(id);
        }
    }

    private static class TestAggregateWithIdLong
            extends Aggregate<Long, LongProject, LongProject.Builder> {

        private TestAggregateWithIdLong(Long id) {
            super(id);
        }
    }

    private static GivenAggregate givenAggregate() {
        var repository = new ProjectAggregateRepository();
        BoundedContextBuilder.assumingTests().add(repository).build();
        return new GivenAggregate(repository);
    }
}
