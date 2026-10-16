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

import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.spine.core.TenantId
import io.spine.environment.Tests
import io.spine.server.delivery.InboxMessageStatus.TO_DELIVER
import io.spine.server.delivery.given.CounterView
import io.spine.server.tenant.TenantAwareRunner
import io.spine.server.under
import io.spine.test.delivery.numberAdded
import io.spine.testing.server.blackbox.BlackBox
import kotlin.jvm.optionals.getOrNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`Delivery` should")
internal class DeliverySpec : AbstractDeliveryTest() {

    @AfterEach
    fun restoreDefaultWeight() {
        CounterView.changeWeightTo(1)
    }

    @Test
    fun `stop delivering a shard once the monitor tells to`() {
        // No shard observer, so the messages wait in the shard until delivered below.
        val delivery = Delivery.newBuilder()
            .setStrategy(UniformAcrossAllShards.singleShard())
            .setPageSize(1)
            .setMonitor(StopAfterEachPage())
            .build()
        under<Tests> { use(delivery) }
        // The weight is static, and `CatchUpTest` leaves it at 100.
        CounterView.changeWeightTo(1)
        val repository = CounterView.Repository()
        BlackBox.singleTenantWith(repository).use { box ->
            repeat(MESSAGES) {
                box.receivesEvent(numberAdded { calculatorId = TARGET; value = 0 })
            }

            val stats = TenantAwareRunner.with(TenantId.getDefaultInstance()).evaluate {
                delivery.deliverMessagesFrom(SHARD)
            }

            stats.shouldBePresent().deliveredCount() shouldBe 1
            repository.find(TARGET).getOrNull()?.state()?.total shouldBe 1
            InboxContents.get().values.flatten().count { it.status == TO_DELIVER } shouldBe
                    MESSAGES - 1
        }
    }

    /**
     * Stops a delivery run after its first page.
     */
    private class StopAfterEachPage : DeliveryMonitor() {

        override fun shouldContinueAfter(stage: DeliveryStage): Boolean = false
    }

    private companion object {
        const val TARGET = "target"
        const val MESSAGES = 3
        val SHARD: ShardIndex = DeliveryStrategy.newIndex(0, 1)
    }
}
