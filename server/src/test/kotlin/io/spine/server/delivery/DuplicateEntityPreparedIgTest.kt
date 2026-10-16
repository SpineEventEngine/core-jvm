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
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.spine.base.EventMessage
import io.spine.base.Identifier
import io.spine.base.Time.currentTime
import io.spine.core.MessageId
import io.spine.environment.Tests
import io.spine.server.BoundedContext
import io.spine.server.delivery.CatchUpStatus.COMPLETED
import io.spine.server.delivery.CatchUpStatus.FINALIZING
import io.spine.server.delivery.CatchUpStatus.IN_PROGRESS
import io.spine.server.delivery.CatchUpStatus.STARTED
import io.spine.server.delivery.InboxMessageStatus.TO_CATCH_UP
import io.spine.server.delivery.InboxMessageStatus.TO_DELIVER
import io.spine.server.delivery.event.EntityPreparedForCatchUp
import io.spine.server.delivery.event.HistoryEventsRecalled
import io.spine.server.delivery.event.HistoryFullyRecalled
import io.spine.server.delivery.event.LiveEventsPickedUp
import io.spine.server.delivery.event.ShardProcessingRequested
import io.spine.server.delivery.given.CounterView
import io.spine.server.event.AbstractEventReactor
import io.spine.server.event.EventDispatcher
import io.spine.server.event.NoReaction
import io.spine.server.event.React
import io.spine.server.storage.memory.InMemoryStorageFactory
import io.spine.server.under
import io.spine.test.delivery.numberAdded
import io.spine.testing.SlowTest
import io.spine.testing.server.blackbox.BlackBox
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.jvm.optionals.getOrNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.Timeout.ThreadMode.SEPARATE_THREAD

/**
 * Tests a catch-up that receives `EntityPreparedForCatchUp` once more for an instance
 * that has already reported.
 *
 * The repeated report is posted the way `CatchUpEndpoint` posts the first one: as a system
 * event of the target projection. It is a new event with an ID of its own, so the
 * deduplication of `Delivery` does not drop it. Parallel posting of system events is
 * enabled, as in production, but its executor runs each task in the calling thread or holds
 * the task until it is released.
 */
@SlowTest
@Timeout(value = 60, unit = SECONDS, threadMode = SEPARATE_THREAD)
@DisplayName("Catch-up, given a repeated `EntityPreparedForCatchUp`, should")
internal class DuplicateEntityPreparedIgTest : AbstractDeliveryTest() {

    private val repository = CounterView.Repository()
    private val failures = CopyOnWriteArrayList<String>()
    private val inboxStorage = RecordingInboxStorage()
    private val catchUpStorage = RecordingCatchUpStorage()
    private val systemEvents = HoldingExecutor()
    private val watchdog = Watchdog(failures)

    /**
     * Uses a single-shard `Delivery` that delivers synchronously through
     * `LocalDispatchingObserver` until the watchdog stops it.
     *
     * Records each failure. Otherwise, a failure would go unnoticed: `Delivery` only logs
     * what a shard observer throws, and `DeliveryMonitor` marks a message as delivered even
     * if its receptor failed.
     */
    @BeforeEach
    fun useRecordingDelivery() {
        val delivery = Delivery.newBuilder()
            .setStrategy(UniformAcrossAllShards.singleShard())
            .setDeduplicationWindow(Durations.ZERO)
            .setInboxStorage(inboxStorage)
            .setCatchUpStorage(catchUpStorage)
            .setMonitor(watchdog)
            .build()
        val dispatching = LocalDispatchingObserver()
        delivery.subscribe { message ->
            if (watchdog.timeIsUp()) {
                return@subscribe
            }
            try {
                dispatching.onMessage(message)
            } catch (e: Exception) {
                failures.add("Delivery run: $e")
                throw e
            }
        }
        under<Tests> { use(delivery) }
    }

    /**
     * Restores the default weight of an event, which `CounterView` keeps in a static field
     * for the whole JVM.
     */
    @AfterEach
    fun resetWeight() {
        CounterView.changeWeightTo(1)
    }

    @Test
    fun `complete once if an instance reports twice before another one reports`() {
        blackBox().use { box ->
            val since = emitHistory(box)
            systemEvents.hold()
            val job = catchUpTwoTargets(since)
            withClue("The reports held: one per target") {
                systemEvents.heldCount shouldBe 2
            }

            // One instance reports twice before the report of the other one is released.
            // The repeat is counted, so the catch-up goes on without the other report, which
            // then comes too late and must be ignored.
            systemEvents.releaseFirst()
            val reported = inboxStorage.reportsOf(job).single()
            systemEvents.withoutHolding {
                repository.lifecycleOf(reported).onEntityPreparedForCatchUp(job)
            }
            systemEvents.release()

            assertCompletedOnce(job, reported, mapOf(FIRST to 100, SECOND to 100))
        }
    }

    @Test
    fun `ignore it while the catch-up is in progress`() {
        assertIgnoredIfRepeatedOn(HistoryEventsRecalled::class.java)
    }

    @Test
    fun `ignore it while the catch-up is finalizing`() {
        assertIgnoredIfRepeatedOn(LiveEventsPickedUp::class.java)
    }

    @Test
    fun `ignore it once the catch-up is completed`() {
        blackBox().use { box ->
            val since = emitHistory(box)
            val job = catchUpTwoTargets(since)

            // A live event after the catch-up, older than the turbulence period by the time
            // the repeated report comes. Another round started by that report would apply it
            // twice.
            box.receivesEvent(numberAdded { calculatorId = FIRST })
            Thread.sleep(PAST_TURBULENCE_MILLIS)

            repository.lifecycleOf(FIRST).onEntityPreparedForCatchUp(job)

            assertCompletedOnce(job, FIRST, mapOf(FIRST to 110, SECOND to 100))
        }
    }

    /**
     * Catches up [FIRST] and [SECOND], with [FIRST] reporting once more on the first catch-up
     * signal of the given type, and asserts that the repeated report changed nothing.
     */
    private fun assertIgnoredIfRepeatedOn(signal: Class<out CatchUpSignal>) {
        val reportAgain = OnFirstSignal(signal) { job ->
            repository.lifecycleOf(FIRST).onEntityPreparedForCatchUp(job)
        }
        blackBox(reportAgain).use { box ->
            val job = catchUpTwoTargets(emitHistory(box))
            assertCompletedOnce(job, FIRST, mapOf(FIRST to 100, SECOND to 100))
        }
    }

    /**
     * Asserts that each target reported, and the `repeated` one once more; that the given job
     * went through each status once and read the history once; that the targets ended with
     * the given totals; and that nothing is left to deliver.
     */
    private fun assertCompletedOnce(
        job: CatchUpId,
        repeated: String,
        expectedTotals: Map<String, Int>
    ) {
        assertSoftly {
            failures.shouldBeEmpty()
            withClue("The reports: one per target and the repeated one") {
                inboxStorage.reportsOf(job).sorted() shouldBe
                        listOf(FIRST, SECOND, repeated).sorted()
            }
            statusTrail(job) shouldBe listOf(STARTED, IN_PROGRESS, FINALIZING, COMPLETED)
            lastState(job).instancesToClear shouldBe 0
            withClue("`HistoryFullyRecalled` events, with ${shardRequests(job)} shard requests") {
                inboxStorage.signalsOf(job)
                    .filterIsInstance<HistoryFullyRecalled>() shouldHaveSize 1
            }
            totals() shouldBe expectedTotals
            undelivered().shouldBeEmpty()
        }
    }

    private fun blackBox(vararg dispatchers: EventDispatcher): BlackBox {
        val builder = BoundedContext.singleTenant("DuplicateEntityPrepared")
            .add(repository)
        dispatchers.forEach { builder.addEventDispatcher(it) }
        builder.systemSettings()
            .enableParallelPosting()
            .useCustomPostingExecutor(systemEvents)
        return BlackBox.from(builder)
    }

    /**
     * Emits [EVENTS_PER_TARGET] events to each of [FIRST] and [SECOND], weighing 1 each.
     *
     * Each event is posted live, so it gets a timestamp of its own. Then the function waits
     * until the events are older than the turbulence period of a catch-up.
     *
     * @return The time to start the catch-up from, which precedes the events.
     */
    private fun emitHistory(box: BlackBox): Timestamp {
        CounterView.changeWeightTo(1)
        val since = subtract(currentTime(), Durations.fromMinutes(1))
        repeat(EVENTS_PER_TARGET) {
            listOf(FIRST, SECOND).forEach { id ->
                box.receivesEvent(numberAdded { calculatorId = id })
            }
        }
        Thread.sleep(PAST_TURBULENCE_MILLIS)
        return since
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
     * Returns the totals of [FIRST] and [SECOND], or `null` for one that does not exist.
     */
    private fun totals(): Map<String, Int?> =
        listOf(FIRST, SECOND).associateWith { id ->
            repository.find(id).getOrNull()?.state()?.total
        }

    /**
     * Returns the messages that are still to be delivered, either live or for a catch-up.
     */
    private fun undelivered(): List<InboxMessage> =
        InboxContents.get().values.flatten().filter {
            it.status == TO_DELIVER || it.status == TO_CATCH_UP
        }

    /**
     * Records each message sent to an inbox.
     *
     * A message sent is stored on its own. A delivery run stores the messages it has
     * processed in a batch, which is not recorded.
     */
    private class RecordingInboxStorage :
        InboxStorage(InMemoryStorageFactory.newInstance(), false) {

        private val sent = CopyOnWriteArrayList<InboxMessage>()

        override fun write(message: InboxMessage) {
            sent.add(message)
            super.write(message)
        }

        /**
         * Returns the events sent to any inbox.
         */
        fun events(): List<EventMessage> =
            sent.filter { it.hasEvent() }.map { it.event.enclosedMessage() }

        /**
         * Returns the catch-up signals of the given job sent to any inbox.
         */
        fun signalsOf(job: CatchUpId): List<CatchUpSignal> =
            events().filterIsInstance<CatchUpSignal>().filter { it.id == job }

        /**
         * Returns the IDs of the instances that reported being prepared for the given job,
         * in the order of the reports.
         */
        fun reportsOf(job: CatchUpId): List<String> =
            signalsOf(job).filterIsInstance<EntityPreparedForCatchUp>()
                .map { Identifier.unpack(it.instanceId, MessageId::class.java) }
                .map { Identifier.unpack(it.id, String::class.java) }
    }

    /**
     * Records each state of a catch-up job written to the storage.
     */
    private class RecordingCatchUpStorage :
        CatchUpStorage(InMemoryStorageFactory.newInstance(), false) {

        private val writes = CopyOnWriteArrayList<CatchUp>()

        override fun write(message: CatchUp) {
            writes.add(message)
            super.write(message)
        }

        fun writesOf(job: CatchUpId): List<CatchUp> = writes.filter { it.id == job }
    }

    /**
     * Stops the delivery once the time is up, and records the receptor failures, which
     * `Delivery` does not throw.
     */
    private class Watchdog(
        private val failures: CopyOnWriteArrayList<String>
    ) : DeliveryMonitor() {

        private val deadline = TimeSource.Monotonic.markNow() + WATCHDOG_SECONDS.seconds

        /**
         * Tells whether the time is up, recording it as a failure if so.
         */
        fun timeIsUp(): Boolean {
            val up = deadline.hasPassedNow()
            if (up) {
                failures.addIfAbsent("Stopped by the watchdog.")
            }
            return up
        }

        override fun shouldContinueAfter(stage: DeliveryStage): Boolean = !timeIsUp()

        override fun onReceptionFailure(reception: FailedReception): FailedReception.Action {
            failures.add("Receptor: ${reception.error().message}")
            return super.onReceptionFailure(reception)
        }
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

    /**
     * Runs each task in the calling thread or, while holding, keeps it until released.
     *
     * Holding stands for an executor that posts the system events after the delivery run ends.
     *
     * All the calls come from the test thread, so the executor needs no synchronization.
     */
    private class HoldingExecutor : Executor {

        private val held = ArrayDeque<Runnable>()
        private var holding = false

        val heldCount: Int
            get() = held.size

        fun hold() {
            holding = true
        }

        /**
         * Runs the first of the held tasks.
         *
         * The tasks it adds are held too.
         */
        fun releaseFirst() {
            held.removeFirst().run()
        }

        /**
         * Runs the given action with holding switched off, so each task it adds runs
         * in the calling thread at once.
         */
        fun withoutHolding(action: () -> Unit) {
            val wasHolding = holding
            holding = false
            try {
                action()
            } finally {
                holding = wasHolding
            }
        }

        /**
         * Runs the held tasks, and each task to come, in the calling thread.
         */
        fun release() {
            holding = false
            while (held.isNotEmpty()) {
                held.removeFirst().run()
            }
        }

        override fun execute(task: Runnable) {
            if (holding) {
                held.addLast(task)
            } else {
                task.run()
            }
        }
    }

    private companion object {
        const val FIRST = "first"
        const val SECOND = "second"
        const val EVENTS_PER_TARGET = 10

        /**
         * Exceeds the 500 ms turbulence period of a catch-up.
         */
        const val PAST_TURBULENCE_MILLIS = 1_000L

        /**
         * The time after which the watchdog stops a delivery that does not end.
         *
         * It is well within the timeout of a test.
         */
        const val WATCHDOG_SECONDS = 30
    }
}
