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

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.spine.server.delivery.CatchUpStatus.COMPLETED
import io.spine.server.delivery.given.CounterView
import io.spine.server.entity.TestTransaction
import io.spine.testing.SlowTest
import io.spine.testing.server.blackbox.BlackBox
import kotlin.jvm.optionals.getOrNull
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
internal class InactiveInstancesCatchUpIgTest : AbstractCatchUpIgTest() {

    @ParameterizedTest(name = WITH_SHARDS)
    @ValueSource(ints = [1, 3])
    fun `rebuild the archived and deleted instances from the history`(shards: Int) {
        useDelivery(UniformAcrossAllShards.forNumber(shards))
        BlackBox.singleTenantWith(repository).use { context ->
            val since = emitHistory(context, EVENTS_PER_TARGET, FIRST, SECOND, THIRD)
            changeStored(SECOND, TestTransaction::archive)
            changeStored(THIRD, TestTransaction::delete)

            repository.catchUpAll(since)

            assertSoftly {
                failures.shouldBeEmpty()
                jobStatuses() shouldBe listOf(COMPLETED)
                totals(*ALL_IDS.toTypedArray()) shouldBe mapOf(
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
            val since = emitHistory(context, EVENTS_PER_TARGET, SECOND, THIRD)
            changeStored(SECOND, TestTransaction::archive)
            changeStored(THIRD, TestTransaction::delete)

            repository.catchUpAll(since)

            assertSoftly {
                failures.shouldBeEmpty()
                jobStatuses() shouldBe listOf(COMPLETED)
                totals(*ALL_IDS.toTypedArray()) shouldBe mapOf(
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
            val since = emitHistory(context, EVENTS_PER_TARGET, FIRST)
            val fourth = repository.create(FOURTH)
            TestTransaction.archive(fourth)
            repository.store(fourth)

            repository.catchUpAll(since)

            assertSoftly {
                failures.shouldBeEmpty()
                jobStatuses() shouldBe listOf(COMPLETED)
                totals(*ALL_IDS.toTypedArray()) shouldBe mapOf(
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

    /**
     * Returns the IDs of the stored projections that are archived or deleted.
     */
    private fun inactive(): List<String> =
        ALL_IDS.filter { id ->
            repository.find(id).getOrNull()?.isActive == false
        }

    private companion object {
        const val FIRST = "first"
        const val SECOND = "second"
        const val THIRD = "third"
        const val FOURTH = "fourth"
        val ALL_IDS = listOf(FIRST, SECOND, THIRD, FOURTH)
        const val EVENTS_PER_TARGET = 5
        const val WITH_SHARDS = "with {0} shard(s)"
    }
}
