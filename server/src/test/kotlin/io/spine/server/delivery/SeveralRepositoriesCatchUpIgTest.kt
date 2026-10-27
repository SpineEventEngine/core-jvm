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
import com.google.protobuf.util.Timestamps.subtract
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.spine.base.Time.currentTime
import io.spine.server.delivery.CatchUpStatus.COMPLETED
import io.spine.server.delivery.given.TallyA1Repository
import io.spine.server.delivery.given.TallyA2Repository
import io.spine.server.delivery.given.TallyB1Repository
import io.spine.server.delivery.given.TallyContexts
import io.spine.server.delivery.given.TallyRepository
import io.spine.server.delivery.given.TallyWeight
import io.spine.testing.SlowTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Tests the catch-up of a projection type, when the `Delivery` of the JVM serves
 * more than one projection repository, or more than one Bounded Context.
 *
 * When one catch-up process served all the repositories of a JVM, the process created last
 * handled every catch-up, and some catch-ups never completed. These scenarios check that each
 * System context handles the catch-ups of its own projections, whatever the order in which
 * the repositories and the contexts are created.
 *
 * Each tally of a context gets the same events. A test changes the weight of the values,
 * catches up one repository, and posts a live event. Then the caught-up tallies hold all their
 * values at the new weight, and the others hold their history at the old one.
 */
@SlowTest
@DisplayName("Catch-up of a projection that shares the `Delivery` with others should")
internal class SeveralRepositoriesCatchUpIgTest : AbstractCatchUpIgTest() {

    private val contexts = TallyContexts()

    @BeforeEach
    @AfterEach
    fun restoreTallyWeight() {
        TallyWeight.value = 1
    }

    @AfterEach
    fun closeContexts() {
        contexts.close()
    }

    @ParameterizedTest(name = WITH_SHARDS)
    @ValueSource(ints = [1, 3])
    fun `rebuild the tallies of the repository registered last`(shards: Int) {
        useDelivery(UniformAcrossAllShards.forNumber(shards))
        val first = TallyA1Repository()
        val last = TallyB1Repository()
        contexts.create(CONTEXT, first, last)

        catchUpAndCheck(caughtUp = listOf(last), notCaughtUp = listOf(first)) {
            last.catchUpAll(anHourAgo())
        }
    }

    @ParameterizedTest(name = WITH_SHARDS)
    @ValueSource(ints = [1, 3])
    fun `rebuild the tallies of a repository registered before another one`(shards: Int) {
        useDelivery(UniformAcrossAllShards.forNumber(shards))
        val first = TallyA1Repository()
        val last = TallyB1Repository()
        contexts.create(CONTEXT, first, last)

        catchUpAndCheck(caughtUp = listOf(first), notCaughtUp = listOf(last)) {
            first.catchUpAll(anHourAgo())
        }
    }

    @ParameterizedTest(name = WITH_SHARDS)
    @ValueSource(ints = [1, 3])
    fun `rebuild the selected tallies of a repository registered before another one`(
        shards: Int
    ) {
        useDelivery(UniformAcrossAllShards.forNumber(shards))
        val first = TallyA1Repository()
        val last = TallyB1Repository()
        contexts.create(CONTEXT, first, last)
        contexts.emit(CONTEXT, ALPHA, BETA)
        waitPastTurbulence()

        TallyWeight.value = CAUGHT_UP_WEIGHT
        first.catchUp(anHourAgo(), setOf(ALPHA))
        contexts.post(CONTEXT, ALPHA, live = true)

        assertSoftly {
            failures.shouldBeEmpty()
            jobStatuses(first.entityStateType()) shouldBe listOf(COMPLETED)
            withClue("The selected tally") {
                first.valuesOf(ALPHA) shouldBe contexts.caughtUp(
                    context = CONTEXT, id = ALPHA, weight = CAUGHT_UP_WEIGHT
                )
            }
            withClue("The tally that is not selected") {
                first.valuesOf(BETA) shouldBe contexts.notCaughtUp(
                    context = CONTEXT, id = BETA, weight = CAUGHT_UP_WEIGHT
                )
            }
            last.shouldNotBeCaughtUp(CONTEXT)
            undelivered().shouldBeEmpty()
        }
    }

    @ParameterizedTest(name = WITH_SHARDS)
    @ValueSource(ints = [1, 3])
    fun `rebuild the tallies of both repositories, one after the other`(shards: Int) {
        useDelivery(UniformAcrossAllShards.forNumber(shards))
        val first = TallyA1Repository()
        val last = TallyB1Repository()
        contexts.create(CONTEXT, first, last)

        catchUpAndCheck(caughtUp = listOf(first, last), notCaughtUp = listOf()) {
            first.catchUpAll(anHourAgo())
            last.catchUpAll(anHourAgo())
        }
    }

    @ParameterizedTest(name = WITH_SHARDS)
    @ValueSource(ints = [1, 3])
    fun `rebuild the tallies of a repository whose context is built before another one`(
        shards: Int
    ) {
        useDelivery(UniformAcrossAllShards.forNumber(shards))
        val earlier = TallyA1Repository()
        val later = TallyA2Repository()
        contexts.create(CONTEXT, earlier)
        contexts.create(LATER_CONTEXT, later)
        contexts.emit(CONTEXT, ALPHA, BETA)
        contexts.emit(LATER_CONTEXT, ALPHA, BETA)
        waitPastTurbulence()

        TallyWeight.value = CAUGHT_UP_WEIGHT
        earlier.catchUpAll(anHourAgo())
        contexts.post(CONTEXT, ALPHA, live = true)
        contexts.post(LATER_CONTEXT, ALPHA, live = true)

        assertSoftly {
            failures.shouldBeEmpty()
            jobStatuses(earlier.entityStateType()) shouldBe listOf(COMPLETED)
            earlier.shouldBeCaughtUp(CONTEXT)
            later.shouldNotBeCaughtUp(LATER_CONTEXT)
            undelivered().shouldBeEmpty()
        }
    }

    @ParameterizedTest(name = WITH_SHARDS)
    @ValueSource(ints = [1, 3])
    fun `complete if a context without projections is built first`(shards: Int) {
        useDelivery(UniformAcrossAllShards.forNumber(shards))
        contexts.create("WithoutProjections")
        val tallies = TallyA1Repository()
        contexts.create(CONTEXT, tallies)

        catchUpAndCheck(caughtUp = listOf(tallies), notCaughtUp = listOf()) {
            tallies.catchUpAll(anHourAgo())
        }
    }

    @ParameterizedTest(name = WITH_SHARDS)
    @ValueSource(ints = [1, 3])
    fun `complete if a context without projections is built last`(shards: Int) {
        useDelivery(UniformAcrossAllShards.forNumber(shards))
        val tallies = TallyA1Repository()
        contexts.create(CONTEXT, tallies)
        contexts.create("WithoutProjections")

        catchUpAndCheck(caughtUp = listOf(tallies), notCaughtUp = listOf()) {
            tallies.catchUpAll(anHourAgo())
        }
    }

    /**
     * Emits the history to [ALPHA] and [BETA] of [CONTEXT], changes the weight, runs
     * the given catch-up, and posts a live event to [ALPHA].
     *
     * Then checks that the given repositories hold the tallies caught up and not caught up.
     */
    private fun catchUpAndCheck(
        caughtUp: List<TallyRepository<*, *>>,
        notCaughtUp: List<TallyRepository<*, *>>,
        catchUp: () -> Unit
    ) {
        contexts.emit(CONTEXT, ALPHA, BETA)
        waitPastTurbulence()

        TallyWeight.value = CAUGHT_UP_WEIGHT
        catchUp()
        contexts.post(CONTEXT, ALPHA, live = true)

        assertSoftly {
            failures.shouldBeEmpty()
            caughtUp.forEach { repository ->
                jobStatuses(repository.entityStateType()) shouldBe listOf(COMPLETED)
                repository.shouldBeCaughtUp(CONTEXT)
            }
            notCaughtUp.forEach { repository ->
                jobStatuses(repository.entityStateType()).shouldBeEmpty()
                repository.shouldNotBeCaughtUp(CONTEXT)
            }
            undelivered().shouldBeEmpty()
        }
    }

    private fun TallyRepository<*, *>.shouldBeCaughtUp(context: String) {
        listOf(ALPHA, BETA).forEach { id ->
            withClue("The caught-up tally `$id` of `${entityStateType()}`") {
                valuesOf(id) shouldBe
                        contexts.caughtUp(context = context, id = id, weight = CAUGHT_UP_WEIGHT)
            }
        }
    }

    private fun TallyRepository<*, *>.shouldNotBeCaughtUp(context: String) {
        listOf(ALPHA, BETA).forEach { id ->
            withClue("The tally `$id` of `${entityStateType()}`, not caught up") {
                valuesOf(id) shouldBe
                        contexts.notCaughtUp(context = context, id = id, weight = CAUGHT_UP_WEIGHT)
            }
        }
    }

    private companion object {

        const val WITH_SHARDS = "with {0} shard(s)"
        const val CONTEXT = "Tallies"
        const val LATER_CONTEXT = "LaterTallies"
        const val ALPHA = "alpha"
        const val BETA = "beta"
        const val CAUGHT_UP_WEIGHT = 100

        fun anHourAgo(): Timestamp = subtract(currentTime(), Durations.fromHours(1))
    }
}
