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

import com.google.protobuf.Duration
import com.google.protobuf.Timestamp
import com.google.protobuf.util.Durations
import com.google.protobuf.util.Timestamps.subtract
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.spine.base.EventMessage
import io.spine.base.Identifier
import io.spine.base.Time.currentTime
import io.spine.core.TenantId
import io.spine.environment.Tests
import io.spine.server.delivery.CatchUpStatus.COMPLETED
import io.spine.server.delivery.CatchUpStatus.FINALIZING
import io.spine.server.delivery.CatchUpStatus.IN_PROGRESS
import io.spine.server.delivery.InboxMessageStatus.DELIVERED
import io.spine.server.delivery.InboxMessageStatus.TO_CATCH_UP
import io.spine.server.delivery.InboxMessageStatus.TO_DELIVER
import io.spine.server.delivery.event.CatchUpCompleted
import io.spine.server.delivery.event.CatchUpStarted
import io.spine.server.delivery.event.HistoryFullyRecalled
import io.spine.server.delivery.event.LiveEventsPickedUp
import io.spine.server.delivery.given.ConsecutiveProjection
import io.spine.server.delivery.given.CounterView
import io.spine.server.storage.memory.InMemoryStorageFactory
import io.spine.server.tenant.TenantAwareRunner
import io.spine.server.under
import io.spine.test.delivery.ConsecutiveNumberView
import io.spine.test.delivery.NumberAdded
import io.spine.test.delivery.numberAdded
import io.spine.test.delivery.positiveNumberEmitted
import io.spine.testing.SlowTest
import io.spine.testing.server.blackbox.BlackBox
import io.spine.type.TypeUrl
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.jvm.optionals.getOrNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.Timeout.ThreadMode.SEPARATE_THREAD

/**
 * Tests that a delivery run delivers each page of a shard according to the catch-up jobs
 * read after the page, and starts over once the statuses of the jobs change.
 *
 * Reading the jobs once per run, or only after a page that is not full, would leave
 * the full pages that follow to an outdated status of a job: a catch-up would never
 * complete, or a live event would be applied twice or lost.
 *
 * Each case uses a single shard. Hooks stand for concurrent writers: the shard observer
 * posts live events as certain messages are written, and the storage of the jobs changes
 * a job right after it is read. The changes happen while a delivery run is in progress.
 *
 * The cases run in a separate thread, so that a timeout fails a case, which a livelocked
 * run would not notice. The timed-out thread is left running, though, so the watchdog of
 * this class is the main guard against a livelock.
 */
@SlowTest
@Timeout(value = 60, unit = SECONDS, threadMode = SEPARATE_THREAD)
@DisplayName("`Delivery`, reading the catch-up jobs for each page, should")
internal class CatchUpJobsPerPageIgTest : AbstractDeliveryTest() {

    private val repository = CounterView.Repository()
    private val failures = CopyOnWriteArrayList<String>()
    private val watchdog = Watchdog()
    private val observer = HookedObserver()
    private lateinit var delivery: Delivery

    @AfterEach
    fun resetProjections() {
        CounterView.changeWeightTo(1)
        ConsecutiveProjection.usePositives()
    }

    @Test
    fun `complete a catch-up, if each page holds a single message`() {
        useDelivery(pageSize = 1)
        BlackBox.singleTenantWith(repository).use { box ->
            val since = emitHistory(box, FIRST)
            CounterView.changeWeightTo(10)

            repository.catchUp(since, setOf(FIRST))

            assertSoftly {
                checkRunsWentOn()
                jobStatuses() shouldBe listOf(COMPLETED)
                totals(FIRST) shouldBe mapOf(FIRST to EVENTS_PER_TARGET * 10)
                undelivered().shouldBeEmpty()
            }
        }
    }

    @Test
    fun `apply a live event once, if it arrives as a repeated catch-up starts`() {
        useDelivery(pageSize = PAGE_SIZE, window = KEEP_DELIVERED)
        BlackBox.singleTenantWith(repository).use { box ->
            val since = emitHistory(box, FIRST, SECOND)
            CounterView.changeWeightTo(10)
            val firstJob = repository.catchUpAll(since)
            withClue("The first catch-up") {
                jobStatuses() shouldBe listOf(COMPLETED)
            }
            fillPageOfNextRequest(box)
            CounterView.changeWeightTo(100)
            observer.onceWritten({ it.isEvent<CatchUpStarted> { e -> e.id != firstJob } }) {
                box.receivesEvent(numberAdded { calculatorId = FIRST; value = 0 })
            }

            repository.catchUp(since, setOf(FIRST))

            assertSoftly {
                checkRunsWentOn()
                jobStatuses() shouldBe listOf(COMPLETED, COMPLETED)
                // The history and the live event, each applied once and weighing 100.
                totals(FIRST) shouldBe mapOf(FIRST to (EVENTS_PER_TARGET + 1) * 100)
                undelivered().shouldBeEmpty()
            }
        }
    }

    @Test
    fun `deliver a live event that arrives after the history is read to the end`() {
        useDelivery(pageSize = PAGE_SIZE, window = KEEP_DELIVERED)
        BlackBox.singleTenantWith(repository).use { box ->
            val since = emitHistory(box, FIRST, SECOND)
            CounterView.changeWeightTo(10)
            // Live events to another instance fill the page of `HistoryFullyRecalled`.
            observer.onceWritten({ it.isEvent<HistoryFullyRecalled>() }) {
                repeat(PAGE_SIZE) {
                    box.receivesEvent(numberAdded { calculatorId = THIRD; value = 0 })
                }
            }
            // `LiveEventsPickedUp` follows the last read of the history.
            observer.onceWritten({ it.isEvent<LiveEventsPickedUp>() }) {
                box.receivesEvent(numberAdded { calculatorId = FIRST; value = 0 })
            }

            repository.catchUp(since, setOf(FIRST))

            assertSoftly {
                checkRunsWentOn()
                jobStatuses() shouldBe listOf(COMPLETED)
                // The history and the live event, each applied once and weighing 10.
                totals(FIRST) shouldBe mapOf(FIRST to (EVENTS_PER_TARGET + 1) * 10)
                undelivered().shouldBeEmpty()
            }
        }
    }

    @Test
    fun `deliver the events held for a catch-up before the live events after its completion`() {
        useDelivery(pageSize = PAGE_SIZE, window = KEEP_DELIVERED)
        // The mode is static, and `CatchUpTest` leaves it at negatives.
        ConsecutiveProjection.usePositives()
        val consecutive = ConsecutiveProjection.Repo()
        BlackBox.singleTenantWith(consecutive).use { box ->
            val since = subtract(currentTime(), Durations.fromMinutes(1))
            (1..5).forEach { box.receivesEvent(numberFor(FIRST, it)) }
            Thread.sleep(PAST_TURBULENCE_MILLIS)
            // Arriving before the last read of the history, 6 is replayed and held until
            // the catch-up completes.
            observer.onceWritten({ it.isEvent<HistoryFullyRecalled>() }) {
                box.receivesEvent(numberFor(FIRST, 6))
            }
            observer.onceWritten({ it.isEvent<CatchUpCompleted>() }) {
                box.receivesEvent(numberFor(FIRST, 7))
            }

            consecutive.catchUp(since, setOf(FIRST))

            assertSoftly {
                checkRunsWentOn()
                jobStatuses(TypeUrl.of(ConsecutiveNumberView::class.java)) shouldBe
                        listOf(COMPLETED)
                // The projection ignores a value that does not follow the last one.
                consecutive.find(FIRST).getOrNull()?.state()?.lastValue shouldBe 7
            }
        }
    }

    @Test
    fun `deliver each page according to the jobs read after the page`() {
        val storage = HookedCatchUpStorage()
        useDelivery(pageSize = PAGE_SIZE, catchUpStorage = storage)
        BlackBox.singleTenantWith(repository).use { box ->
            val job = inProgressJobFor(FIRST)
            storage.write(job)
            // The job moves on and a live event arrives right after the jobs are read.
            storage.afterNextRead {
                storage.write(job.copy { status = FINALIZING })
                box.receivesEvent(numberAdded { calculatorId = FIRST; value = MARK })
            }

            box.receivesEvent(numberAdded { calculatorId = THIRD; value = 0 })

            withClue("The live event, held while the job is finalizing") {
                inboxContents().filter { it.carries(MARK) }.map { it.status } shouldBe
                        listOf(TO_CATCH_UP)
            }
            checkRunsWentOn()
        }
    }

    @Test
    fun `read no jobs for a page of the delivered messages kept for deduplication`() {
        val storage = HookedCatchUpStorage()
        useDelivery(pageSize = PAGE_SIZE, window = KEEP_DELIVERED, catchUpStorage = storage)
        BlackBox.singleTenantWith(repository).use { box ->
            box.receivesEvent(numberAdded { calculatorId = FIRST; value = 0 })
            withClue("The live event, delivered and kept") {
                inboxContents().map { it.status } shouldBe listOf(DELIVERED)
            }
            val readsBefore = storage.reads.get()

            TenantAwareRunner.with(TenantId.getDefaultInstance()).run {
                delivery.deliverMessagesFrom(SHARD)
            }

            withClue("The reads of the jobs by a run over the delivered messages") {
                storage.reads.get() - readsBefore shouldBe 0
            }
            checkRunsWentOn()
        }
    }

    /**
     * Sets up a single-shard `Delivery` with the [watchdog] and the [observer], and uses it
     * in the `Tests` environment.
     *
     * Call it before creating a `BlackBox`: a projection repository binds its catch-up to
     * the `Delivery` in use when it is registered.
     */
    private fun useDelivery(
        pageSize: Int,
        window: Duration = Durations.ZERO,
        catchUpStorage: CatchUpStorage? = null
    ) {
        val builder = Delivery.newBuilder()
            .setStrategy(UniformAcrossAllShards.singleShard())
            .setPageSize(pageSize)
            .setDeduplicationWindow(window)
            .setMonitor(watchdog)
        catchUpStorage?.let { builder.setCatchUpStorage(it) }
        delivery = builder.build()
        delivery.subscribe(observer)
        under<Tests> { use(delivery) }
    }

    /**
     * Checks that the watchdog stopped no run, and that no run, reception or hook failed.
     */
    private fun checkRunsWentOn() {
        withClue("A run stopped by the watchdog") {
            watchdog.stopped.get() shouldBe false
        }
        withClue("The failures of the runs, receptions and hooks") {
            failures.shouldBeEmpty()
        }
    }

    /**
     * Emits [EVENTS_PER_TARGET] events, each weighing 1, to each of the given targets.
     *
     * Each event is posted live, so it gets a timestamp of its own. Then the function waits
     * until the events are older than the turbulence period of a catch-up.
     *
     * @return The time to start a catch-up from, which precedes the events.
     */
    private fun emitHistory(box: BlackBox, vararg targets: String): Timestamp {
        CounterView.changeWeightTo(1)
        val since = subtract(currentTime(), Durations.fromMinutes(1))
        repeat(EVENTS_PER_TARGET) {
            targets.forEach { target ->
                box.receivesEvent(numberAdded { calculatorId = target; value = 0 })
            }
        }
        Thread.sleep(PAST_TURBULENCE_MILLIS)
        return since
    }

    /**
     * Emits live events to [THIRD] until the next message makes a full page of the shard.
     *
     * A run that delivers the next message reads the shard from its start, and that message
     * is the last one in the shard. Once the shard holds a whole number of pages with it,
     * every page of that run is full.
     */
    private fun fillPageOfNextRequest(box: BlackBox) {
        repeat(PAGE_SIZE) {
            if ((inboxContents().size + 1) % PAGE_SIZE == 0) {
                return
            }
            box.receivesEvent(numberAdded { calculatorId = THIRD; value = 0 })
        }
        error("No live event made the next message fill a page of $PAGE_SIZE messages.")
    }

    private fun jobStatuses(
        stateType: TypeUrl = CounterView.projectionType()
    ): List<CatchUpStatus> =
        delivery.catchUpStorage()
            .readByType(stateType)
            .asSequence()
            .map { it.status }
            .toList()

    /**
     * Returns the totals of the projections with the given IDs,
     * or `null` for those that do not exist.
     */
    private fun totals(vararg ids: String): Map<String, Int?> =
        ids.associateWith { id -> repository.find(id).getOrNull()?.state()?.total }

    /**
     * Returns the messages that are still to be delivered, either live or for a catch-up.
     */
    private fun undelivered(): List<InboxMessage> =
        inboxContents().filter { it.status == TO_DELIVER || it.status == TO_CATCH_UP }

    private fun inboxContents(): List<InboxMessage> = InboxContents.get().values.flatten()

    /**
     * Stops a delivery that goes on for too many pages, taking it for a livelock.
     *
     * From the first stop on, every run is stopped and the [observer] dispatches nothing,
     * so that a case ends quickly instead of running until it times out.
     *
     * Also records the failed receptions of messages. By default, `DeliveryMonitor` marks
     * such a message delivered, which a shard observer cannot see.
     */
    private inner class Watchdog : DeliveryMonitor() {

        val stopped = AtomicBoolean()
        private val pages = AtomicInteger()

        override fun onDeliveryStarted(index: ShardIndex) {
            pages.set(0)
        }

        override fun shouldContinueAfter(stage: DeliveryStage): Boolean {
            if (!stopped.get() && pages.incrementAndGet() > MAX_PAGES_PER_DELIVERY) {
                stopped.set(true)
            }
            return !stopped.get()
        }

        override fun onReceptionFailure(reception: FailedReception): FailedReception.Action {
            val error = reception.error()
            failures.add("Reception: ${error.type}: ${error.message}")
            return super.onReceptionFailure(reception)
        }
    }

    /**
     * Delivers synchronously through `LocalDispatchingObserver`, unless the [watchdog]
     * stopped a run, and records what a delivery or a hook throws.
     *
     * Runs a one-off hook once a matching message is written, before delivering it.
     */
    private inner class HookedObserver : ShardObserver {

        private val dispatching = LocalDispatchingObserver()
        private val hooks = CopyOnWriteArrayList<Hook>()

        fun onceWritten(matches: (InboxMessage) -> Boolean, action: () -> Unit) {
            hooks.add(Hook(matches, action))
        }

        override fun onMessage(message: InboxMessage) {
            try {
                val hook = hooks.firstOrNull { it.matches(message) }
                if (hook != null && hooks.remove(hook)) {
                    hook.action()
                }
                if (!watchdog.stopped.get()) {
                    dispatching.onMessage(message)
                }
            } catch (e: Exception) {
                failures.add("Run or hook: $e")
                throw e
            }
        }
    }

    /**
     * An action to run once a message matching the condition is written.
     */
    private class Hook(
        val matches: (InboxMessage) -> Boolean,
        val action: () -> Unit
    )

    /**
     * Counts the reads of all the jobs, and runs a one-off action right after the next one.
     */
    private class HookedCatchUpStorage :
        CatchUpStorage(InMemoryStorageFactory.newInstance(), /* multitenant = */ false) {

        val reads = AtomicInteger()
        private val nextReadAction = AtomicReference<(() -> Unit)?>()

        fun afterNextRead(action: () -> Unit) {
            nextReadAction.set(action)
        }

        override fun readAll(): Iterator<CatchUp> {
            val jobs = super.readAll().asSequence().toList()
            reads.incrementAndGet()
            nextReadAction.getAndSet(null)?.invoke()
            return jobs.iterator()
        }
    }

    private companion object {
        const val FIRST = "first"
        const val SECOND = "second"
        const val THIRD = "third"
        const val EVENTS_PER_TARGET = 10
        const val PAGE_SIZE = 3

        /**
         * Marks the live event a case looks for.
         */
        const val MARK = 777

        /**
         * Keeps the delivered messages, so that pages fill up.
         */
        val KEEP_DELIVERED: Duration = Durations.fromMinutes(1)

        /**
         * Exceeds the 500 ms turbulence period of a catch-up.
         */
        const val PAST_TURBULENCE_MILLIS = 1_000L

        /**
         * Far more pages than any case delivers without a livelock.
         */
        const val MAX_PAGES_PER_DELIVERY = 1_000

        val SHARD: ShardIndex = DeliveryStrategy.newIndex(0, 1)
    }
}

/**
 * Tells whether this message carries an event of type [T] matching the given condition.
 */
private inline fun <reified T : EventMessage> InboxMessage.isEvent(
    matches: (T) -> Boolean = { true }
): Boolean {
    if (!hasEvent()) {
        return false
    }
    val message = event.enclosedMessage()
    return message is T && matches(message)
}

/**
 * Tells whether this message carries a `NumberAdded` event with the given value.
 */
private fun InboxMessage.carries(value: Int): Boolean =
    isEvent<NumberAdded> { it.value == value }

private fun numberFor(instance: String, number: Int) = positiveNumberEmitted {
    id = instance
    value = number
}

/**
 * Creates a catch-up job of `CounterView` targeting the given instance,
 * in the `IN_PROGRESS` status, with no catch-up process behind it.
 */
private fun inProgressJobFor(instance: String): CatchUp = catchUp {
    id = catchUpId {
        uuid = Identifier.newUuid()
        projectionType = CounterView.projectionType().value()
    }
    request = CatchUpKt.request {
        target += Identifier.pack(instance)
        sinceWhen = subtract(currentTime(), Durations.fromMinutes(1))
    }
    status = IN_PROGRESS
}
