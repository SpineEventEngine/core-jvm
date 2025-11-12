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

package io.spine.server.delivery;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.util.Durations;
import io.spine.base.Identifier;
import io.spine.environment.Tests;
import io.spine.protobuf.Messages;
import io.spine.server.ServerEnvironment;
import io.spine.server.delivery.given.DeliveryTestEnv.RawMessageMemoizer;
import io.spine.server.delivery.given.DeliveryTestEnv.ShardIndexMemoizer;
import io.spine.server.delivery.given.FixedShardStrategy;
import io.spine.server.delivery.given.MemoizingDeliveryMonitor;
import io.spine.server.delivery.given.TaskAggregate;
import io.spine.server.delivery.given.TaskAssignment;
import io.spine.server.delivery.given.TaskView;
import io.spine.server.delivery.given.ThrowingWorkRegistry;
import io.spine.server.delivery.memory.InMemoryShardedWorkRegistry;
import io.spine.server.tenant.TenantAwareRunner;
import io.spine.test.delivery.DCreateTask;
import io.spine.testing.SlowTest;
import io.spine.testing.core.given.GivenTenantId;
import io.spine.testing.server.blackbox.BlackBox;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;
import static io.spine.server.delivery.given.DeliveryTestEnv.manyTargets;
import static io.spine.server.delivery.given.DeliveryTestEnv.singleTarget;
import static java.util.Collections.synchronizedList;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Integration tests on message delivery that use different settings of sharding configuration and
 * post {@code Command}s and {@code Events} via multiple threads.
 *
 * @implNote Some of the test methods in this test class use underscores to improve the
 *         readability and allow to distinguish one test from another by their names faster.
 */
@SlowTest
@DisplayName("Delivery of messages to entities should deliver those via")
@SuppressWarnings("WeakerAccess")   // Exposed for libraries, wishing to run these tests.
public class DeliveryTest extends AbstractDeliveryTest {

    @Test
    @DisplayName("a single shard to a single target in a multi-threaded env")
    public void singleTarget_singleShard_manyThreads() {
        changeShardCountTo(1);
        var aTarget = singleTarget();
        new NastyClient(42).runWith(aTarget);
    }

    @Test
    @DisplayName("a single shard to multiple targets in a multi-threaded env")
    public void manyTargets_singleShard_manyThreads() {
        changeShardCountTo(1);
        var targets = manyTargets(7);
        new NastyClient(10).runWith(targets);
    }

    @Test
    @DisplayName("multiple shards to a single target in a multi-threaded env")
    public void singleTarget_manyShards_manyThreads() {
        changeShardCountTo(1986);
        var targets = singleTarget();
        new NastyClient(15).runWith(targets);
    }

    @Test
    @DisplayName("multiple shards to multiple targets in a multi-threaded env")
    public void manyTargets_manyShards_manyThreads() {
        changeShardCountTo(2004);
        var targets = manyTargets(13);
        new NastyClient(19).runWith(targets);
    }

    @Test
    @DisplayName("multiple shards to a single target in a single-threaded env")
    public void singleTarget_manyShards_singleThread() {
        changeShardCountTo(12);
        var aTarget = singleTarget();
        new NastyClient(1).runWith(aTarget);
    }

    @Test
    @DisplayName("a single shard to a single target in a single-threaded env")
    public void singleTarget_singleShard_singleThread() {
        changeShardCountTo(1);
        var aTarget = singleTarget();
        new NastyClient(1).runWith(aTarget);
    }

    @Test
    @DisplayName("a single shard to multiple targets in a single-threaded env")
    public void manyTargets_singleShard_singleThread() {
        changeShardCountTo(1);
        var targets = manyTargets(11);
        new NastyClient(1).runWith(targets);
    }

    @Test
    @DisplayName("multiple shards to multiple targets in a single-threaded env")
    public void manyTargets_manyShards_singleThread() {
        changeShardCountTo(2019);
        var targets = manyTargets(13);
        new NastyClient(1).runWith(targets);
    }

    @Test
    @DisplayName("multiple shards to multiple targets " +
            "in a multi-threaded env with the custom strategy")
    public void withCustomStrategy() {
        var strategy = new FixedShardStrategy(13);
        var newDelivery = Delivery.localWithStrategyAndWindow(strategy, Durations.ZERO);
        var memoizer = new ShardIndexMemoizer();
        newDelivery.subscribe(memoizer);
        ServerEnvironment.when(Tests.class)
                         .use(newDelivery);

        var targets = manyTargets(7);
        new NastyClient(5, false).runWith(targets);

        var shards = memoizer.shards();
        assertThat(shards.size()).isEqualTo(1);
        assertThat(shards.iterator()
                         .next())
                .isEqualTo(strategy.nonEmptyShard());
    }

    @Test
    @DisplayName("multiple shards to multiple targets in a single-threaded env " +
            "and calculate the statistics properly")
    public void calculateStats() {
        var delivery = Delivery.newBuilder()
                                    .setStrategy(UniformAcrossAllShards.forNumber(7))
                                    .build();
        ServerEnvironment.when(Tests.class)
                         .use(delivery);
        List<DeliveryStats> deliveryStats = synchronizedList(new ArrayList<>());
        delivery.subscribe(msg -> {
            var stats = delivery.deliverMessagesFrom(msg.shardIndex());
            stats.ifPresent(deliveryStats::add);
        });

        var rawMessageMemoizer = new RawMessageMemoizer();
        delivery.subscribe(rawMessageMemoizer);

        var targets = manyTargets(7);
        new NastyClient(1).runWith(targets);
        var totalMsgsInStats = deliveryStats.stream()
                                            .mapToInt(DeliveryStats::deliveredCount)
                                            .sum();
        assertThat(totalMsgsInStats).isEqualTo(rawMessageMemoizer.messages()
                                                                 .size());
    }

    @Test
    @DisplayName("single shard and return stats when picked up the shard " +
            "and notify monitor if shard was already picked up by another node")
    public void returnOptionalEmptyIfPicked() {
        var shardCount = 11;
        ShardedWorkRegistry registry = new InMemoryShardedWorkRegistry();
        var strategy = new FixedShardStrategy(shardCount);
        var monitor = new MonitorUnderTest();
        var delivery = Delivery.newBuilder()
                .setStrategy(strategy)
                .setWorkRegistry(registry)
                .setMonitor(monitor)
                .build();
        ServerEnvironment.when(Tests.class)
                         .use(delivery);

        var index = strategy.nonEmptyShard();
        var tenantId = GivenTenantId.generate();
        TenantAwareRunner.with(tenantId)
                         .run(() -> assertPickedAndDelivered(delivery, index, monitor));

        var outcome = registry.pickUp(index, ServerEnvironment.instance()
                                                              .nodeId());
        assertThat(outcome.hasSession()).isTrue();
        TenantAwareRunner.with(tenantId)
                         .run(() -> assertAlreadyPicked(delivery, index, monitor));
    }

    @Test
    @DisplayName("a single shard and notify monitor if shard pick-up failed for a technical reason")
    public void notifyOnPickUpFailure() {
        var shardCount = 1;
        var registry = new ThrowingWorkRegistry();
        var strategy = new FixedShardStrategy(shardCount);
        var monitor = new MonitorUnderTest();
        var delivery = Delivery.newBuilder()
                .setStrategy(strategy)
                .setWorkRegistry(registry)
                .setMonitor(monitor)
                .build();
        ServerEnvironment.when(Tests.class)
                         .use(delivery);

        var index = strategy.nonEmptyShard();
        var tenantId = GivenTenantId.generate();
        assertThrows(IllegalStateException.class,
                     () -> TenantAwareRunner.with(tenantId)
                                            .run(() -> delivery.deliverMessagesFrom(index))
        );
        assertThat(monitor.failedToPickUp()).containsExactly(index);
    }

    @Test
    @DisplayName("single shard and notify the monitor once the delivery is completed")
    public void notifyDeliveryMonitorOfDeliveryCompletion() {
        var monitor = new MonitorUnderTest();
        var shardCount = 1;
        var strategy = new FixedShardStrategy(shardCount);
        var theOnlyIndex = strategy.nonEmptyShard();
        var delivery = Delivery.newBuilder()
                .setStrategy(strategy)
                .setMonitor(monitor)
                .build();
        var rawMessageMemoizer = new RawMessageMemoizer();
        delivery.subscribe(rawMessageMemoizer);
        delivery.subscribe(new LocalDispatchingObserver());
        ServerEnvironment.when(Tests.class)
                         .use(delivery);

        var aTarget = singleTarget();
        assertThat(monitor.stats()).isEmpty();
        new NastyClient(1).runWith(aTarget);

        for (var singleRunStats : monitor.stats()) {
            assertThat(singleRunStats.shardIndex()).isEqualTo(theOnlyIndex);
        }
        var totalFromStats = monitor.stats()
                .stream()
                .mapToInt(DeliveryStats::deliveredCount)
                .sum();

        var observedMsgCount = rawMessageMemoizer.messages()
                                                 .size();
        assertThat(totalFromStats).isEqualTo(observedMsgCount);
    }

    @Test
    @DisplayName("multiple shards and " +
            "keep them as `TO_DELIVER` right after they are written to `InboxStorage`, " +
            "and mark every as `DELIVERED` after they are actually delivered.")
    @SuppressWarnings("MethodWithMultipleLoops")
    // Traversing over the storage.
    public void markDelivered() {

        var strategy = new FixedShardStrategy(3);

        // Set a very long window to keep the messages non-deleted from the `InboxStorage`.
        var newDelivery = Delivery.localWithStrategyAndWindow(strategy, Durations.fromDays(1));
        var memoizer = new RawMessageMemoizer();
        newDelivery.subscribe(memoizer);
        ServerEnvironment.when(Tests.class)
                         .use(newDelivery);

        var targets = manyTargets(6);
        new NastyClient(3, false).runWith(targets);

        // Check that each message was in `TO_DELIVER` status upon writing to the storage.
        var rawMessages = memoizer.messages();
        for (var message : rawMessages) {
            assertThat(message.getStatus()).isEqualTo(InboxMessageStatus.TO_DELIVER);
        }

        var contents = InboxContents.get();
        for (var messages : contents.values()) {
            for (var message : messages) {
                assertThat(message.getStatus()).isEqualTo(InboxMessageStatus.DELIVERED);
            }
        }
    }

    @Test
    @DisplayName("a single shard to a single target in a multi-threaded env in batches")
    public void deliverInBatch() {
        var strategy = new FixedShardStrategy(1);
        var monitor = new MemoizingDeliveryMonitor();
        var pageSize = 20;
        var delivery = Delivery.newBuilder()
                .setStrategy(strategy)
                .setDeduplicationWindow(Durations.ZERO)
                .setMonitor(monitor)
                .setPageSize(pageSize)
                .build();
        deliverAfterPause(delivery);

        ServerEnvironment.when(Tests.class)
                         .use(delivery);
        var targets = singleTarget();
        var simulator = new NastyClient(7, false);
        simulator.runWith(targets);

        var theTarget = targets.iterator().next();
        var signalsDispatched = simulator.signalsPerTarget()
                                         .get(theTarget)
                                         .size();
        assertThat(simulator.callsToRepoLoadOrCreate(theTarget)).isLessThan(signalsDispatched);
        assertThat(simulator.callsToRepoStore(theTarget)).isLessThan(signalsDispatched);

        assertStages(monitor, pageSize);
    }

    @Test
    @DisplayName("via multiple shards in multiple threads in an order of message emission")
    public void deliverMessagesInOrderOfEmission() throws InterruptedException {
        changeShardCountTo(20);
        TaskView.enableStrictMode();

        var context = BlackBox.singleTenantWith(
                TaskAggregate.class,
                new TaskAssignment.Repository(),
                new TaskView.Repository()
        );
        var commands = generateCommands(200);
        var service = newFixedThreadPool(20);
        service.invokeAll(commands.stream()
                                  .map(c -> (Callable<Object>) () -> context.receivesCommand(c))
                                  .collect(toList()));
        var leftovers = service.shutdownNow();
        assertThat(leftovers).isEmpty();

        for (var command : commands) {
            var taskId = command.getId();
            var subject = context.assertEntity(taskId, TaskView.class);
            subject.exists();

            var actualView = (TaskView) subject.actual();
            var state = actualView.state();
            var actualAssignee = state.getAssignee();

            assertThat(state.getId()).isEqualTo(taskId);
            assertThat(Messages.isDefault(actualAssignee)).isFalse();
        }
    }

    @Test
    @DisplayName("direct `Delivery`")
    public void directDelivery() {
        // Wait until all previous `Delivery` tests complete their async routines.
        sleepUninterruptibly(500, MILLISECONDS);

        TaskView.disableStrictMode();
        TaskView.clearCache();
        var directDelivery = Delivery.direct();
        ServerEnvironment.when(Tests.class)
                         .use(directDelivery);
        try (var context = BlackBox.singleTenantWith(
                TaskAggregate.class,
                new TaskAssignment.Repository(),
                new TaskView.Repository())
        ) {
            var commands = generateCommands(200);
            for (var command : commands) {
                context.receivesCommand(command);
                var taskId = command.getId();
                var subject = context.assertEntity(taskId, TaskView.class);
                subject.exists();

                var actualView = (TaskView) subject.actual();
                var state = actualView.state();
                var actualAssignee = state.getAssignee();

                assertThat(state.getId()).isEqualTo(taskId);
                assertThat(Messages.isDefault(actualAssignee)).isFalse();
            }
        }
    }

    /*
     * Test environment.
     *
     * <p>Accesses the {@linkplain Delivery Delivery API} which has been made
     * package-private and marked as visible for testing. Therefore, the test environment routines
     * aren't moved to a separate {@code ...TestEnv} class. Otherwise the test-only API
     * of {@code Delivery} must have been made {@code public}, which wouldn't be
     * a good API design move.
     ******************************************************************************/

    private static void
    assertPickedAndDelivered(Delivery delivery, ShardIndex index, MonitorUnderTest monitor) {
        var stats = delivery.deliverMessagesFrom(index);
        assertThat(stats).isPresent();
        assertThat(stats.get()
                        .shardIndex()).isEqualTo(index);
        assertThat(monitor.failedToPickUp()).isEmpty();
        assertThat(monitor.alreadyPickedShards()).isEmpty();
    }

    private static void
    assertAlreadyPicked(Delivery delivery, ShardIndex index, MonitorUnderTest monitor) {
        var emptyStats = delivery.deliverMessagesFrom(index);
        assertThat(emptyStats).isEmpty();
        assertThat(monitor.failedToPickUp()).isEmpty();
        assertThat(monitor.alreadyPickedShards()).containsExactly(index);
    }

    private static List<DCreateTask> generateCommands(int howMany) {
        List<DCreateTask> commands = new ArrayList<>();
        for (var taskIndex = 0; taskIndex < howMany; taskIndex++) {
            var taskId = Identifier.newUuid();
            commands.add(DCreateTask.newBuilder()
                                 .setId(taskIndex + "--" + taskId)
                                 .build());
        }
        return commands;
    }

    private static void assertStages(MemoizingDeliveryMonitor monitor, int pageSize) {
        var totalStages = monitor.getStages();
        var actualSizePerPage = totalStages.stream()
                .map(DeliveryStage::getMessagesDelivered)
                .collect(toList());
        for (var actualSize : actualSizePerPage) {
            assertThat(actualSize).isAtMost(pageSize);
        }
    }

    private static void deliverAfterPause(Delivery delivery) {
        var latch = new CountDownLatch(20);
        // Sleep for some time to accumulate messages in shards before starting to process them.
        delivery.subscribe(update -> {
            if (latch.getCount() > 0) {
                sleepUninterruptibly(Duration.ofMillis(10));
                latch.countDown();
            } else {
                delivery.deliverMessagesFrom(update.shardIndex());
            }
        });
    }

    private static final class MonitorUnderTest extends DeliveryMonitor {

        private final List<DeliveryStats> allStats = new ArrayList<>();
        private final List<ShardIndex> alreadyPicked = new ArrayList<>();
        private final List<ShardIndex> failedToPickUp = new ArrayList<>();

        @Override
        public void onDeliveryCompleted(DeliveryStats stats) {
            allStats.add(stats);
        }

        private ImmutableList<DeliveryStats> stats() {
            return ImmutableList.copyOf(allStats);
        }


        @Override
        public FailedPickUp.Action onShardPickUpFailure(RuntimeFailure failure) {
            failedToPickUp.add(failure.shard());
            return super.onShardPickUpFailure(failure);
        }

        @Override
        public FailedPickUp.Action onShardAlreadyPicked(AlreadyPickedUp failure) {
            alreadyPicked.add(failure.shard());
            return super.onShardAlreadyPicked(failure);
        }

        public ImmutableList<ShardIndex> alreadyPickedShards() {
            return ImmutableList.copyOf(alreadyPicked);
        }

        public ImmutableList<ShardIndex> failedToPickUp() {
            return ImmutableList.copyOf(failedToPickUp);
        }
    }
}
