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

import com.google.protobuf.util.Durations
import com.google.protobuf.util.Timestamps.subtract
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.spine.base.Time.currentTime
import io.spine.core.Event
import io.spine.server.BoundedContext
import io.spine.server.delivery.CatchUpStatus.COMPLETED
import io.spine.server.delivery.CatchUpStatus.STARTED
import io.spine.server.delivery.InboxLabel.CATCH_UP
import io.spine.server.delivery.InboxLabel.UPDATE_SUBSCRIBER
import io.spine.server.delivery.InboxMessageStatus.TO_CATCH_UP
import io.spine.server.delivery.event.catchUpStarted
import io.spine.server.delivery.given.TallyA1Repository
import io.spine.server.delivery.given.TestCatchUpJobs.catchUpJob
import io.spine.server.delivery.given.TestCatchUpJobs.writeJob
import io.spine.server.storage.memory.InMemoryStorageFactory
import io.spine.test.delivery.TallyA1
import io.spine.test.delivery.numberAdded
import io.spine.testing.server.TestEventFactory
import io.spine.testing.server.blackbox.BlackBox
import io.spine.type.TypeUrl
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Tests the delivery of the messages that a completed catch-up held back, when they do not
 * fit in one page.
 *
 * While a catch-up finalizes, the `Delivery` holds back the messages of its targets: the live
 * events, and the copies of the last events of the history, which the catch-up reads once
 * more. A live event posted just before that last read is in the inbox twice: as the live
 * event held back, and as its copy. The live event also comes before the copies of the events
 * that preceded it, because the copies are written later. Once the catch-up completes,
 * the `Delivery` delivers each of these events once, in the order of the events.
 *
 * The tests write such an inbox by hand. A tally gets three events. The first two come only
 * as copies. The third one comes twice: first as the live event held back, and then as its
 * copy. The job of the catch-up is completed.
 */
@DisplayName("`Delivery`, given the messages held back for a completed catch-up, should")
internal class HeldCatchUpMessagesIgTest : AbstractCatchUpIgTest() {

    private val tallies = TallyA1Repository()
    private val events = TestEventFactory.newInstance(javaClass)

    @ParameterizedTest(name = "with pages of {0}")
    @ValueSource(ints = [1, 2, 3])
    fun `deliver each event once and in order, if the messages take several pages`(
        pageSize: Int
    ) {
        val delivery = useDelivery(UniformAcrossAllShards.singleShard(), observer = { }) {
            it.setPageSize(pageSize)
        }
        BlackBox.from(BoundedContext.singleTenant("Tallies").add(tallies)).use {
            writeJob(delivery.catchUpStorage(), completedJob())
            writeHeld(delivery)

            delivery.deliverMessagesFrom(SHARD)

            assertSoftly {
                failures.shouldBeEmpty()
                withClue("The values of the tally") {
                    tallies.valuesOf(TALLY) shouldBe listOf(1, 2, 3)
                }
                withClue("The messages left to deliver") {
                    undelivered().shouldBeEmpty()
                }
            }
        }
    }

    /**
     * Starts a new catch-up of the tally right after the `Delivery` reads the jobs for
     * the first page, and before it reads the held messages from the later pages.
     *
     * The start signal of the new catch-up is among those messages. The `Delivery` must not
     * deliver it as a message of the completed job. The new job also catches up another tally,
     * whose start signal is not in the inbox yet. So the new job stays started, and holds
     * the messages of the tally until its own catch-up goes on.
     */
    @Test
    fun `leave the held messages to a catch-up started while they are read`() {
        val storage = StartingCatchUpStorage()
        val delivery = useDelivery(UniformAcrossAllShards.singleShard(), observer = { }) {
            it.setPageSize(2)
                .setCatchUpStorage(storage)
        }
        BlackBox.from(BoundedContext.singleTenant("Tallies").add(tallies)).use {
            writeJob(storage, completedJob())
            val newJob = startedJob()
            val held = writeHeld(delivery)
            val startSignal = message(startSignalOf(newJob), CATCH_UP)
            delivery.inboxStorage().write(startSignal.id, startSignal)
            storage.afterFirstRead = { writeJob(storage, newJob) }

            delivery.deliverMessagesFrom(SHARD)

            assertSoftly {
                failures.shouldBeEmpty()
                withClue("The tally, reset by the new catch-up") {
                    tallies.valuesOf(TALLY).shouldBeNull()
                }
                withClue("The messages left to deliver") {
                    undelivered().map { it.id } shouldContainExactlyInAnyOrder held.map { it.id }
                }
            }
        }
    }

    /**
     * Writes the held messages of the [TALLY] to the inbox, in the order they are received.
     *
     * @return the written messages
     */
    private fun writeHeld(delivery: Delivery): List<InboxMessage> {
        val (first, second, third) = (1..3).map { added(it) }
        val held = listOf(
            message(third, UPDATE_SUBSCRIBER),
            message(first, CATCH_UP),
            message(second, CATCH_UP),
            message(third, CATCH_UP)
        )
        val inbox = delivery.inboxStorage()
        held.forEach { inbox.write(it.id, it) }
        return held
    }

    /**
     * Creates an event that adds the given number to the [TALLY].
     */
    private fun added(number: Int): Event =
        events.createEvent(numberAdded {
            calculatorId = TALLY
            value = number
        })

    private fun startSignalOf(job: CatchUp): Event =
        events.createEvent(catchUpStarted { id = job.id })

    /**
     * Creates the message of the given event sent to the [TALLY], held for the catch-up.
     *
     * @param label The label of a live event, or that of a message sent by the catch-up.
     */
    private fun message(event: Event, label: InboxLabel): InboxMessage = inboxMessage {
        id = InboxMessageMixin.generateIdWith(SHARD)
        signalId = inboxSignalId { value = event.id.value }
        inboxId = InboxIds.wrap(TALLY, TYPE)
        this.label = label
        status = TO_CATCH_UP
        whenReceived = currentTime()
        this.event = event
    }

    private fun completedJob(): CatchUp =
        catchUpJob(TYPE, COMPLETED, aMinuteAgo(), listOf<Any>(TALLY))

    /**
     * Creates a started job of catching up the [TALLY] and another tally, waiting for both
     * of them to be reset.
     */
    private fun startedJob(): CatchUp =
        catchUpJob(TYPE, STARTED, aMinuteAgo(), listOf<Any>(TALLY, ANOTHER_TALLY))
            .toBuilder()
            .setInstancesToClear(2)
            .build()

    private fun aMinuteAgo() = subtract(currentTime(), Durations.fromMinutes(1))

    private companion object {
        const val TALLY = "tally"
        const val ANOTHER_TALLY = "another-tally"
        val TYPE: TypeUrl = TypeUrl.of(TallyA1::class.java)
        val SHARD: ShardIndex = DeliveryStrategy.newIndex(0, 1)
    }
}

/**
 * A storage of catch-up jobs that runs an action once, right after the first read of the jobs.
 */
private class StartingCatchUpStorage :
    CatchUpStorage(InMemoryStorageFactory.newInstance(), false) {

    var afterFirstRead: (() -> Unit)? = null

    override fun readJobs(): Iterator<CatchUp> {
        val jobs = super.readJobs().asSequence().toList()
        afterFirstRead?.let { action ->
            afterFirstRead = null
            action()
        }
        return jobs.iterator()
    }
}
