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

package io.spine.server.aggregate;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Durations;
import io.spine.base.Identifier;
import io.spine.core.Event;
import io.spine.core.Version;
import io.spine.server.ContextSpec;
import io.spine.server.ServerEnvironment;
import io.spine.server.aggregate.AggregateStorageTest.TestAggregate;
import io.spine.server.aggregate.given.fibonacci.FibonacciRepository;
import io.spine.server.aggregate.given.fibonacci.SequenceId;
import io.spine.server.aggregate.given.fibonacci.command.MoveSequence;
import io.spine.server.aggregate.given.fibonacci.command.SetStartingNumbers;
import io.spine.server.aggregate.given.fibonacci.event.StartingNumbersSet;
import io.spine.test.aggregate.AggProject;
import io.spine.test.aggregate.ProjectId;
import io.spine.testdata.Sample;
import io.spine.testing.server.TestEventFactory;
import io.spine.testing.server.blackbox.BlackBox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static com.google.protobuf.util.Timestamps.add;
import static com.google.protobuf.util.Timestamps.subtract;
import static io.spine.base.Identifier.newUuid;
import static io.spine.base.Time.currentTime;
import static io.spine.core.Versions.increment;
import static io.spine.core.Versions.zero;
import static io.spine.protobuf.Durations2.seconds;
import static io.spine.server.aggregate.given.aggregate.AggregateTestEnv.event;
import static io.spine.server.aggregate.given.fibonacci.FibonacciAggregate.lastNumberOne;
import static io.spine.server.aggregate.given.fibonacci.FibonacciAggregate.lastNumberTwo;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests the truncation of the legacy Aggregate journal.
 *
 * <p>The snapshot-index truncation operates only on the {@link AggregateEventRecord}s written
 * by the earlier, event-sourced versions of the framework. These tests simulate such legacy
 * journals by writing the records directly to the {@linkplain AggregateStorage#legacyJournal()
 * legacy journal storage}.
 *
 * <p>Wishing to customize the storage for these tests, descendants may configure it via:
 * <pre>
 *     ServerEnvironment.when(Tests.class)
 *                      .use(customStorageFactory);
 * </pre>
 *
 * <p>Please note that for the test name to make sense the descendants should have some
 * meaningful display names, e.g. {@code "InMemoryAggregateStorage"}.
 */
@SuppressWarnings({
        "AbstractClassWithoutAbstractMethods" /* Designed for the various storage impls. */,
        "deprecation" /* Tests the deprecated legacy-journal truncation on purpose. */})
public abstract class AggregateHistoryTruncationTest {

    private static final SequenceId ID = SequenceId.newBuilder()
            .setValue(newUuid())
            .build();

    @Nested
    @DisplayName("after the history truncation should ")
    class VerifyIntegrity {

        @Test
        @DisplayName("restore the `Aggregate` state properly")
        void restoreAggregateState() {
            var repo = new FibonacciRepository();
            var context = BlackBox.singleTenantWith(repo);
            try (context) {
                // Set the starting numbers.
                var setStartingNumbers = SetStartingNumbers.newBuilder()
                        .setId(ID)
                        .setNumberOne(0)
                        .setNumberTwo(1)
                        .build();
                context.receivesCommand(setStartingNumbers)
                       .assertEvents()
                       .withType(StartingNumbersSet.class)
                       .hasSize(1);
                // Send a lot of `MoveSequence` events.
                var moveSequence = MoveSequence.newBuilder()
                        .setId(ID)
                        .build();
                var historyDepth = repo.historyDepth();
                for (var i = 0; i < historyDepth * 5 + 1; i++) {
                    context.receivesCommand(moveSequence);
                }

                // Compare against the numbers calculated by hand.
                var expectedNumberOne = 121393;
                var expectedNumberTwo = 196418;
                assertThat(lastNumberOne())
                        .isEqualTo(expectedNumberOne);
                assertThat(lastNumberTwo())
                        .isEqualTo(expectedNumberTwo);

                // Run the legacy truncation. Since the event-sourcing cutover the aggregate
                // loads from its latest state record, and the truncation touches only the
                // legacy journal records — so it must not affect the aggregate's behavior.
                var storage = repo.aggregateStorage();
                storage.truncateOlderThan(0);

                // Run one more command and check the result.
                var expectedNext = lastNumberOne() + lastNumberTwo();
                context.receivesCommand(moveSequence);
                assertThat(lastNumberTwo())
                        .isEqualTo(expectedNext);
            }
        }
    }

    @Nested
    @DisplayName("should truncate the legacy history of an `Aggregate` instance")
    class Truncate {

        private final ProjectId id = Sample.messageOfType(ProjectId.class);
        private final TestEventFactory eventFactory =
                TestEventFactory.newInstance(AggregateStorageTest.class);

        private Version currentVersion;
        private AggregateStorage<ProjectId, AggProject> storage;
        private AggregateEventStorage legacyJournal;

        @BeforeEach
        void setUp() {
            currentVersion = zero();

            var spec = ContextSpec.singleTenant("Aggregate truncation tests");
            storage = ServerEnvironment.instance()
                                       .storageFactory()
                                       .createAggregateStorage(spec, TestAggregate.class);
            legacyJournal = storage.legacyJournal();
        }

        @Test
        @DisplayName("to the Nth latest snapshot")
        void toTheNthSnapshot() {
            writeSnapshot();
            writeEvent();
            var latestSnapshot = writeSnapshot();

            var snapshotIndex = 0;
            storage.truncateOlderThan(snapshotIndex);

            List<AggregateEventRecord> records = historyBackward();
            assertThat(records)
                    .hasSize(1);
            assertThat(records.get(0)
                              .getSnapshot())
                    .isEqualTo(latestSnapshot);
        }

        @Test
        @DisplayName("by date")
        void byDate() {
            var delta = seconds(10);
            var now = currentTime();
            var before = subtract(now, delta);
            var after = add(now, delta);

            writeSnapshot(before);
            writeEvent(before);
            var latestEvent = writeEvent(after);
            var latestSnapshot = writeSnapshot(after);

            var snapshotIndex = 0;
            storage.truncateOlderThan(snapshotIndex, now);

            List<AggregateEventRecord> records = historyBackward();
            assertThat(records)
                    .hasSize(2);
            assertThat(records.get(0)
                              .getSnapshot())
                    .isEqualTo(latestSnapshot);
            assertThat(records.get(1)
                              .getEvent())
                    .isEqualTo(latestEvent);
        }

        @Test
        @DisplayName("by date preserving at least the Nth latest snapshot")
        void byDateAndSnapshot() {
            var delta = seconds(10);
            var now = currentTime();
            var before = subtract(now, delta);
            var after = add(now, delta);

            writeEvent(before);
            var snapshot1 = writeSnapshot(before);
            var event1 = writeEvent(before);
            var event2 = writeEvent(after);
            var snapshot2 = writeSnapshot(after);

            var snapshotIndex = 1;
            storage.truncateOlderThan(snapshotIndex, now);

            // The `event1` should be preserved event though it occurred before the specified date.
            List<AggregateEventRecord> records = historyBackward();
            assertThat(records).hasSize(4);
            assertThat(records.get(0)
                              .getSnapshot())
                    .isEqualTo(snapshot2);
            assertThat(records.get(1)
                              .getEvent())
                    .isEqualTo(event2);
            assertThat(records.get(2)
                              .getEvent())
                    .isEqualTo(event1);
            assertThat(records.get(3)
                              .getSnapshot())
                    .isEqualTo(snapshot1);
        }

        @Test
        @DisplayName("with an `IllegalArgumentException` thrown in case " +
                "an incorrect snapshot index is specified for truncate operation")
        void throwIaeOnInvalidTruncate() {
            assertThrows(IllegalArgumentException.class, () -> storage.truncateOlderThan(-1));
            assertThrows(IllegalArgumentException.class,
                         () -> storage.truncateOlderThan(-2, Timestamp.getDefaultInstance()));
        }

        private ImmutableList<AggregateEventRecord> historyBackward() {
            var builder = legacyJournal.queryBuilder()
                    .where(AggregateEventRecordColumn.aggregate_id)
                    .is(Identifier.pack(id));
            var query = TruncateOperation.newestFirst(builder)
                                         .build();
            return ImmutableList.copyOf(legacyJournal.readAll(query));
        }

        @CanIgnoreReturnValue
        private Snapshot writeSnapshot() {
            return writeSnapshot(Timestamp.getDefaultInstance());
        }

        @CanIgnoreReturnValue
        private Snapshot writeSnapshot(Timestamp atTime) {
            currentVersion = increment(currentVersion);
            var snapshot = Snapshot.newBuilder()
                    .setTimestamp(atTime)
                    .setVersion(currentVersion)
                    .build();
            var record = AggregateEventRecord.newBuilder()
                    .setId(legacyRecordId())
                    .setAggregateId(Identifier.pack(id))
                    .setTimestamp(atTime)
                    .setSnapshot(snapshot)
                    .build();
            legacyJournal.write(record);
            return snapshot;
        }

        @CanIgnoreReturnValue
        private Event writeEvent() {
            return writeEvent(subtract(currentTime(), Durations.fromDays(365)));
        }

        @CanIgnoreReturnValue
        private Event writeEvent(Timestamp atTime) {
            currentVersion = increment(currentVersion);
            var state = AggProject.getDefaultInstance();
            var event = eventFactory.createEvent(event(state), currentVersion, atTime);
            var record = AggregateEventRecord.newBuilder()
                    .setId(legacyRecordId())
                    .setAggregateId(Identifier.pack(id))
                    .setTimestamp(atTime)
                    .setEvent(event)
                    .build();
            legacyJournal.write(record);
            return event;
        }

        private AggregateEventRecordId legacyRecordId() {
            return AggregateEventRecordId.newBuilder()
                    .setValue(newUuid())
                    .build();
        }
    }
}
