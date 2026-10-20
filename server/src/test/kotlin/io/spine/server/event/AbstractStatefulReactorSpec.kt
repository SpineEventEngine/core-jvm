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

package io.spine.server.event

import com.google.common.collect.ImmutableSet
import io.kotest.matchers.shouldBe
import io.spine.environment.Tests
import io.spine.server.BoundedContextBuilder
import io.spine.server.delivery.AbstractDeliveryTest
import io.spine.server.delivery.Delivery
import io.spine.server.type.EventEnvelope
import io.spine.server.under
import io.spine.test.delivery.DCounter
import io.spine.test.delivery.NumberAdded
import io.spine.test.delivery.numberAdded
import io.spine.testing.server.blackbox.BlackBox
import io.spine.type.TypeUrl
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`AbstractStatefulReactor` should")
internal class AbstractStatefulReactorSpec : AbstractDeliveryTest() {

    @Test
    fun `dispatch the events delivered through its inbox to itself, by default`() {
        under<Tests> { use(Delivery.local()) }
        val reactor = CountingReactor()
        val setup = BoundedContextBuilder.assumingTests()
            .addEventDispatcher(reactor)
        BlackBox.from(setup).use { context ->
            repeat(2) {
                context.receivesEvent(numberAdded { calculatorId = CALCULATOR })
            }

            reactor.totalOf(CALCULATOR) shouldBe 2
        }
    }

    private companion object {
        const val CALCULATOR = "calculator"
    }
}

/**
 * Counts the [NumberAdded] events of each calculator, keeping the counters in memory.
 *
 * Keeps the default choice of the reactor receiving an event, so it dispatches each event
 * delivered through its inbox to itself.
 */
private class CountingReactor :
    AbstractStatefulReactor<String, DCounter, DCounter.Builder>(TypeUrl.of(DCounter::class.java)) {

    private val counters = ConcurrentHashMap<String, DCounter>()

    @React
    fun on(@Suppress("UNUSED_PARAMETER") event: NumberAdded): NoReaction {
        builder().total += 1
        return noReaction()
    }

    fun totalOf(calculator: String): Int? = counters[calculator]?.total

    override fun load(id: String): Optional<DCounter> = Optional.ofNullable(counters[id])

    override fun store(updatedState: DCounter) {
        counters[updatedState.id] = updatedState
    }

    override fun newStateBuilderWith(id: String): DCounter.Builder =
        DCounter.newBuilder().setId(id)

    override fun route(event: EventEnvelope): ImmutableSet<String> {
        val message = event.message() as NumberAdded
        return ImmutableSet.of(message.calculatorId)
    }
}
