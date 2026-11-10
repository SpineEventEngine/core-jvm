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

package io.spine.server.given.context.counting

import io.spine.core.Subscribe
import io.spine.server.BoundedContext
import io.spine.server.BoundedContext.singleTenant
import io.spine.server.command.Assign
import io.spine.server.command.SingleCommandAssignee
import io.spine.server.given.context.counting.command.GenerateNumbers
import io.spine.server.given.context.counting.event.NumberGenerated
import io.spine.server.given.context.counting.event.numberGenerated
import io.spine.server.projection.Projection
import io.spine.server.projection.ProjectionRepository
import io.spine.server.route.EventRouting
import io.spine.testing.TestValues.random
import io.spine.testing.server.buildForTests

/**
 * The Counting context generates random numbers in a range and accumulates
 * statistics in [RangeStatsView] and [NumberStatsView] projects.
 *
 * The numbers are generated in response to [GenerateNumbers] command, which is
 * handled by [RandomNumberGenerator].
 *
 * This context is a test fixture for [io.spine.server.query.QueryingClientSpec].
 *
 * ### Implementation note
 *
 * The implementation of this context is deliberately naïve in terms of event generation
 * and propagation. It generates many small events for each generated figure.
 *
 * If a number of events is big, it leads to an increased load to a data storage because we need to
 * load and store corresponding entity states. It is not noticeable for this test fixture
 * arrangement because in-memory storage is used.
 *
 * A production implementation of similar cases should prefer a bigger event containing
 * all information (provided [size limit](https://stackoverflow.com/a/34186672) is met),
 * or the series of events containing chunks of information.
 */
@Suppress("unused") // is declared for documentation purposes.
private const val ABOUT = ""

/**
 * Generates random numbers taking the parameter from the [GenerateNumbers] command.
 */
private class RandomNumberGenerator: SingleCommandAssignee<GenerateNumbers>() {

    @Assign
    override fun handle(command: GenerateNumbers): Iterable<NumberGenerated> {
        val events = generateSequence {
            with(command.range) {
                random(minValue, maxValue)
            }
        }.map { generated ->
            numberGenerated {
                number = generated
                range = command.range
            }
        }.take(command.count).toList()
        return events
    }
}

/**
 * Accumulates number stats per range in response to [NumberGenerated] event.
 */
private class RangeStatsView: Projection<Range, RangeStats, RangeStats.Builder>() {

    @Subscribe
    fun whenever(event: NumberGenerated) = alter {
        range = event.range
        val number = event.number
        val currentCount = countMap[number]
        val newValue = currentCount?.inc() ?: 1
        putCount(number, newValue)
    }
}

/**
 * Customized event routing so that [RangeStatsView] gets its events.
 */
private class RangeStatsRepository : ProjectionRepository<Range, RangeStatsView, RangeStats>() {

    override fun setupEventRouting(routing: EventRouting<Range>) {
        super.setupEventRouting(routing)
        routing.unicast(NumberGenerated::class.java) { e, _ ->
            e.range
        }
    }
}

/**
 * Counts a number of times a number was generated.
 */
private class NumberStatsView: Projection<Int, NumberStats, NumberStats.Builder>() {

    @Subscribe
    fun whenever(event: NumberGenerated) = alter {
        number = event.number
        count = count.inc()
    }
}

/**
 * Tunes event routing so that [NumberStatsView] get its events.
 */
private class NumberStatsRepository : ProjectionRepository<Int, NumberStatsView, NumberStats>() {

    override fun setupEventRouting(routing: EventRouting<Int>) {
        super.setupEventRouting(routing)
        routing.unicast(NumberGenerated::class.java) { event, _ ->
            event.number
        }
    }
}

/**
 * Creates Counting bounded context.
 */
fun createCountingContext(): BoundedContext = singleTenant("Counting").apply {
    addAssignee(RandomNumberGenerator())
    add(RangeStatsRepository())
    add(NumberStatsRepository())
}.buildForTests()
