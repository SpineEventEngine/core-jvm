/*
 * Copyright 2025, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Redistribution and use in source and/or binary forms, with or without
 * modification, must retain the above copyright notice and the following
 * disclaimer.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.spine.server.route

import io.kotest.matchers.shouldBe
import io.spine.server.given.context.sorting.FigureStats
import io.spine.server.given.context.sorting.FigureStatsView
import io.spine.server.given.context.sorting.Sorter
import io.spine.server.given.context.sorting.SorterKt.bucket
import io.spine.server.given.context.sorting.SorterView
import io.spine.server.given.context.sorting.command.generateFigures
import io.spine.server.given.context.sorting.createSortingContext
import io.spine.testing.server.blackbox.assertEntity
import io.spine.server.given.context.sorting.figure
import io.spine.server.given.context.sorting.sorter
import io.spine.testing.server.blackbox.BlackBox
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Static event routing should")
internal class StaticEventRoutingITest {

    /**
     * This test verifies that events are routed via static methods defined in
     * [SorterView] and [FigureStatsView] entity classes.
     *
     * The test fires the
     * [io.spine.server.given.context.sorting.command.GenerateFigures] command and then
     * verifies that corresponding views have the expected state, which means that
     * expected events were successfully delivered.
     *
     * @see createSortingContext
     */
    @Test
    fun `route an event via a static method of an entity class`() {
        // The list of figures to generate.
        val figures = listOf(
            figure { circle = true },
            figure { triangle = true },
            figure { square = true }
        )
        // The number of times each figure should be repeated.
        val times = 3

        BlackBox.from(createSortingContext()).use { context ->
            context.receivesCommand(
                generateFigures {
                    figure.addAll(figures)
                    count = times
                }
            )

            // The expected state of the `Sorter` view.
            val expected = sorter {
                id = SorterView.SINGLETON_ID
                val buckets = figures.map { f ->
                    bucket {
                        repeat(times) {
                            figure.add(f)
                        }
                    }
                }
                bucket.addAll(buckets)
            }

            val sorter = context.assertEntity<SorterView, _>(SorterView.SINGLETON_ID)
                .actual()?.state() as Sorter

            sorter.bucketList.toSet() shouldBe expected.bucketList.toSet()

            figures.forEach { figure ->
                // Each `FigureStats` view should contain the expected number.
                val stats = context.assertEntity<FigureStatsView, _>(figure)
                    .actual()?.state() as FigureStats
                stats.count shouldBe times
            }
        }
    }
}
