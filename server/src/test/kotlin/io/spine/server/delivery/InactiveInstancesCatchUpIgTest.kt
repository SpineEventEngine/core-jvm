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

package io.spine.server.delivery

import com.google.protobuf.Timestamp
import com.google.protobuf.util.Durations
import com.google.protobuf.util.Timestamps.subtract
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.spine.base.Time.currentTime
import io.spine.environment.Tests
import io.spine.server.ServerEnvironment
import io.spine.server.delivery.CatchUpStatus.COMPLETED
import io.spine.server.delivery.InboxMessageStatus.TO_CATCH_UP
import io.spine.server.delivery.InboxMessageStatus.TO_DELIVER
import io.spine.server.delivery.given.CounterView
import io.spine.server.entity.TestTransaction
import io.spine.server.under
import io.spine.test.delivery.numberAdded
import io.spine.testing.SlowTest
import io.spine.testing.server.blackbox.BlackBox
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.jvm.optionals.getOrNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Tests the catch-up of all the instances of a projection type, some of which are archived
 * or deleted.
 *
 * Such a catch-up resets every stored instance, whatever its lifecycle flags, and rebuilds
 * it from the replayed history. So an archived or deleted instance comes back active, and no
 * event is applied to it twice.
 *
 * The tests emit the history as live events, so the instances are stored with the state
 * it builds. Then they archive or delete some of the instances.
 */
@SlowTest
@DisplayName("`catchUpAll()` of a projection with archived or deleted instances should")
internal class InactiveInstancesCatchUpIgTest : AbstractDeliveryTest() {

    private val repository = CounterView.Repository()

    /**
     * What failed during the delivery: the delivery runs and the receptors.
     */
    private val failures = CopyOnWriteArrayList<String>()

    /**
     * Restores the default weight of an event, which `CounterView` keeps in a static field,
     * and other suites leave changed.
     */
    @BeforeEach
    fun weighEachEventOne() {
        CounterView.changeWeightTo(1)
    }

    @ParameterizedTest(name = WITH_SHARDS)
    @ValueSource(ints = [1, 3])
    fun `rebuild the archived and deleted instances from the history`(shards: Int) {
        useDelivery(UniformAcrossAllShards.forNumber(shards))
        BlackBox.singleTenantWith(repository).use { context ->
            val since = emitHistory(context, FIRST, SECOND, THIRD)
            changeStored(SECOND, TestTransaction::archive)
            changeStored(THIRD, TestTransaction::delete)

            repository.catchUpAll(since)

            assertSoftly {
                failures.shouldBeEmpty()
                jobStatuses() shouldBe listOf(COMPLETED)
                totals() shouldBe mapOf(
                    FIRST to EVENTS_PER_TARGET,
                    SECOND to EVENTS_PER_TARGET,
                    THIRD to EVENTS_PER_TARGET,
                    FOURTH to null
                )
                inactive().shouldBeEmpty()
                undelivered().shouldBeEmpty()
            }
        }
    }

    @ParameterizedTest(name = WITH_SHARDS)
    @ValueSource(ints = [1, 3])
    fun `rebuild the instances when none of them is active`(shards: Int) {
        useDelivery(UniformAcrossAllShards.forNumber(shards))
        BlackBox.singleTenantWith(repository).use { context ->
            val since = emitHistory(context, SECOND, THIRD)
            changeStored(SECOND, TestTransaction::archive)
            changeStored(THIRD, TestTransaction::delete)

            repository.catchUpAll(since)

            assertSoftly {
                failures.shouldBeEmpty()
                jobStatuses() shouldBe listOf(COMPLETED)
                totals() shouldBe mapOf(
                    FIRST to null,
                    SECOND to EVENTS_PER_TARGET,
                    THIRD to EVENTS_PER_TARGET,
                    FOURTH to null
                )
                inactive().shouldBeEmpty()
                undelivered().shouldBeEmpty()
            }
        }
    }

    @Test
    fun `remove an inactive instance that the history does not reach`() {
        useDelivery(UniformAcrossAllShards.singleShard())
        BlackBox.singleTenantWith(repository).use { context ->
            val since = emitHistory(context, FIRST)
            val fourth = repository.create(FOURTH)
            TestTransaction.archive(fourth)
            repository.store(fourth)

            repository.catchUpAll(since)

            assertSoftly {
                failures.shouldBeEmpty()
                jobStatuses() shouldBe listOf(COMPLETED)
                totals() shouldBe mapOf(
                    FIRST to EVENTS_PER_TARGET,
                    SECOND to null,
                    THIRD to null,
                    FOURTH to null
                )
                undelivered().shouldBeEmpty()
            }
        }
    }

    /**
     * Installs a `Delivery` that uses the given strategy, delivers synchronously
     * through `LocalDispatchingObserver`, and records each failure.
     *
     * Otherwise, a failure would go unnoticed: `Delivery` only logs what a shard observer
     * throws, and `DeliveryMonitor` marks a message failed by its receptor as delivered.
     */
    private fun useDelivery(strategy: DeliveryStrategy) {
        val delivery = Delivery.newBuilder()
            .setStrategy(strategy)
            .setDeduplicationWindow(Durations.ZERO)
            .setMonitor(FailureRecorder())
            .build()
        val dispatching = LocalDispatchingObserver()
        delivery.subscribe { message ->
            try {
                dispatching.onMessage(message)
            } catch (e: Exception) {
                failures.add("Delivery run: $e")
                throw e
            }
        }
        under<Tests> { use(delivery) }
    }

    /**
     * Emits [EVENTS_PER_TARGET] events to each of the given projections.
     *
     * Each event is posted live, so it gets a timestamp of its own. Then the function waits
     * until the events are older than the turbulence period of a catch-up. That way,
     * a catch-up replays them while `IN_PROGRESS`, rather than all at once while `FINALIZING`.
     *
     * @return The time since when to catch up, which precedes the events.
     */
    private fun emitHistory(context: BlackBox, vararg ids: String): Timestamp {
        val since = subtract(currentTime(), Durations.fromMinutes(1))
        repeat(EVENTS_PER_TARGET) {
            ids.forEach { id ->
                context.receivesEvent(numberAdded { calculatorId = id })
            }
        }
        Thread.sleep(PAST_TURBULENCE_MILLIS)
        return since
    }

    /**
     * Applies the given change to the stored projection with the given ID,
     * then stores the projection back.
     */
    private fun changeStored(id: String, change: (CounterView) -> Unit) {
        val projection = checkNotNull(repository.find(id).getOrNull()) {
            "The projection `$id` is not stored."
        }
        change(projection)
        repository.store(projection)
    }

    private fun jobStatuses(): List<CatchUpStatus> =
        ServerEnvironment.instance()
            .delivery()
            .catchUpStorage()
            .readByType(CounterView.projectionType())
            .asSequence()
            .map { it.status }
            .toList()

    /**
     * Returns the totals of the projections, or `null` for those that are not stored.
     */
    private fun totals(): Map<String, Int?> =
        ALL_IDS.associateWith { id ->
            repository.find(id).getOrNull()?.state()?.total
        }

    /**
     * Returns the IDs of the stored projections that are archived or deleted.
     */
    private fun inactive(): List<String> =
        ALL_IDS.filter { id ->
            repository.find(id).getOrNull()?.isActive == false
        }

    /**
     * Returns the messages that are still to be delivered, either live or for a catch-up.
     */
    private fun undelivered(): List<InboxMessage> =
        InboxContents.get().values.flatten().filter {
            it.status == TO_DELIVER || it.status == TO_CATCH_UP
        }

    /**
     * Records the receptor failures, which `Delivery` does not throw.
     */
    private inner class FailureRecorder : DeliveryMonitor() {

        override fun onReceptionFailure(reception: FailedReception): FailedReception.Action {
            failures.add("Receptor: ${reception.error().message}")
            return super.onReceptionFailure(reception)
        }
    }

    private companion object {
        const val FIRST = "first"
        const val SECOND = "second"
        const val THIRD = "third"
        const val FOURTH = "fourth"
        val ALL_IDS = listOf(FIRST, SECOND, THIRD, FOURTH)
        const val EVENTS_PER_TARGET = 5
        const val WITH_SHARDS = "with {0} shard(s)"

        /**
         * Exceeds the 500 ms turbulence period of a catch-up.
         */
        const val PAST_TURBULENCE_MILLIS = 1_000L
    }
}
