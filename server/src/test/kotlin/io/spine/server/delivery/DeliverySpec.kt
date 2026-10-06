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

import com.google.common.collect.ImmutableList
import com.google.protobuf.Duration
import com.google.protobuf.Message
import com.google.protobuf.Timestamp
import com.google.protobuf.util.Durations.fromMillis
import com.google.protobuf.util.Durations.fromSeconds
import com.google.protobuf.util.Timestamps
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.spine.base.Time
import io.spine.environment.Tests
import io.spine.server.ContextSpec
import io.spine.server.ServerEnvironment
import io.spine.server.delivery.CatchUpStatus.FINALIZING
import io.spine.server.delivery.CatchUpStatus.IN_PROGRESS
import io.spine.server.delivery.InboxLabel.UPDATE_SUBSCRIBER
import io.spine.server.delivery.InboxMessageStatus.TO_CATCH_UP
import io.spine.server.delivery.given.TestCatchUpJobs.catchUpJob
import io.spine.server.dispatch.DispatchOutcome
import io.spine.server.dispatch.DispatchOutcomes.successfulOutcome
import io.spine.server.entity.Repository
import io.spine.server.entity.given.concurrency.Gate
import io.spine.server.entity.given.concurrency.Worker
import io.spine.server.storage.DelegatingRecordStorage
import io.spine.server.storage.RecordSpec
import io.spine.server.storage.RecordStorage
import io.spine.server.storage.RecordWithColumns
import io.spine.server.storage.StorageFactory
import io.spine.server.storage.StorageGroup
import io.spine.server.storage.memory.InMemoryStorageFactory
import io.spine.server.type.EventEnvelope
import io.spine.test.delivery.Calc
import io.spine.test.delivery.PositiveNumberEmitted
import io.spine.test.delivery.positiveNumberEmitted
import io.spine.testing.server.TestEventFactory
import io.spine.type.TypeUrl
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Tests how a [Delivery] reads a shard page by page, each next page holding the messages
 * received after the last message of the previous page.
 *
 * The cases deliver events to the targets `X` and `Y` through a real `Delivery` and
 * [InboxStorage], and record the deliveries as `target:value`. The time of receiving of
 * each message is set by the [clock][setClock].
 */
@DisplayName("`Delivery` should")
internal class DeliverySpec : AbstractDeliveryTest() {

    private val storage = StubInboxStorage()
    private val monitor = StubMonitor()
    private val received = CopyOnWriteArrayList<String>()
    private val events = TestEventFactory.newInstance(DeliverySpec::class.java)
    private val shard = DeliveryStrategy.newIndex(0, 1)
    private val baseTime = Time.currentTime()

    /**
     * Restores the clock set by [setClock].
     */
    @AfterEach
    fun resetClock() {
        Time.resetProvider()
    }

    @Nested inner class
    `deliver a message before the later ones of its target` {

        @Test
        fun `if it is stored after a later message is read`() {
            val delivery = useDelivery(pageSize = 500)
            val inbox = newInbox(delivery)
            setClock(20)
            inbox.deliver("Y", 1)
            monitor.afterNextStage {
                setClock(10)
                inbox.deliver("X", 1)
                setClock(30)
                inbox.deliver("X", 2)
            }

            delivery.deliverMessagesFrom(shard)

            received shouldContainExactly listOf("Y:1", "X:1", "X:2")
        }

        /**
         * The write of the first message is held at a gate inside the record storage.
         * As `InboxStorage` takes no lock, a message of another target is stored and
         * read meanwhile.
         */
        @Test
        fun `if storing it takes longer than storing a later message`() {
            val delivery = useDelivery(pageSize = 500)
            val inbox = newInbox(delivery)
            val gate = Gate()
            storage.holdNextWrite(gate)
            setClock(10)
            val producer = Worker {
                inbox.deliver("X", 1)
                inbox.deliver("X", 2)
            }
            try {
                gate.awaitReached(by = producer)
                setClock(20)
                inbox.deliver("Y", 1)
                monitor.afterNextStage {
                    setClock(30)
                    gate.open()
                    producer.result()
                }

                delivery.deliverMessagesFrom(shard)
            } finally {
                gate.open()
            }

            received shouldContainExactly listOf("Y:1", "X:1", "X:2")
        }

        /**
         * The search for the messages left behind follows the reading of the next page.
         * Had the search come first, `X:1` would only be found after `X:2` is delivered.
         */
        @Test
        fun `if it and a later one are stored right after a search for messages left behind`() {
            val delivery = useDelivery(pageSize = 500)
            val inbox = newInbox(delivery)
            setClock(20)
            inbox.deliver("Y", 1)
            storage.afterNextSearch {
                setClock(10)
                inbox.deliver("X", 1)
                setClock(30)
                inbox.deliver("X", 2)
            }

            delivery.deliverMessagesFrom(shard)

            received shouldContainExactly listOf("Y:1", "X:1", "X:2")
        }
    }

    /**
     * The delivered messages are kept for the deduplication window, so they stay
     * in the first page.
     */
    @Nested inner class
    `deliver all the messages received at the same time` {

        @Test
        fun `if a page ends among them`() {
            val delivery = useDelivery(pageSize = 2, window = fromSeconds(30))
            val inbox = newInbox(delivery)
            setClock(0)
            inbox.deliver("X", 1)
            inbox.deliver("X", 2)
            inbox.deliver("X", 3)

            delivery.deliverMessagesFrom(shard)

            received shouldContainExactly listOf("X:1", "X:2", "X:3")
        }

        /**
         * Each message is delivered as soon as it is stored, so the third one finds
         * the first two kept in the first page.
         */
        @Test
        fun `with the synchronous local observer`() {
            val delivery = useDelivery(pageSize = 2, window = fromSeconds(30))
            delivery.subscribe(LocalDispatchingObserver())
            val inbox = newInbox(delivery)
            setClock(0)

            inbox.deliver("X", 1)
            inbox.deliver("X", 2)
            inbox.deliver("X", 3)

            received shouldContainExactly listOf("X:1", "X:2", "X:3")
        }
    }

    @Test
    fun `deliver the messages left behind in stages of at most a page`() {
        val delivery = useDelivery(pageSize = 2)
        val inbox = newInbox(delivery)
        setClock(20)
        inbox.deliver("Y", 1)
        monitor.afterNextStage {
            leaveBehindFiveMessages(inbox)
        }

        delivery.deliverMessagesFrom(shard)

        received shouldContainExactly listOf("Y:1", "X:1", "X:2", "X:3", "X:4", "X:5")
        monitor.stageSizes shouldContainExactly listOf(1, 2, 2, 1)
    }

    @Test
    fun `stop delivering the messages left behind if the monitor tells to`() {
        val delivery = useDelivery(pageSize = 2)
        val inbox = newInbox(delivery)
        setClock(20)
        inbox.deliver("Y", 1)
        monitor.afterNextStage {
            leaveBehindFiveMessages(inbox)
            monitor.stopAfterNextStage()
        }

        delivery.deliverMessagesFrom(shard)
        val deliveredFirst = received.toList()
        delivery.deliverMessagesFrom(shard)

        deliveredFirst shouldContainExactly listOf("Y:1", "X:1", "X:2")
        received shouldContainExactly listOf("Y:1", "X:1", "X:2", "X:3", "X:4", "X:5")
    }

    /**
     * A message left behind goes through the same stations as the messages of a page,
     * so a catch-up of its target applies to it.
     */
    @Nested inner class
    `handle a message left behind for a catching-up target` {

        @Test
        fun `by dropping it while the catch-up is in progress`() {
            val delivery = useDelivery(pageSize = 500)
            catchUp(delivery, target = "X", status = IN_PROGRESS)
            leaveBehindOneMessage(delivery)

            delivery.deliverMessagesFrom(shard)

            received shouldContainExactly listOf("Y:1")
            storedStatuses().shouldBeEmpty()
        }

        @Test
        fun `by pausing it while the catch-up is finalizing`() {
            val delivery = useDelivery(pageSize = 500)
            catchUp(delivery, target = "X", status = FINALIZING)
            leaveBehindOneMessage(delivery)

            delivery.deliverMessagesFrom(shard)

            received shouldContainExactly listOf("Y:1")
            storedStatuses() shouldContainExactly listOf(TO_CATCH_UP)
        }

        /**
         * Stores the message for `Y`, and makes the end of the stage delivering it store
         * a message for `X` received earlier.
         */
        private fun leaveBehindOneMessage(delivery: Delivery) {
            val inbox = newInbox(delivery)
            setClock(20)
            inbox.deliver("Y", 1)
            monitor.afterNextStage {
                setClock(10)
                inbox.deliver("X", 1)
            }
        }

        /**
         * Puts the catch-up of the given target into the given status.
         */
        private fun catchUp(delivery: Delivery, target: String, status: CatchUpStatus) {
            val job = catchUpJob(TypeUrl.of(Calc::class.java), status, baseTime, listOf(target))
            delivery.catchUpStorage().write(job)
        }

        /**
         * Obtains the statuses of the messages stored in the shard.
         */
        private fun storedStatuses(): List<InboxMessageStatus> =
            storage.readAll(shard, Int.MAX_VALUE)
                .contents()
                .map { it.status }
    }

    /**
     * Creates a `Delivery` over the [storage] and the [monitor] of this test, with a single
     * shard, and makes it the `Delivery` of the server environment.
     */
    private fun useDelivery(
        pageSize: Int,
        window: Duration = Duration.getDefaultInstance()
    ): Delivery {
        val delivery = Delivery.newBuilder()
            .setInboxStorage(storage)
            .setStrategy(UniformAcrossAllShards.singleShard())
            .setMonitor(monitor)
            .setPageSize(pageSize)
            .setDeduplicationWindow(window)
            .build()
        ServerEnvironment.under(Tests::class.java).use(delivery)
        return delivery
    }

    /**
     * Creates an `Inbox` which records the events it delivers into [received].
     */
    private fun newInbox(delivery: Delivery): Inbox<String> =
        delivery.newInbox<String>(TypeUrl.of(Calc::class.java))
            .addEventEndpoint(UPDATE_SUBSCRIBER) { event -> Recorder(event, received) }
            .build()

    /**
     * Stores the messages `X:1` to `X:5` received before the message for `Y`, in this order.
     */
    private fun leaveBehindFiveMessages(inbox: Inbox<String>) {
        for (value in 1..5) {
            setClock(10L + value)
            inbox.deliver("X", value)
        }
    }

    /**
     * Sends an event with the given value to the given target through this inbox.
     */
    private fun Inbox<String>.deliver(target: String, number: Int) {
        val message = positiveNumberEmitted {
            id = target
            value = number
        }
        val event = events.createEvent(message)
        send(EventEnvelope.of(event)).toSubscriber(target)
    }

    /**
     * Makes the clock show the time of the start of this test plus the given number
     * of milliseconds.
     */
    private fun setClock(millis: Long) {
        val time = Timestamps.add(baseTime, fromMillis(millis))
        Time.setProvider { time }
    }
}

/**
 * An `InboxStorage` in memory, which can hold the next write at a [Gate] inside
 * the record storage, and run an action after the next search for the messages left behind.
 *
 * @property factory The factory of the record storage, which can hold a write.
 */
private class StubInboxStorage(
    private val factory: HoldingStorageFactory = HoldingStorageFactory()
) : InboxStorage(factory, false) {

    private val afterSearch = AtomicReference<(() -> Unit)?>()

    /**
     * Makes the next write of a record pass the given gate inside the record storage,
     * where a real storage does its I/O.
     */
    fun holdNextWrite(gate: Gate) {
        factory.holdNextWrite(gate)
    }

    /**
     * Makes the next search for the messages left behind run the given action
     * before returning what it has found.
     */
    fun afterNextSearch(action: () -> Unit) {
        afterSearch.set(action)
    }

    override fun readToDeliver(
        index: ShardIndex,
        receivedUpTo: Timestamp
    ): ImmutableList<InboxMessage> {
        val found = super.readToDeliver(index, receivedUpTo)
        afterSearch.getAndSet(null)?.invoke()
        return found
    }
}

/**
 * Creates in-memory record storages which hold the next write of a record at a [Gate].
 */
private class HoldingStorageFactory : StorageFactory {

    private val delegate = InMemoryStorageFactory.newInstance()
    private val nextWrite = AtomicReference<Gate?>()

    /**
     * Makes the next write of a record pass the given gate.
     */
    fun holdNextWrite(gate: Gate) {
        nextWrite.set(gate)
    }

    override fun <I : Any, R : Message> createRecordStorage(
        context: ContextSpec,
        recordSpec: RecordSpec<I, R>,
        group: StorageGroup?
    ): RecordStorage<I, R> {
        val records = delegate.createRecordStorage(context, recordSpec, group)
        return HoldingRecords(context, delegate = records, nextWrite = nextWrite)
    }

    override fun isOpen(): Boolean = delegate.isOpen

    override fun close() = delegate.close()
}

/**
 * A record storage which makes the calling thread pass the gate set for the next write,
 * if any, before writing a record.
 *
 * @param I The type of the record identifiers.
 * @param R The type of the stored records.
 *
 * @param context The specification of the context in which the storage is used.
 * @param delegate The storage doing the actual work.
 * @property nextWrite The gate for the next write to pass.
 */
private class HoldingRecords<I : Any, R : Message>(
    context: ContextSpec,
    delegate: RecordStorage<I, R>,
    private val nextWrite: AtomicReference<Gate?>
) : DelegatingRecordStorage<I, R>(context, delegate) {

    override fun write(record: RecordWithColumns<I, R>) {
        nextWrite.getAndSet(null)?.pass()
        super.write(record)
    }
}

/**
 * A `DeliveryMonitor` that records how many messages each stage delivers, can run
 * an action after the next stage, and can stop the delivery after the next stage.
 */
private class StubMonitor : DeliveryMonitor() {

    private val sizes = CopyOnWriteArrayList<Int>()
    private val afterStage = AtomicReference<(() -> Unit)?>()
    private val stopAt = AtomicInteger(Int.MAX_VALUE)

    /**
     * The numbers of the messages delivered by the stages, in their order.
     */
    val stageSizes: List<Int>
        get() = sizes

    /**
     * Makes the end of the next stage run the given action.
     */
    fun afterNextStage(action: () -> Unit) {
        afterStage.set(action)
    }

    /**
     * Makes the delivery stop after the next stage.
     */
    fun stopAfterNextStage() {
        stopAt.set(sizes.size + 1)
    }

    override fun shouldContinueAfter(stage: DeliveryStage): Boolean {
        sizes.add(stage.messagesDelivered)
        afterStage.getAndSet(null)?.invoke()
        return sizes.size != stopAt.get()
    }
}

/**
 * An endpoint recording each delivery of its event as `target:value`.
 *
 * @property event The event to deliver.
 * @property log The list to record the deliveries into.
 */
private class Recorder(
    private val event: EventEnvelope,
    private val log: MutableList<String>
) : MessageEndpoint<String, EventEnvelope> {

    override fun dispatchTo(targetId: String): DispatchOutcome {
        val number = event.message() as PositiveNumberEmitted
        log.add("$targetId:${number.value}")
        return successfulOutcome(event)
    }

    override fun onDuplicate(target: String, envelope: EventEnvelope) = Unit

    override fun repository(): Repository<String, *> =
        error("The endpoint of this test has no repository.")
}
