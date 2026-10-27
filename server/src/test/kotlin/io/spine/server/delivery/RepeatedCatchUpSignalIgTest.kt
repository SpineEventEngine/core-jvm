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
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.spine.core.EventId
import io.spine.protobuf.AnyPacker
import io.spine.server.BoundedContext
import io.spine.server.delivery.CatchUpMessages.fullyRecalled
import io.spine.server.delivery.CatchUpMessages.recalled
import io.spine.server.delivery.CatchUpStatus.COMPLETED
import io.spine.server.delivery.CatchUpStatus.FINALIZING
import io.spine.server.delivery.CatchUpStatus.IN_PROGRESS
import io.spine.server.delivery.CatchUpStatus.STARTED
import io.spine.server.delivery.event.CatchUpRequested
import io.spine.server.delivery.event.HistoryEventsRecalled
import io.spine.server.delivery.event.LiveEventsPickedUp
import io.spine.server.delivery.event.ShardProcessingRequested
import io.spine.server.delivery.event.catchUpRequested
import io.spine.server.delivery.given.CounterView
import io.spine.server.delivery.given.HoldingExecutor
import io.spine.server.delivery.given.RecordingInboxStorage
import io.spine.server.entity.EntityRecord
import io.spine.server.event.AbstractEventReactor
import io.spine.server.event.EventDispatcher
import io.spine.server.event.NoReaction
import io.spine.server.event.React
import io.spine.server.storage.RecordWithColumns
import io.spine.server.storage.memory.InMemoryStorageFactory
import io.spine.system.server.SystemBoundedContexts.systemOf
import io.spine.test.delivery.numberAdded
import io.spine.testing.SlowTest
import io.spine.testing.server.TestEventFactory
import io.spine.testing.server.blackbox.BlackBox
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit.SECONDS
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.Timeout.ThreadMode.SEPARATE_THREAD

/**
 * Tests a catch-up that receives one of its signals once more.
 *
 * A signal can come again after a crash: the delivery is at-least-once, and the in-memory
 * cache of the delivered messages does not survive the crash.
 *
 * Each repeat is a new event with the message of the original, so the deduplication of
 * `Delivery` does not drop it. A report of an instance is posted the way `CatchUpEndpoint`
 * posts the first one: as a system event of the target projection. Any other signal is
 * posted to the System context, where `CatchUpStarter` posts `CatchUpRequested`.
 *
 * Parallel posting of system events is enabled, as in production, but its executor runs
 * each task in the calling thread or holds the task until it is released.
 */
@SlowTest
@Timeout(value = 60, unit = SECONDS, threadMode = SEPARATE_THREAD)
@DisplayName("Catch-up, given a repeated signal, should")
internal class RepeatedCatchUpSignalIgTest : AbstractCatchUpIgTest() {

    private val inboxStorage = RecordingInboxStorage()
    private val catchUpStorage = RecordingCatchUpStorage()
    private val systemEvents = HoldingExecutor()

    /**
     * The instances that [reportAgain] reported once more.
     */
    private val repeatedReports = mutableListOf<String>()

    /**
     * The IDs of the events that [postAgain] posted to repeat the other signals.
     */
    private val repeatedSignals = mutableListOf<EventId>()

    private lateinit var context: BoundedContext

    /**
     * Uses a single-shard `Delivery` that records each message sent to an inbox and each
     * state of a catch-up job.
     */
    @BeforeEach
    fun useRecordingDelivery() {
        useDelivery(UniformAcrossAllShards.singleShard()) { builder ->
            builder.setInboxStorage(inboxStorage)
                .setCatchUpStorage(catchUpStorage)
        }
    }

    @Nested inner class
    `'EntityPreparedForCatchUp'` {

        @Test
        fun `complete once if an instance reports twice before another one reports`() {
            blackBox().use { box ->
                val since = emitHistory(box, EVENTS_PER_TARGET, FIRST, SECOND)
                systemEvents.hold()
                val job = catchUpTwoTargets(since)
                withClue("The reports held: one per target") {
                    systemEvents.heldCount shouldBe 2
                }

                // One instance reports twice before the report of the other one is released.
                // The repeat is counted, so the catch-up goes on without the other report,
                // which then comes too late and must be ignored.
                systemEvents.releaseFirst()
                val reported = inboxStorage.reportsOf(job).single()
                systemEvents.withoutHolding {
                    reportAgain(reported, job)
                }
                systemEvents.release()

                assertCompletedOnce(job, mapOf(FIRST to 100, SECOND to 100))
            }
        }

        @Test
        fun `ignore it while the catch-up is in progress`() {
            assertCompletedOnceIfRepeatedOn(HistoryEventsRecalled::class.java) { job ->
                reportAgain(FIRST, job)
            }
        }

        @Test
        fun `ignore it while the catch-up is finalizing`() {
            assertCompletedOnceIfRepeatedOn(LiveEventsPickedUp::class.java) { job ->
                reportAgain(FIRST, job)
            }
        }

        @Test
        fun `ignore it once the catch-up is completed`() {
            assertCompletedOnceIfRepeatedAfterwards { job -> reportAgain(FIRST, job) }
        }
    }

    @Nested inner class
    `'HistoryEventsRecalled'` {

        /**
         * The repeat starts a second chain of history reads, which runs alongside the first
         * one while the catch-up is in progress. The chains take turns because the handlers
         * of a job run one at a time, and each read starts after the last event read, so
         * no event is read twice.
         *
         * Each chain ends with a `HistoryFullyRecalled` emitted while the catch-up is in
         * progress. The second one finds it finalizing and runs the finalization again,
         * which is harmless.
         */
        @Test
        fun `complete once if it comes while the catch-up is in progress`() {
            assertCompletedOnceIfRepeatedOn(
                HistoryEventsRecalled::class.java,
                finalizations = 2
            ) { job ->
                postAgain(recalled(job))
            }
        }

        @Test
        fun `ignore it while the catch-up is finalizing`() {
            assertCompletedOnceIfRepeatedOn(LiveEventsPickedUp::class.java) { job ->
                postAgain(recalled(job))
            }
        }

        @Test
        fun `ignore it once the catch-up is completed`() {
            assertCompletedOnceIfRepeatedAfterwards { job -> postAgain(recalled(job)) }
        }
    }

    @Nested inner class
    `'HistoryFullyRecalled'` {

        /**
         * The catch-up stores its `FINALIZING` status before it runs the finalization.
         * So the signal delivered again after a failure in between must run it again, and
         * running it once more is harmless.
         */
        @Test
        fun `run the finalization again while the catch-up is finalizing`() {
            assertCompletedOnceIfRepeatedOn(
                LiveEventsPickedUp::class.java,
                finalizations = 2
            ) { job ->
                postAgain(fullyRecalled(job))
            }
        }

        @Test
        fun `ignore it once the catch-up is completed`() {
            assertCompletedOnceIfRepeatedAfterwards { job -> postAgain(fullyRecalled(job)) }
        }
    }

    @Nested inner class
    `'CatchUpRequested'` {

        /**
         * The catch-up stores its `STARTED` status before it sends the start signals.
         * So the request delivered again after a failure in between must send them again.
         * Each target is then reset and reports once more.
         */
        @Test
        fun `send the start signals again while the catch-up is started`() {
            blackBox().use { box ->
                val since = emitHistory(box, EVENTS_PER_TARGET, FIRST, SECOND)
                systemEvents.hold()
                val job = catchUpTwoTargets(since)

                postAgain(requestOf(job))
                withClue("The reports held: two per target") {
                    systemEvents.heldCount shouldBe 4
                }
                systemEvents.release()

                val expectedTotals = mapOf(FIRST to 100, SECOND to 100)
                assertCompletedOnce(job, expectedTotals, reportsPerTarget = 2)
            }
        }

        @Test
        fun `ignore it while the catch-up is in progress`() {
            assertCompletedOnceIfRepeatedOn(HistoryEventsRecalled::class.java) { job ->
                postAgain(requestOf(job))
            }
        }

        @Test
        fun `ignore it while the catch-up is finalizing`() {
            assertCompletedOnceIfRepeatedOn(LiveEventsPickedUp::class.java) { job ->
                postAgain(requestOf(job))
            }
        }

        @Test
        fun `ignore it once the catch-up is completed`() {
            assertCompletedOnceIfRepeatedAfterwards { job -> postAgain(requestOf(job)) }
        }
    }

    /**
     * Catches up [FIRST] and [SECOND], calling the given action on the first catch-up signal
     * of the given type, and asserts that the catch-up completed once, running
     * the finalization the given number of times.
     */
    private fun assertCompletedOnceIfRepeatedOn(
        signal: Class<out CatchUpSignal>,
        finalizations: Int = 1,
        action: (CatchUpId) -> Unit
    ) {
        blackBox(OnFirstSignal(signal, action)).use { box ->
            val job = catchUpTwoTargets(emitHistory(box, EVENTS_PER_TARGET, FIRST, SECOND))
            val expectedTotals = mapOf(FIRST to 100, SECOND to 100)
            assertCompletedOnce(job, expectedTotals, finalizations = finalizations)
        }
    }

    /**
     * Catches up [FIRST] and [SECOND], posts a live event to [FIRST], and calls the given
     * action once the event is older than the turbulence period.
     *
     * Asserts that the catch-up completed once and the live event was applied once.
     */
    private fun assertCompletedOnceIfRepeatedAfterwards(action: (CatchUpId) -> Unit) {
        blackBox().use { box ->
            val job = catchUpTwoTargets(emitHistory(box, EVENTS_PER_TARGET, FIRST, SECOND))

            // A live event after the catch-up, older than the turbulence period by the time
            // the signal comes again. Another round started by the repeat would apply it
            // twice.
            box.receivesEvent(numberAdded { calculatorId = FIRST })
            waitPastTurbulence()

            action(job)

            assertCompletedOnce(job, mapOf(FIRST to 110, SECOND to 100))
        }
    }

    /**
     * Asserts that the test repeated exactly one signal and that the signal reached an inbox;
     * that the reports are the given number per target, plus the repeated ones; that
     * the given job went through each status once and ran the finalization the given number
     * of times; that the targets ended with the given totals; and that nothing is left
     * to deliver.
     */
    private fun assertCompletedOnce(
        job: CatchUpId,
        expectedTotals: Map<String, Int>,
        finalizations: Int = 1,
        reportsPerTarget: Int = 1
    ) {
        val expectedReports =
            List(reportsPerTarget) { listOf(FIRST, SECOND) }.flatten() + repeatedReports
        assertSoftly {
            failures.shouldBeEmpty()
            withClue("The signals repeated by the test") {
                repeatedReports.size + repeatedSignals.size shouldBe 1
            }
            withClue("The repeated signals sent to an inbox") {
                inboxStorage.eventIds() shouldContainAll repeatedSignals
            }
            withClue("The reports: $reportsPerTarget per target and the repeated ones") {
                inboxStorage.reportsOf(job) shouldContainExactlyInAnyOrder expectedReports
            }
            statusTrail(job) shouldBe listOf(STARTED, IN_PROGRESS, FINALIZING, COMPLETED)
            lastState(job).instancesToClear shouldBe 0
            withClue("The finalizations, with ${shardRequests(job)} shard requests") {
                inboxStorage.signalsOf(job)
                    .filterIsInstance<LiveEventsPickedUp>() shouldHaveSize finalizations
            }
            totals(FIRST, SECOND) shouldBe expectedTotals
            undelivered().shouldBeEmpty()
        }
    }

    /**
     * Creates a `BlackBox` with the [repository] and the given dispatchers, which posts
     * the system events through [systemEvents].
     *
     * Also sets the [context], to which [postAgain] posts.
     */
    private fun blackBox(vararg dispatchers: EventDispatcher): BlackBox {
        val builder = BoundedContext.singleTenant("RepeatedCatchUpSignal")
            .add(repository)
        dispatchers.forEach { builder.addEventDispatcher(it) }
        builder.systemSettings()
            .enableParallelPosting()
            .useCustomPostingExecutor(systemEvents)
        context = builder.build()
        return BlackBox.from(context)
    }

    /**
     * Catches up [FIRST] and [SECOND], weighing each event 10.
     *
     * @return The ID of the catch-up job.
     */
    private fun catchUpTwoTargets(since: Timestamp): CatchUpId {
        CounterView.changeWeightTo(10)
        return repository.catchUp(since, setOf(FIRST, SECOND))
    }

    /**
     * Reports the given instance prepared for the given job once more, the way
     * `CatchUpEndpoint` reports it, and records the instance in [repeatedReports].
     */
    private fun reportAgain(instance: String, job: CatchUpId) {
        repeatedReports.add(instance)
        repository.lifecycleOf(instance).onEntityPreparedForCatchUp(job)
    }

    /**
     * Posts the given signal once more as a new event to the System context, where
     * `CatchUpStarter` posts `CatchUpRequested`, and records the event ID in [repeatedSignals].
     *
     * The signal is posted at once, even while the system events are held. The system
     * events that follow it are held as usual.
     */
    private fun postAgain(signal: CatchUpSignal) {
        val event = TestEventFactory.newInstance(javaClass).createEvent(signal)
        repeatedSignals.add(event.id)
        systemOf(context).eventBus().post(event)
    }

    /**
     * Returns the `CatchUpRequested` that started the given job.
     */
    private fun requestOf(job: CatchUpId): CatchUpRequested = catchUpRequested {
        id = job
        request = lastState(job).request
    }

    /**
     * Returns the statuses the given job was stored with, in order, with each run of the same
     * status collapsed into one.
     */
    private fun statusTrail(job: CatchUpId): List<CatchUpStatus> =
        catchUpStorage.writesOf(job)
            .map { it.status }
            .fold(listOf()) { trail, status ->
                if (trail.lastOrNull() == status) trail else trail + status
            }

    private fun lastState(job: CatchUpId): CatchUp = catchUpStorage.writesOf(job).last()

    private fun shardRequests(job: CatchUpId): Int =
        inboxStorage.events()
            .filterIsInstance<ShardProcessingRequested>()
            .count { it.process == job }

    /**
     * Records each state of a catch-up job written to the storage.
     */
    private class RecordingCatchUpStorage :
        CatchUpStorage(InMemoryStorageFactory.newInstance(), /* multitenant = */ false) {

        private val writes = CopyOnWriteArrayList<CatchUp>()

        override fun write(record: RecordWithColumns<CatchUpId, EntityRecord>) {
            writes.add(AnyPacker.unpack(record.record().state, CatchUp::class.java))
            super.write(record)
        }

        fun writesOf(job: CatchUpId): List<CatchUp> = writes.filter { it.id == job }
    }

    /**
     * Calls the given action once, on the first catch-up signal of the given type.
     *
     * Reacts to `HistoryEventsRecalled`, which the catch-up emits while `IN_PROGRESS`, and
     * to `LiveEventsPickedUp`, which it emits after it moves to `FINALIZING`. A signal of
     * any other type never calls the action.
     */
    private class OnFirstSignal(
        private val type: Class<out CatchUpSignal>,
        private val action: (CatchUpId) -> Unit
    ) : AbstractEventReactor() {

        /**
         * Set before the action is called, so that the action cannot call itself again.
         */
        private var fired = false

        @React
        fun on(event: HistoryEventsRecalled): NoReaction = fireOn(event)

        @React
        fun on(event: LiveEventsPickedUp): NoReaction = fireOn(event)

        private fun fireOn(signal: CatchUpSignal): NoReaction {
            if (!fired && type.isInstance(signal)) {
                fired = true
                action(signal.id)
            }
            return noReaction()
        }
    }

    private companion object {
        const val FIRST = "first"
        const val SECOND = "second"
        const val EVENTS_PER_TARGET = 10
    }
}
