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
import io.spine.base.Identifier
import io.spine.base.Time.currentTime
import io.spine.core.MessageId
import io.spine.environment.Tests
import io.spine.server.BoundedContext
import io.spine.server.delivery.CatchUpStatus.COMPLETED
import io.spine.server.delivery.InboxMessageStatus.DELIVERED
import io.spine.server.delivery.InboxMessageStatus.TO_CATCH_UP
import io.spine.server.delivery.InboxMessageStatus.TO_DELIVER
import io.spine.server.delivery.event.CatchUpStarted
import io.spine.server.delivery.event.EntityPreparedForCatchUp
import io.spine.server.delivery.given.CounterView
import io.spine.server.storage.memory.InMemoryStorageFactory
import io.spine.server.under
import io.spine.test.delivery.numberAdded
import io.spine.testing.SlowTest
import io.spine.testing.server.blackbox.BlackBox
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.jvm.optionals.getOrNull
import org.junit.jupiter.api.AfterEach
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
 * The catch-up storage of this test lets the test freeze what `readAll()` returns, which
 * `Delivery` reads the jobs with, while the other reads and the writes go through.
 *
 * The system events are posted in parallel, as in production, through an executor that runs
 * each task in the calling thread or holds it until released. Holding the reports of the
 * targets keeps the new job `STARTED` until the kept signals meet a run that sees the job.
 */
@SlowTest
@Timeout(value = 60, unit = SECONDS, threadMode = SEPARATE_THREAD)
@DisplayName("Catch-up, given its start signals kept by a completed job, should")
internal class KeptStartSignalIgTest : AbstractDeliveryTest() {

    private val repository = CounterView.Repository()
    private val failures = CopyOnWriteArrayList<String>()
    private val inboxStorage = RecordingInboxStorage()
    private val catchUpStorage = LaggingCatchUpStorage()
    private val systemEvents = HoldingExecutor()

    /**
     * Uses a single-shard `Delivery` that delivers synchronously through
     * `LocalDispatchingObserver`, and records each failure.
     *
     * Without the recording, a failure would go unnoticed: `Delivery` only logs what a shard
     * observer throws, and the default `DeliveryMonitor` marks a message as delivered even if
     * its receptor failed.
     */
    @BeforeEach
    fun useRecordingDelivery() {
        val delivery = Delivery.newBuilder()
            .setStrategy(UniformAcrossAllShards.singleShard())
            .setDeduplicationWindow(Durations.ZERO)
            .setInboxStorage(inboxStorage)
            .setCatchUpStorage(catchUpStorage)
            .setMonitor(FailureRecorder(failures))
            .build()
        val dispatching = LocalDispatchingObserver()
        delivery.subscribe { message ->
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
    fun `reset each target once, while the jobs read by a delivery run lag behind the storage`() {
        blackBox().use { box ->
            val since = emitHistory(box)
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
                    val state = catchUpStorage.read(job).getOrNull()
                    state?.status shouldBe COMPLETED
                    state?.instancesToClear shouldBe 0
                }
                totals() shouldBe TARGETS.associateWith { 1000 }
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
     * Returns the totals of the [TARGETS], or `null` for one that does not exist.
     */
    private fun totals(): Map<String, Int?> =
        TARGETS.associateWith { id ->
            repository.find(id).getOrNull()?.state()?.total
        }
}

private const val FIRST = "first"
private const val SECOND = "second"
private const val THIRD = "third"
private val TARGETS = setOf(FIRST, SECOND)
private const val EVENTS_PER_TARGET = 10

/**
 * Exceeds the 500 ms turbulence period of a catch-up.
 */
private const val PAST_TURBULENCE_MILLIS = 1_000L

/**
 * Exceeds the 1 s for which a completed job keeps a message it delivers.
 */
private const val PAST_KEEPING_MILLIS = 1_200L

/**
 * Emits [EVENTS_PER_TARGET] events to each of the [TARGETS], weighing 1 each.
 *
 * Each event is posted live, so it gets a timestamp of its own. Then the function waits
 * until the events are older than the turbulence period of a catch-up.
 *
 * @param box The black box to post the events to.
 * @return The time to start the catch-up from, which precedes the events.
 */
private fun emitHistory(box: BlackBox): Timestamp {
    CounterView.changeWeightTo(1)
    val since = subtract(currentTime(), Durations.fromMinutes(1))
    repeat(EVENTS_PER_TARGET) {
        TARGETS.forEach { id ->
            box.receivesEvent(numberAdded { calculatorId = id })
        }
    }
    Thread.sleep(PAST_TURBULENCE_MILLIS)
    return since
}

/**
 * Returns the start signals of the given job in the inbox.
 */
private fun startSignalsOf(job: CatchUpId): List<InboxMessage> =
    inboxContents().filter {
        it.hasEvent() && (it.event.enclosedMessage() as? CatchUpStarted)?.id == job
    }

/**
 * Returns the messages that are still to be delivered, either live or for a catch-up.
 */
private fun undelivered(): List<InboxMessage> =
    inboxContents().filter {
        it.status == TO_DELIVER || it.status == TO_CATCH_UP
    }

private fun inboxContents(): List<InboxMessage> = InboxContents.get().values.flatten()

/**
 * Records each message sent to an inbox.
 *
 * A message sent to an inbox is written on its own. A delivery run writes the messages
 * it has processed in a batch, which is not recorded.
 */
private class RecordingInboxStorage :
    InboxStorage(InMemoryStorageFactory.newInstance(), false) {

    private val sent = CopyOnWriteArrayList<InboxMessage>()

    override fun write(message: InboxMessage) {
        sent.add(message)
        super.write(message)
    }

    /**
     * Returns the IDs of the instances that reported being prepared for the given job,
     * in the order of the reports.
     */
    fun reportsOf(job: CatchUpId): List<String> =
        sent.filter { it.hasEvent() }
            .map { it.event.enclosedMessage() }
            .filterIsInstance<EntityPreparedForCatchUp>()
            .filter { it.id == job }
            .map { Identifier.unpack(it.instanceId, MessageId::class.java) }
            .map { Identifier.unpack(it.id, String::class.java) }
}

/**
 * A catch-up storage whose `readAll()` may lag behind its writes.
 *
 * Once frozen, `readAll()` returns the jobs stored at that moment, until unfrozen.
 * The other reads and the writes go through.
 */
private class LaggingCatchUpStorage :
    CatchUpStorage(InMemoryStorageFactory.newInstance(), false) {

    private var frozen: List<CatchUp>? = null

    fun freezeQueries() {
        frozen = super.readAll().asSequence().toList()
    }

    fun unfreezeQueries() {
        frozen = null
    }

    override fun readAll(): Iterator<CatchUp> = frozen?.iterator() ?: super.readAll()
}

/**
 * Records the receptor failures, which `Delivery` does not rethrow.
 */
private class FailureRecorder(
    private val failures: MutableList<String>
) : DeliveryMonitor() {

    override fun onReceptionFailure(reception: FailedReception): FailedReception.Action {
        failures.add("Receptor: ${reception.error().message}")
        return super.onReceptionFailure(reception)
    }
}

/**
 * Runs each task in the calling thread or, while holding, keeps it until released.
 *
 * Holding emulates an executor that posts the system events after the delivery run ends.
 *
 * All the calls come from the test thread, so the executor needs no synchronization.
 */
private class HoldingExecutor : Executor {

    private val held = ArrayDeque<Runnable>()
    private var holding = false

    fun hold() {
        holding = true
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
