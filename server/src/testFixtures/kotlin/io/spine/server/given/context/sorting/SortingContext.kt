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

package io.spine.server.given.context.sorting

import io.spine.annotation.VisibleForTesting
import io.spine.base.EventMessage
import io.spine.core.EventContext
import io.spine.core.Subscribe
import io.spine.server.BoundedContextBuilder
import io.spine.server.BoundedContextBuilder.assumingTests
import io.spine.server.command.Assign
import io.spine.server.command.SingleCommandAssignee
import io.spine.server.given.context.sorting.SorterKt.bucket
import io.spine.server.given.context.sorting.command.GenerateFigures
import io.spine.server.given.context.sorting.event.FigureGenerated
import io.spine.server.given.context.sorting.event.figureGenerated
import io.spine.server.projection.Projection
import io.spine.server.route.Route

/**
 * Creates the builder of a toy context that generates geometric figures in response to
 * the [GenerateFigures] command.
 *
 * Figures are accumulated by buckets by the [SorterView] projection.
 * Statistics for figures are handled by [FigureStatsView].
 *
 * ### Implementation note
 *
 * The implementation of this context is deliberately naïve in terms of event generation
 * and propagation. It generates an event for each generated figure.
 *
 * If a number of events is big, it leads to an increased load to a data storage because
 * we need to load and store corresponding entity states.
 * It is not noticeable for this test fixture arrangement because in-memory storage is used.
 *
 * A production implementation of similar cases should prefer a bigger event containing
 * all information (provided [size limit](https://stackoverflow.com/a/34186672) is met),
 * or a series of events containing chunks of information.
 *
 * @see io.spine.server.given.context.fizzbuzz.createFizzBuzzContext
 */
fun createSortingContext(): BoundedContextBuilder = assumingTests("Sorting").apply {
    addAssignee(FigureGenerator())
    add(SorterView::class.java)
    add(FigureStatsView::class.java)
}

/**
 * Generates geometric figures taking the parameters from the [GenerateFigures] command.
 */
private class FigureGenerator: SingleCommandAssignee<GenerateFigures>() {

    @Assign
    override fun handle(command: GenerateFigures): Iterable<FigureGenerated> {
        val events = sequence<Figure> {
            command.figureList.forEach { figure ->
                repeat(command.count) {
                    yield(figure)
                }
            }
        }.map {
            figureGenerated {
                figure = it
            }
        }.toList()
        return events
    }
}

/**
 * A singleton accumulating figures in buckets corresponding to their kinds
 * in response to [FigureGenerated] event.
 */
@VisibleForTesting
class SorterView: Projection<String, Sorter, Sorter.Builder>() {

    @Subscribe
    internal fun whenever(event: FigureGenerated) = alter {
        val figure = event.figure
        fun newBucket() = bucket { this@bucket.figure.add(figure) }

        // Find a bucket with the same kind of figures.
        val existingBucket = bucketBuilderList.find { bucket ->
            bucket.figureList.first().kindCase == figure.kindCase
        }
        if (existingBucket == null) {
            addBucket(newBucket())
            return@alter
        } else {
            existingBucket.addFigure(figure)
        }
    }

    companion object {

        const val SINGLETON_ID = "sorter"

        /**
         * The routing function accepting only one parameter.
         *
         * It also accepts an interface, rather than an event message class.
         */
        @Route
        @JvmStatic
        fun toSingleton(@Suppress("UNUSED_PARAMETER") e: EventMessage): String = SINGLETON_ID
    }
}

/**
 * Counts a number of times a figure was generated.
 */
@VisibleForTesting
class FigureStatsView: Projection<Figure, FigureStats, FigureStats.Builder>() {

    @Subscribe
    internal fun whenever(event: FigureGenerated) = alter {
        figure = event.figure
        count = count.inc()
    }

    companion object {

        /**
         * The routing function with the second parameter.
         */
        @Route
        @JvmStatic
        @Suppress("UNUSED_PARAMETER")
        fun byFigure(e: FigureGenerated, ctx: EventContext): Figure = e.figure
    }
}

