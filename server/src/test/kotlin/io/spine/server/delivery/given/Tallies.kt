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

package io.spine.server.delivery.given

import io.spine.core.Subscribe
import io.spine.server.projection.Projection
import io.spine.server.projection.ProjectionRepository
import io.spine.server.route.EventRouting
import io.spine.test.delivery.ConsecutiveNumberView
import io.spine.test.delivery.DCounter
import io.spine.test.delivery.NumberAdded
import io.spine.type.TypeUrl

/**
 * Sums up the [NumberAdded] events of a calculator, weighing each with [weight].
 *
 * Together with [SecondTally], forms a pair of projection types whose instances share IDs
 * and count the same events.
 */
internal class FirstTally : Projection<String, DCounter, DCounter.Builder>() {

    @Subscribe
    fun on(@Suppress("UNUSED_PARAMETER") event: NumberAdded) = alter {
        total += weight
    }

    companion object {

        /**
         * The weight of each event. A test changes it before a catch-up.
         */
        @Volatile
        var weight: Int = 1

        val type: TypeUrl = TypeUrl.of(DCounter::class.java)
    }
}

/**
 * Routes the [NumberAdded] events to the [FirstTally] of their calculator.
 */
internal class FirstTallyRepository : ProjectionRepository<String, FirstTally, DCounter>() {

    override fun setupEventRouting(routing: EventRouting<String>) {
        super.setupEventRouting(routing)
        routing.unicast<NumberAdded> { it.calculatorId }
    }
}

/**
 * Sums up the [NumberAdded] events of a calculator, weighing each with [weight].
 *
 * Keeps the sum as the last value of a [ConsecutiveNumberView], so that its state type
 * differs from that of [FirstTally].
 */
internal class SecondTally :
    Projection<String, ConsecutiveNumberView, ConsecutiveNumberView.Builder>() {

    @Subscribe
    fun on(@Suppress("UNUSED_PARAMETER") event: NumberAdded) = alter {
        lastValue += weight
    }

    companion object {

        /**
         * The weight of each event. A test changes it before a catch-up.
         */
        @Volatile
        var weight: Int = 1

        val type: TypeUrl = TypeUrl.of(ConsecutiveNumberView::class.java)
    }
}

/**
 * Routes the [NumberAdded] events to the [SecondTally] of their calculator.
 */
internal class SecondTallyRepository :
    ProjectionRepository<String, SecondTally, ConsecutiveNumberView>() {

    override fun setupEventRouting(routing: EventRouting<String>) {
        super.setupEventRouting(routing)
        routing.unicast<NumberAdded> { it.calculatorId }
    }
}
