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

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.spine.base.Time.currentTime
import io.spine.server.delivery.CatchUpStatus.COMPLETED
import io.spine.server.delivery.CatchUpStatus.IN_PROGRESS
import io.spine.server.delivery.CatchUpStatus.STARTED
import io.spine.server.delivery.given.CounterView
import io.spine.server.delivery.given.TestCatchUpJobs.catchUpJob
import io.spine.server.delivery.given.TestInboxMessages.catchingUp
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`CatchUpBaseline` should")
internal class CatchUpBaselineSpec {

    private val type = CounterView.projectionType()
    private val started = catchUpJob(type, STARTED, currentTime(), listOf(FIRST))
    private val completed = catchUpJob(type, COMPLETED, currentTime(), null)
    private val baseline = CatchUpBaseline()

    @Test
    fun `index the jobs it is given first`() {
        val jobs = baseline.compare(iteratorOf(started, completed))

        jobs.all() shouldContainExactly listOf(started, completed)
        jobs.jobFor(catchingUp(FIRST, type)) shouldBe started
        baseline.changed() shouldBe false
    }

    @Test
    fun `return the baseline for the same jobs in the same statuses`() {
        val first = baseline.compare(iteratorOf(started, completed))
        val read = started.copy { whenLastRead = currentTime() }

        baseline.compare(iteratorOf(completed, read)) shouldBeSameInstanceAs first
        baseline.changed() shouldBe false
    }

    @Test
    fun `note a job in another status`() {
        baseline.compare(iteratorOf(started, completed))

        baseline.compare(iteratorOf(started.copy { status = IN_PROGRESS }, completed))

        baseline.changed() shouldBe true
    }

    @Test
    fun `note a new job`() {
        baseline.compare(iteratorOf(started, completed))
        val added = catchUpJob(type, STARTED, currentTime(), null)

        baseline.compare(iteratorOf(started, completed, added))

        baseline.changed() shouldBe true
    }

    @Test
    fun `note a missing job`() {
        baseline.compare(iteratorOf(started, completed))

        baseline.compare(iteratorOf(started))

        baseline.changed() shouldBe true
    }

    @Test
    fun `keep noting a change once noted`() {
        baseline.compare(iteratorOf(started, completed))
        baseline.compare(iteratorOf(started.copy { status = IN_PROGRESS }, completed))

        baseline.compare(iteratorOf(started, completed))

        baseline.changed() shouldBe true
    }

    private fun iteratorOf(vararg jobs: CatchUp): Iterator<CatchUp> = jobs.iterator()

    private companion object {
        const val FIRST = "first"
    }
}
