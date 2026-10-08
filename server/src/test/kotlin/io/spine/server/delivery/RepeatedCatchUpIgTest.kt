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
import io.spine.server.delivery.CatchUpStatus.COMPLETED
import io.spine.server.delivery.InboxMessageStatus.TO_CATCH_UP
import io.spine.server.delivery.InboxMessageStatus.TO_DELIVER
import io.spine.server.delivery.given.CounterView
import io.spine.server.under
import io.spine.test.delivery.numberAdded
import io.spine.testing.SlowTest
import io.spine.testing.server.blackbox.BlackBox
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.jvm.optionals.getOrNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests the catch-up of the projection instances that were caught up before.
 *
 * The job of the completed catch-up stays in the storage, so the messages of
 * the new catch-up match two jobs.
 */
@SlowTest
@DisplayName("Repeated catch-up of a projection should")
internal class RepeatedCatchUpIgTest : AbstractDeliveryTest() {

    private val repository = CounterView.Repository()
    private val failures = CopyOnWriteArrayList<Throwable>()
    private val delivery = Delivery.newBuilder()
        .setStrategy(UniformAcrossAllShards.singleShard())
        .setDeduplicationWindow(Durations.ZERO)
        .build()

    /**
     * Uses a single-shard `Delivery`, which delivers synchronously through
     * `LocalDispatchingObserver`, and records what each delivery run throws.
     *
     * Otherwise, a failed run would go unnoticed: `Delivery` logs the failure
     * of a shard observer and goes on.
     */
    @BeforeEach
    fun useRecordingDelivery() {
        val dispatching = LocalDispatchingObserver()
        delivery.subscribe { message ->
            try {
                dispatching.onMessage(message)
            } catch (e: RuntimeException) {
                failures.add(e)
                throw e
            }
        }
        under<Tests> { use(delivery) }
    }

    @AfterEach
    fun resetWeight() {
        CounterView.changeWeightTo(1)
    }

    @Test
    fun `replay the history once and complete`() {
        BlackBox.singleTenantWith(repository).use { context ->
            val since = emitHistory(context)
            catchUpAllFirstTime(since)

            CounterView.changeWeightTo(100)
            repository.catchUpAll(since)
            context.receivesEvent(numberAdded { calculatorId = THIRD; value = 0 })

            assertSoftly {
                failures.shouldBeEmpty()
                jobStatuses() shouldBe listOf(COMPLETED, COMPLETED)
                totals(FIRST, SECOND, THIRD) shouldBe
                        mapOf(FIRST to 1000, SECOND to 1000, THIRD to 100)
                undelivered().shouldBeEmpty()
            }
        }
    }

    @Test
    fun `not hold back the live events of the instances it does not catch up`() {
        BlackBox.singleTenantWith(repository).use { context ->
            val since = emitHistory(context)
            catchUpAllFirstTime(since)

            CounterView.changeWeightTo(100)
            repository.catchUp(since, setOf(FIRST))
            context.receivesEvent(numberAdded { calculatorId = SECOND; value = 0 })

            assertSoftly {
                failures.shouldBeEmpty()
                jobStatuses() shouldBe listOf(COMPLETED, COMPLETED)
                totals(FIRST, SECOND) shouldBe mapOf(FIRST to 1000, SECOND to 200)
                undelivered().shouldBeEmpty()
            }
        }
    }

    /**
     * Emits [EVENTS_PER_TARGET] events to each of [FIRST] and [SECOND], weighing 1 each.
     *
     * Each event is posted live, so it gets a timestamp of its own. Then the function waits
     * until the events are older than the turbulence period of a catch-up. That way,
     * a catch-up replays them while `IN_PROGRESS`, rather than all at once while `FINALIZING`.
     *
     * @return The time since when to catch up, which precedes the events.
     */
    private fun emitHistory(context: BlackBox): Timestamp {
        CounterView.changeWeightTo(1)
        val since = subtract(currentTime(), Durations.fromMinutes(1))
        repeat(EVENTS_PER_TARGET) {
            listOf(FIRST, SECOND).forEach { id ->
                context.receivesEvent(numberAdded { calculatorId = id; value = 0 })
            }
        }
        Thread.sleep(PAST_TURBULENCE_MILLIS)
        return since
    }

    /**
     * Catches up all the instances, weighing each event 10, and checks the outcome.
     */
    private fun catchUpAllFirstTime(since: Timestamp) {
        CounterView.changeWeightTo(10)
        repository.catchUpAll(since)
        assertSoftly {
            failures.shouldBeEmpty()
            jobStatuses() shouldBe listOf(COMPLETED)
            totals(FIRST, SECOND) shouldBe mapOf(FIRST to 100, SECOND to 100)
        }
    }

    private fun jobStatuses(): List<CatchUpStatus> =
        delivery.catchUpStorage()
            .readByType(CounterView.projectionType())
            .asSequence()
            .map { it.status }
            .toList()

    /**
     * Returns the totals of the projections with the given IDs,
     * or `null` for those that do not exist.
     */
    private fun totals(vararg ids: String): Map<String, Int?> =
        ids.associateWith { id ->
            repository.find(id).getOrNull()?.state()?.total
        }

    /**
     * Returns the messages that are still to be delivered, either live or for a catch-up.
     */
    private fun undelivered(): List<InboxMessage> =
        InboxContents.get().values.flatten().filter {
            it.status == TO_DELIVER || it.status == TO_CATCH_UP
        }

    private companion object {
        const val FIRST = "first"
        const val SECOND = "second"
        const val THIRD = "third"
        const val EVENTS_PER_TARGET = 10

        /**
         * Exceeds the 500 ms turbulence period of a catch-up.
         */
        const val PAST_TURBULENCE_MILLIS = 1_000L
    }
}
