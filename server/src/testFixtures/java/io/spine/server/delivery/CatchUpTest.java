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

package io.spine.server.delivery;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Durations;
import io.spine.base.Time;
import io.spine.server.delivery.given.ConsecutiveNumberProcess;
import io.spine.server.delivery.given.ConsecutiveProjection;
import io.spine.server.delivery.given.CounterCatchUp;
import io.spine.server.delivery.given.CounterView;
import io.spine.server.delivery.given.WhatToCatchUp;
import io.spine.test.delivery.EmitNextNumber;
import io.spine.testing.SlowTest;
import io.spine.testing.server.blackbox.BlackBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.IntStream;

import static com.google.common.truth.Truth.assertThat;
import static com.google.protobuf.util.Timestamps.subtract;
import static io.spine.base.Time.currentTime;
import static io.spine.server.delivery.CatchUpStatus.COMPLETED;
import static io.spine.server.delivery.TestRoutines.findView;
import static io.spine.server.delivery.TestRoutines.post;
import static io.spine.server.delivery.given.WhatToCatchUp.catchUpAll;
import static io.spine.server.delivery.given.WhatToCatchUp.catchUpOf;
import static io.spine.testing.TestValues.nullRef;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests for the {@linkplain io.spine.server.projection.ProjectionRepository#catchUp(Timestamp, Set)
 * projection catch-up} functionality.
 *
 * <p>The test routines are designed to check both small and big use-cases, including
 * the full catch-up. To deal with the different wall-clock providers, some of the tests
 * configure the {@linkplain Time#currentTime() time provider} to return the values with
 * millisecond precision. It is required to test the catch-up in the scenarios close to the legacy
 * applications, as at that time there were no emulation of the nanosecond time resolution.
 *
 * <p>As the downstream libraries, such as Spine Google Cloud library, would want to run the same
 * tests under their specific conditions, the big and slow catch-up tests are made {@code public}.
 * In this way such tests may be overridden and disabled, if needed.
 */
@SlowTest
@DisplayName("Catch-up of projection instances should")
@SuppressWarnings("WeakerAccess")   // see the class-level documentation.
public abstract class CatchUpTest extends AbstractDeliveryTest {

    @Override
    @AfterEach
    public void tearDown() {
        clearCatchUps();
        super.tearDown();
        Time.resetProvider();
    }

    private void clearCatchUps() {
        delivery().catchUpStorage()
                  .clear();
    }

    /**
     * This test is intentionally left {@code public}.
     *
     * <p>See the class-level docs.
     */
    @Test
    @DisplayName("given the time is provided with nanosecond resolution " +
            "catch up only particular instances by their IDs")
    public void withNanosByIds() throws InterruptedException {
        testCatchUpByIds();
    }

    /**
     * This test is intentionally left {@code public}.
     *
     * <p>See the class-level docs.
     */
    @Test
    @DisplayName("given the time is provided with nanosecond resolution" +
            " catch up all of projection instances " +
            "and respect the order of the delivered events")
    public void withNanosAllInOrder() throws InterruptedException {
        testCatchUpAll();
    }

    /**
     * This test is intentionally left {@code public}.
     *
     * <p>See the class-level docs.
     */
    @Test
    @DisplayName("given the time is provided with millisecond resolution" +
            " catch up only particular instances by their IDs")
    public void withMillisByIds() throws InterruptedException {
        setupMillis();
        testCatchUpByIds();
    }

    /**
     * This test is intentionally left {@code public}.
     *
     * <p>See the class-level docs.
     */
    @Test
    @DisplayName("given the time is provided with millisecond resolution " +
            "catch up all of projection instances and " +
            "respect the order of the delivered events")
    public void withMillisAllInOrder() throws InterruptedException {
        setupMillis();
        testCatchUpAll();
    }

    @Test
    @DisplayName("do nothing if the event store is empty")
    public void onAnEmptyStorage() {
        testCatchUpEmpty();
    }

    @Nested
    @DisplayName("allow catch-up")
    class AllowCatchUp {

        @Test
        @DisplayName("if the event store is empty")
        void onEmptyEventStore() {
            try (var counterCatchUp = catchUpForCounter()) {
                counterCatchUp.catchUp(WhatToCatchUp.catchUpAll(aMinuteAgo()));
            }
        }

        @Test
        @DisplayName("of the same instance, if the previous catch-up is already completed")
        void ifPreviousCatchUpCompleted() {
            CounterCatchUp.addOngoingCatchUpRecord(CatchUpTest.this,
                                                   catchUpAll(aMinuteAgo()),
                                                   COMPLETED);
            try (var counterCatchUp = catchUpForCounter()) {
                counterCatchUp.catchUp(WhatToCatchUp.catchUpAll(aMinuteAgo()));
            }
        }
    }

    @Nested
    @DisplayName("not allow simultaneous catch-up")
    class NotAllowSimultaneousCatchUp {

        private static final String TARGET_ID = "some target";

        @Test
        @DisplayName("if catching up of all repository instances has started previously")
        void ifCatchUpAllStartedPreviously() {
            CounterCatchUp.addOngoingCatchUpRecord(CatchUpTest.this, catchUpAll(aMinuteAgo()));
            try (var counterCatchUp = catchUpForCounter()) {
                for (var target : counterCatchUp.targets()) {
                    assertCatchUpAlreadyStarted(counterCatchUp, target);
                }
            }
        }

        @Test
        @DisplayName("of the same repository instances")
        void ofSameInstances() {
            CounterCatchUp.addOngoingCatchUpRecord(CatchUpTest.this,
                                                   catchUpOf(TARGET_ID, aMinuteAgo()));
            try (var counterCatchUp = new CounterCatchUp(CatchUpTest.this, TARGET_ID)) {
                assertCatchUpAlreadyStarted(counterCatchUp, TARGET_ID);
            }
        }

        @Test
        @DisplayName("of all instances if at least one catch-up of an instance is in progress")
        void ofAllIfOneAlreadyStarted() {
            CounterCatchUp.addOngoingCatchUpRecord(CatchUpTest.this,
                                                   catchUpOf(TARGET_ID, aMinuteAgo()));
            try(var counterCatchUp = new CounterCatchUp(CatchUpTest.this, TARGET_ID)) {
                counterCatchUp.catchUp(catchUpAll(aMinuteAgo()));
                fail("It must not be possible to start catching up all the instances," +
                             " while some instance is already catching up.");
            } catch (CatchUpAlreadyStartedException exception) {
                assertThat(exception.projectionStateType()).isEqualTo(CounterView.projectionType());
            }
        }

        private static void assertCatchUpAlreadyStarted(CounterCatchUp counterCatchUp,
                                                        String target) {
            try {
                counterCatchUp.catchUp(catchUpOf(target, aMinuteAgo()));
                fail(format("Simultaneous catch-up was somehow started for ID `%s`.", target));
            } catch (CatchUpAlreadyStartedException exception) {
                assertThat(exception.projectionStateType()).isEqualTo(CounterView.projectionType());
                assertThat(exception.requestedIds()).contains(target);
            }
        }
    }

    private CounterCatchUp catchUpForCounter() {
        return new CounterCatchUp(CatchUpTest.this, "first", "second", "third", "fourth");
    }

    private void testCatchUpEmpty() {
        changeShardCountTo(17);
        try (var counterCatchUp = catchUpForCounter()) {
            var aWhileAgo = subtract(currentTime(), Durations.fromHours(1));
            var someTarget = "some-target";
            counterCatchUp.catchUp(WhatToCatchUp.catchUpOf(someTarget, aWhileAgo));

            var actual = counterCatchUp.find(someTarget);
            assertThat(actual).isEmpty();
        }
    }

    private void testCatchUpByIds() throws InterruptedException {
        changeShardCountTo(2);
        try(var counterCatchUp = catchUpForCounter()) {

            var events = counterCatchUp.generateEvents(200);

            var aWhileAgo = subtract(currentTime(), Durations.fromHours(1));
            counterCatchUp.addHistory(aWhileAgo, events);

            // Round 1. Fight!

            var initialWeight = 1;
            CounterView.changeWeightTo(initialWeight);

            counterCatchUp.dispatch(events, 20);

            var targets = counterCatchUp.targets();
            var totalTargets = targets.length;
            var initialTotals = counterCatchUp.counterValues();
            var sumInRound = events.size() / totalTargets * initialWeight;
            var sums = IntStream.iterate(sumInRound, i -> i)
                                .limit(totalTargets);
            assertThat(initialTotals).isEqualTo(sums.boxed()
                                                    .collect(toList()));

            // Round 2. Catch up the first and the second and fight!

            var newWeight = 100;
            CounterView.changeWeightTo(newWeight);
            counterCatchUp
                    .dispatchWithCatchUp(events, 20,
                                         catchUpOf(targets[0], aWhileAgo),
                                         catchUpOf(targets[1], aMinuteAgo()));

            var totalsAfterCatchUp = counterCatchUp.counterValues();

            var firstSumExpected = sumInRound * newWeight / initialWeight * 3;
            var secondSumExpected = sumInRound * newWeight / initialWeight * 2;
            var untouchedSum = sumInRound + sumInRound * newWeight / initialWeight;
            List<Integer> expectedTotals =
                    ImmutableList.of(firstSumExpected, secondSumExpected, untouchedSum, untouchedSum);

            assertThat(totalsAfterCatchUp).isEqualTo(expectedTotals);
        }
    }

    private void testCatchUpAll() throws InterruptedException {
        ConsecutiveProjection.usePositives();

        var ids = new String[]{"erste", "zweite", "dritte", "vierte"};
        var totalCommands = 300;
        var commands = generateEmissionCommands(totalCommands, ids);

        changeShardCountTo(3);
        var projectionRepo = new ConsecutiveProjection.Repo();
        var ctx = blackBoxWith(
                projectionRepo,
                ConsecutiveNumberProcess.class
        );
        var jobs = asPostCommandJobs(ctx, commands);
        post(jobs, 1);

        var positiveExpected = totalCommands / ids.length;
        List<Integer> positiveValues =
                ImmutableList.of(positiveExpected, positiveExpected,
                                 positiveExpected, positiveExpected);

        var actualLastValues = readLastValues(projectionRepo, ids);
        assertThat(actualLastValues).isEqualTo(positiveValues);

        ConsecutiveProjection.useNegatives();

        var excludedTarget = ids[0];
        projectionRepo.excludeFromRouting(excludedTarget);

        List<Callable<Object>> sameWithCatchUp =
                ImmutableList.<Callable<Object>>builder()
                        .addAll(jobs)
                        .add(() -> {
                            projectionRepo.catchUpAll(aMinuteAgo());
                            return nullRef();
                        })
                        .build();
        post(sameWithCatchUp, 20);

        var negativeExpected = -1 * positiveExpected * 2;

        assertThat(projectionRepo.find(excludedTarget))
                .isEmpty();
        for (var idIndex = 1; idIndex < ids.length; idIndex++) {
            var identifier = ids[idIndex];
            var maybeState = projectionRepo.find(identifier);
            assertThat(maybeState).isPresent();

            var state = maybeState.get()
                                  .state();
            assertThat(state.getLastValue()).isEqualTo(negativeExpected);
        }
        ctx.close();
    }

    private static Timestamp aMinuteAgo() {
        return subtract(currentTime(), Durations.fromMinutes(1));
    }

    private static List<Integer> readLastValues(ConsecutiveProjection.Repo repo,
                                                String[] ids) {
        return Arrays.stream(ids)
                .map((id) -> findView(repo, id).state()
                                               .getLastValue())
                .collect(toList());
    }

    private static List<EmitNextNumber> generateEmissionCommands(int howMany, String[] ids) {
        var idIterator = Iterators.cycle(ids);
        List<EmitNextNumber> commands = new ArrayList<>(howMany);
        for (var i = 0; i < howMany; i++) {
            commands.add(EmitNextNumber.newBuilder()
                                 .setId(idIterator.next())
                                 .build());
        }
        return commands;
    }

    private static List<Callable<Object>>
    asPostCommandJobs(BlackBox ctx, List<EmitNextNumber> commands) {
        return commands.stream()
                .map(cmd -> (Callable<Object>) () -> ctx.receivesCommand(cmd))
                .collect(toList());
    }

    private static void setupMillis() {
        Time.setProvider(new WithMillisOnlyResolution());
    }

    /**
     * A time provider that provides the current time based upon JDK's wall clock,
     * i.e. without the emulated nanoseconds.
     */
    private static class WithMillisOnlyResolution implements Time.Provider {

        @Override
        public Timestamp currentTime() {
            var now = Instant.now();
            var result = Timestamp.newBuilder()
                    .setSeconds(now.getEpochSecond())
                    .setNanos(now.getNano())
                    .build();
            return result;
        }
    }
}
