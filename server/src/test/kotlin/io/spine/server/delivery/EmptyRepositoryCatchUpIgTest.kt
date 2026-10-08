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
import com.google.protobuf.util.Timestamps.add
import com.google.protobuf.util.Timestamps.subtract
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.spine.base.Time.currentTime
import io.spine.server.BoundedContextBuilder
import io.spine.server.delivery.CatchUpStatus.COMPLETED
import io.spine.server.delivery.event.LiveEventsPickedUp
import io.spine.server.event.AbstractEventReactor
import io.spine.server.event.React
import io.spine.test.delivery.NumberAdded
import io.spine.test.delivery.numberAdded
import io.spine.testing.SlowTest
import io.spine.testing.server.TestEventFactory
import io.spine.testing.server.blackbox.BlackBox
import io.spine.type.TypeUrl
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Tests the catch-up of all the instances of a projection type, none of which is stored yet.
 *
 * Such a catch-up builds a new projection type from the event history. Where there is
 * a history, the tests append it to the `EventStore` directly, as if the events were emitted
 * before the projection type existed. Each event has a timestamp of its own.
 */
@SlowTest
@DisplayName("`catchUpAll()` of a projection with no stored instances should")
internal class EmptyRepositoryCatchUpIgTest : AbstractCatchUpIgTest() {

    @ParameterizedTest(name = WITH_SHARDS)
    @ValueSource(ints = [1, 3])
    fun `build the instances from the history`(shards: Int) {
        useDelivery(UniformAcrossAllShards.forNumber(shards))
        BlackBox.singleTenantWith(repository).use { context ->
            appendHistory(context)

            repository.catchUpAll(anHourAgo())
            context.receivesEvent(numberAdded { calculatorId = FIRST })
            context.receivesEvent(numberAdded { calculatorId = THIRD })

            assertSoftly {
                failures.shouldBeEmpty()
                jobStatuses() shouldBe listOf(COMPLETED)
                totals(FIRST, SECOND, THIRD) shouldBe mapOf(
                    FIRST to EVENTS_PER_TARGET + 1,
                    SECOND to EVENTS_PER_TARGET,
                    THIRD to 1
                )
                undelivered().shouldBeEmpty()
            }
        }
    }

    @ParameterizedTest(name = WITH_SHARDS)
    @ValueSource(ints = [1, 3])
    fun `complete when there is no history`(shards: Int) {
        useDelivery(UniformAcrossAllShards.forNumber(shards))
        BlackBox.singleTenantWith(repository).use { context ->
            repository.catchUpAll(anHourAgo())
            context.receivesEvent(numberAdded { calculatorId = THIRD })

            assertSoftly {
                failures.shouldBeEmpty()
                jobStatuses() shouldBe listOf(COMPLETED)
                totals(FIRST, SECOND, THIRD) shouldBe
                        mapOf(FIRST to null, SECOND to null, THIRD to 1)
                undelivered().shouldBeEmpty()
            }
        }
    }

    @Test
    fun `deliver a live event held back while it finalizes`() {
        useDelivery(ThirdApart())
        val builder = BoundedContextBuilder.assumingTests()
            .add(repository)
            .addEventDispatcher(EmitLiveEventWhenFinalizing())
        BlackBox.from(builder).use { context ->
            appendHistory(context)

            repository.catchUpAll(anHourAgo())

            assertSoftly {
                failures.shouldBeEmpty()
                jobStatuses() shouldBe listOf(COMPLETED)
                totals(FIRST, SECOND, THIRD) shouldBe mapOf(
                    FIRST to EVENTS_PER_TARGET,
                    SECOND to EVENTS_PER_TARGET,
                    THIRD to 1
                )
                undelivered().shouldBeEmpty()
            }
        }
    }

    /**
     * Appends [EVENTS_PER_TARGET] events for each of [FIRST] and [SECOND] to the `EventStore`,
     * bypassing the repository.
     *
     * The events are a second apart, starting ten minutes ago.
     */
    private fun appendHistory(context: BlackBox) {
        val factory = TestEventFactory.newInstance(javaClass)
        val tenMinutesAgo = subtract(currentTime(), Durations.fromMinutes(10))
        val targets = List(EVENTS_PER_TARGET) { listOf(FIRST, SECOND) }.flatten()
        targets.forEachIndexed { seconds, target ->
            val message = numberAdded { calculatorId = target }
            val stamp = add(tenMinutesAgo, Durations.fromSeconds(seconds.toLong()))
            context.append(factory.createEvent(message, null, stamp))
        }
    }

    /**
     * Assigns [THIRD] to the first of three shards, and anything else, the catch-up process
     * included, to the second one.
     *
     * So the historical events of [FIRST] and [SECOND] never reach the shard of [THIRD].
     * That shard runs again after the catch-up completes only if the catch-up treats every
     * shard as affected.
     */
    private class ThirdApart : DeliveryStrategy() {

        override fun indexFor(entityId: Any, entityStateType: TypeUrl): ShardIndex =
            newIndex(if (entityId == THIRD) 0 else 1, shardCount())

        override fun shardCount(): Int = 3
    }

    /**
     * Emits a live event to [THIRD] when the catch-up has read the history to its end.
     *
     * The catch-up emits `LiveEventsPickedUp` after its last read of the history, while
     * `FINALIZING`. So the catch-up does not replay this live event, and `Delivery` holds it
     * back until the catch-up completes.
     */
    private class EmitLiveEventWhenFinalizing : AbstractEventReactor() {

        @React
        fun on(@Suppress("UNUSED_PARAMETER") event: LiveEventsPickedUp): NumberAdded =
            numberAdded { calculatorId = THIRD }
    }

    private companion object {
        const val FIRST = "first"
        const val SECOND = "second"
        const val THIRD = "third"
        const val EVENTS_PER_TARGET = 5
        const val WITH_SHARDS = "with {0} shard(s)"

        fun anHourAgo(): Timestamp = subtract(currentTime(), Durations.fromHours(1))
    }
}
