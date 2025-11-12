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

package io.spine.server.aggregate;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Durations;
import io.spine.core.Event;
import io.spine.core.Version;
import io.spine.server.ContextSpec;
import io.spine.server.ServerEnvironment;
import io.spine.server.aggregate.AggregateStorageTest.TestAggregate;
import io.spine.server.aggregate.given.fibonacci.FibonacciRepository;
import io.spine.server.aggregate.given.fibonacci.Sequence;
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
import static java.lang.Integer.MAX_VALUE;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests the truncation of an Aggregate history.
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
@SuppressWarnings("AbstractClassWithoutAbstractMethods") // designed for the various storage impls.
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
                // Send a lot of `MoveSequence` events, so several snapshots are created.
                var moveSequence = MoveSequence.newBuilder()
                        .setId(ID)
                        .build();
                var snapshotTrigger = repo.snapshotTrigger();
                for (var i = 0; i < snapshotTrigger * 5 + 1; i++) {
                    context.receivesCommand(moveSequence);
                }

                // Compare against the numbers calculated by hand.
                var expectedNumberOne = 121393;
                var expectedNumberTwo = 196418;
                assertThat(lastNumberOne())
                        .isEqualTo(expectedNumberOne);
                assertThat(lastNumberTwo())
                        .isEqualTo(expectedNumberTwo);

                // Truncate the storage.
                var storage = repo.aggregateStorage();
                var countBeforeTruncate = recordCount(storage);
                assertThat(countBeforeTruncate)
                        .isGreaterThan(snapshotTrigger);
                storage.truncateOlderThan(0);
                var countAfterTruncate = recordCount(storage);
                assertThat(countAfterTruncate)
                        .isAtMost(snapshotTrigger);

                // Run one more command and check the result.
                var expectedNext = lastNumberOne() + lastNumberTwo();
                context.receivesCommand(moveSequence);
                assertThat(lastNumberTwo())
                        .isEqualTo(expectedNext);
            }
        }

        private int recordCount(AggregateStorage<SequenceId, Sequence> storage) {
            var iterator = storage.historyBackward(ID,
                                                   Integer.MAX_VALUE);
            return Iterators.size(iterator);
        }
    }

    @Nested
    @DisplayName("should truncate the history of an `Aggregate` instance")
    class Truncate {

        private final ProjectId id = Sample.messageOfType(ProjectId.class);
        private final TestEventFactory eventFactory =
                TestEventFactory.newInstance(AggregateStorageTest.class);

        private Version currentVersion;
        private AggregateStorage<ProjectId, AggProject> storage;

        @BeforeEach
        void setUp() {
            currentVersion = zero();

            var spec = ContextSpec.singleTenant("Aggregate truncation tests");
            storage = ServerEnvironment.instance()
                                       .storageFactory()
                                       .createAggregateStorage(spec, TestAggregate.class);
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
            var iterator = storage.historyBackward(id, MAX_VALUE);
            return ImmutableList.copyOf(iterator);
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
            storage.writeSnapshot(id, snapshot);
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
            storage.writeEvent(id, event);
            return event;
        }
    }
}
