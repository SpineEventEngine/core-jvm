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

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.spine.server.BoundedContext
import io.spine.server.delivery.CatchUpStatus.COMPLETED
import io.spine.server.delivery.InboxMessageStatus.DELIVERED
import io.spine.server.delivery.event.CatchUpStarted
import io.spine.server.delivery.given.CounterView
import io.spine.server.delivery.given.HoldingExecutor
import io.spine.server.delivery.given.RecordingInboxStorage
import io.spine.server.storage.memory.InMemoryStorageFactory
import io.spine.test.delivery.numberAdded
import io.spine.testing.SlowTest
import io.spine.testing.server.blackbox.BlackBox
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.jvm.optionals.getOrNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.Timeout.ThreadMode.SEPARATE_THREAD

/**
 * Tests a catch-up whose start signals are delivered by a completed catch-up job
 * of the same targets, and kept in the inbox by that job.
 *
 * It happens when a delivery run processes the signals with the jobs that do not include
 * the new one yet. Because each run reads the jobs again for each page, making this happen
 * takes a storage whose queries lag behind its writes, as eventually consistent queries do.
 * The catch-up storage of this test lets the test freeze what `readJobs()` returns, which
 * `Delivery` reads the jobs with, while the other reads and the writes go through.
 *
 * The system events are posted in parallel, as in production, through an executor that runs
 * each task in the calling thread or holds it until released. Holding the reports of the
 * targets keeps the new job `STARTED` until the kept signals meet a run that sees the job.
 */
@SlowTest
@Timeout(value = 60, unit = SECONDS, threadMode = SEPARATE_THREAD)
@DisplayName("Catch-up, given its start signals kept by a completed job, should")
internal class KeptStartSignalIgTest : AbstractCatchUpIgTest() {

    private val inboxStorage = RecordingInboxStorage()
    private val catchUpStorage = LaggingCatchUpStorage()
    private val systemEvents = HoldingExecutor()

    /**
     * Uses a single-shard `Delivery` that records each message sent to an inbox, and whose
     * query of the catch-up jobs the test can freeze.
     */
    @BeforeEach
    fun useRecordingDelivery() {
        useDelivery(UniformAcrossAllShards.singleShard()) { builder ->
            builder.setInboxStorage(inboxStorage)
                .setCatchUpStorage(catchUpStorage)
        }
    }

    @Test
    fun `reset each target once, while the jobs read by a delivery run lag behind the storage`() {
        blackBox().use { box ->
            val since = emitHistory(box, EVENTS_PER_TARGET, *TARGETS.toTypedArray())
            CounterView.changeWeightTo(10)
            repository.catchUp(since, TARGETS)

            // The first job replayed the events at 10 each. Each event weighs 100 now:
            // one reset and replay totals 1000 per target.
            CounterView.changeWeightTo(100)
            catchUpStorage.freezeQueries()
            systemEvents.hold()
            val job = repository.catchUp(since, TARGETS)

            withClue("The start signals, delivered and kept by the completed job") {
                startSignalsOf(job).map { it.status to it.hasKeepUntil() } shouldBe
                        TARGETS.map { DELIVERED to true }
            }
            catchUpStorage.unfreezeQueries()
            systemEvents.release()

            assertSoftly {
                failures.shouldBeEmpty()
                withClue("The reports of the targets") {
                    inboxStorage.reportsOf(job).sorted() shouldBe TARGETS.sorted()
                }
                withClue("The state of the job") {
                    val state = catchUpStorage.readJob(job).getOrNull()
                    state?.status shouldBe COMPLETED
                    state?.instancesToClear shouldBe 0
                }
                totals(*TARGETS.toTypedArray()) shouldBe TARGETS.associateWith { 1000 }
                undelivered().shouldBeEmpty()
            }

            // Once the kept signals expire, a live event starts a run, which removes them.
            Thread.sleep(PAST_KEEPING_MILLIS)
            box.receivesEvent(numberAdded { calculatorId = THIRD })
            withClue("The start signals left after a delivery run") {
                startSignalsOf(job).shouldBeEmpty()
            }
        }
    }

    private fun blackBox(): BlackBox {
        val builder = BoundedContext.singleTenant("KeptStartSignal").add(repository)
        builder.systemSettings()
            .enableParallelPosting()
            .useCustomPostingExecutor(systemEvents)
        return BlackBox.from(builder)
    }

    /**
     * Returns the start signals of the given job in the inbox.
     */
    private fun startSignalsOf(job: CatchUpId): List<InboxMessage> =
        inboxContents().filter {
            it.hasEvent() && (it.event.enclosedMessage() as? CatchUpStarted)?.id == job
        }

    private companion object {

        /**
         * The ID of the first projection to catch up.
         */
        const val FIRST = "first"

        /**
         * The ID of the second projection to catch up.
         */
        const val SECOND = "second"

        /**
         * The ID of a projection that no catch-up of the test touches.
         *
         * See [TARGETS] for its purpose.
         */
        const val THIRD = "third"

        /**
         * The IDs of the projections that each catch-up of the test resets and replays.
         *
         * [THIRD] is not among them, because it serves another purpose. At the end of
         * the test, a live event to [THIRD] starts one more delivery run, whose cleanup
         * removes the expired start signals. No catch-up job matches [THIRD], so its event
         * takes no part in the catch-ups and leaves the totals of the targets as they are.
         */
        val TARGETS = setOf(FIRST, SECOND)

        /**
         * The number of history events each of the [TARGETS] gets before the catch-ups.
         *
         * Each catch-up replays all of them, so a target replayed once at a weight of 100
         * totals 1000.
         */
        const val EVENTS_PER_TARGET = 10

        /**
         * Exceeds the 1 s for which a completed job keeps a message it delivers.
         */
        const val PAST_KEEPING_MILLIS = 1_200L
    }
}

/**
 * A catch-up storage whose `readJobs()` may lag behind its writes.
 *
 * Once frozen, `readJobs()` returns the jobs stored at that moment, until unfrozen.
 * The other reads and the writes go through.
 */
private class LaggingCatchUpStorage :
    CatchUpStorage(InMemoryStorageFactory.newInstance(), false) {

    private var frozen: List<CatchUp>? = null

    fun freezeQueries() {
        frozen = super.readJobs().asSequence().toList()
    }

    fun unfreezeQueries() {
        frozen = null
    }

    override fun readJobs(): Iterator<CatchUp> = frozen?.iterator() ?: super.readJobs()
}
