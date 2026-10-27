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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.spine.server.BoundedContextBuilder
import io.spine.server.delivery.given.HoldingExecutor
import io.spine.testing.SlowTest
import io.spine.testing.server.blackbox.BlackBox
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests that a catch-up is recorded by the time `catchUp()` returns, when the System context
 * posts its events in parallel, as it does in production.
 *
 * The request of a catch-up is a system event. If it were posted in parallel, `catchUp()`
 * could return before the job of the catch-up is stored. Then a second call for the same
 * instances would not see the first job, and two catch-ups would rebuild the same projections.
 *
 * The executor of this test holds each system event posted in parallel. So the job waits for
 * the report of its target, which is a system event too, and stays started.
 */
@SlowTest
@DisplayName("Catch-up, requested while the system events are posted in parallel, should")
internal class CatchUpRequestIgTest : AbstractCatchUpIgTest() {

    private val systemEvents = HoldingExecutor()

    @Test
    fun `reject a catch-up of the same instances requested right after it`() {
        useDelivery(UniformAcrossAllShards.singleShard())
        val builder = BoundedContextBuilder.assumingTests()
            .add(repository)
        builder.systemSettings()
            .enableParallelPosting()
            .useCustomPostingExecutor(systemEvents)
        BlackBox.from(builder).use { box ->
            val since = emitHistory(box, eventsPerTarget = 1, TARGET)
            systemEvents.hold()
            repository.catchUp(since, setOf(TARGET))

            val error = shouldThrow<CatchUpAlreadyStartedException> {
                repository.catchUp(since, setOf(TARGET))
            }
            error.requestedIds() shouldBe setOf(TARGET)
        }
    }

    private companion object {
        const val TARGET = "target"
    }
}
