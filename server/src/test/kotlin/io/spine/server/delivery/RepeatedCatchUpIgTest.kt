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
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.spine.server.delivery.CatchUpStatus.COMPLETED
import io.spine.server.delivery.given.CounterView
import io.spine.test.delivery.numberAdded
import io.spine.testing.SlowTest
import io.spine.testing.server.blackbox.BlackBox
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
internal class RepeatedCatchUpIgTest : AbstractCatchUpIgTest() {

    @BeforeEach
    fun useSingleShard() {
        useDelivery(UniformAcrossAllShards.singleShard())
    }

    @Test
    fun `replay the history once and complete`() {
        BlackBox.singleTenantWith(repository).use { context ->
            val since = emitHistory(context, EVENTS_PER_TARGET, FIRST, SECOND)
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
            val since = emitHistory(context, EVENTS_PER_TARGET, FIRST, SECOND)
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

    private companion object {
        const val FIRST = "first"
        const val SECOND = "second"
        const val THIRD = "third"
        const val EVENTS_PER_TARGET = 10
    }
}
