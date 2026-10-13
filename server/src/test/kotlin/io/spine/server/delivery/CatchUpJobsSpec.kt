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

import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.spine.base.Time.currentTime
import io.spine.server.delivery.CatchUpStatus.COMPLETED
import io.spine.server.delivery.CatchUpStatus.FINALIZING
import io.spine.server.delivery.CatchUpStatus.IN_PROGRESS
import io.spine.server.delivery.CatchUpStatus.STARTED
import io.spine.server.delivery.given.CounterView
import io.spine.server.delivery.given.TestCatchUpJobs.catchUpJob
import io.spine.server.delivery.given.TestInboxMessages.catchingUp
import io.spine.test.delivery.Calc
import io.spine.test.delivery.DTask
import io.spine.type.TypeUrl
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Checks that [CatchUpJobs] chooses among the jobs that match a message according to
 * [CatchUp.matches], preferring the jobs of ongoing catch-ups.
 */
@DisplayName("`CatchUpJobs` should")
internal class CatchUpJobsSpec {

    private val type = CounterView.projectionType()
    private val anotherType = TypeUrl.of(Calc::class.java)
    private val typeWithoutJobs = TypeUrl.of(DTask::class.java)

    /**
     * Jobs catching up all instances or some of them, of two types, completed and not.
     */
    private val pool = listOf(
        catchUpJob(type, COMPLETED, currentTime(), null),
        catchUpJob(type, IN_PROGRESS, currentTime(), null),
        catchUpJob(type, COMPLETED, currentTime(), listOf(FIRST)),
        catchUpJob(type, FINALIZING, currentTime(), listOf(FIRST)),
        catchUpJob(type, COMPLETED, currentTime(), listOf(SECOND, THIRD)),
        catchUpJob(anotherType, STARTED, currentTime(), null),
        catchUpJob(anotherType, COMPLETED, currentTime(), listOf(FIRST)),
    )

    private val messages = listOf(
        catchingUp(FIRST, type),
        catchingUp(SECOND, type),
        catchingUp(THIRD, type),
        catchingUp(FOURTH, type),
        catchingUp(FIRST, anotherType),
        catchingUp(FIRST, typeWithoutJobs),
    )

    @Test
    fun `return all the jobs in their order`() {
        CatchUpJobs.of(pool).all() shouldContainExactly pool
    }

    @Test
    fun `prefer the job of an ongoing catch-up to a completed one`() {
        val completed = catchUpJob(type, COMPLETED, currentTime(), null)
        val ongoing = catchUpJob(type, IN_PROGRESS, currentTime(), listOf(FIRST))
        val message = catchingUp(FIRST, type)

        CatchUpJobs.of(listOf(completed, ongoing)).jobFor(message) shouldBe ongoing
        CatchUpJobs.of(listOf(ongoing, completed)).jobFor(message) shouldBe ongoing
    }

    @Test
    fun `choose among the matching jobs of any subset in any order, preferring an ongoing one`() {
        val variants = pool.subsets().flatMap { listOf(it, it.reversed()) }
        for (jobs in variants) {
            val catchUpJobs = CatchUpJobs.of(jobs)
            val numbers = jobs.map(pool::indexOf)
            messages.forEachIndexed { number, message ->
                val matching = jobs.filter { it.matches(message) }
                withClue("Jobs $numbers, message $number.") {
                    catchUpJobs.jobFor(message) shouldBeChosenFrom matching
                }
            }
        }
    }

    private companion object {
        const val FIRST = "first"
        const val SECOND = "second"
        const val THIRD = "third"
        const val FOURTH = "fourth"
    }
}

/**
 * Asserts that this job, chosen for a message, is `null` if no job matches the message.
 * Otherwise, it must be one of the matching jobs, and the job of an ongoing catch-up
 * if any of them is.
 */
private infix fun CatchUp?.shouldBeChosenFrom(matching: List<CatchUp>) {
    if (matching.isEmpty()) {
        shouldBeNull()
        return
    }
    val chosen = shouldNotBeNull()
    matching shouldContain chosen
    if (matching.any { it.status != COMPLETED }) {
        chosen.status shouldNotBe COMPLETED
    }
}

/**
 * Returns all the subsets of this list, each keeping the order of the elements.
 */
private fun <T> List<T>.subsets(): List<List<T>> =
    (0 until (1 shl size)).map { mask ->
        filterIndexed { index, _ -> mask and (1 shl index) != 0 }
    }
