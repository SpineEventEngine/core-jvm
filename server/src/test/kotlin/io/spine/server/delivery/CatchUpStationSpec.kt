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
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.spine.base.Time.currentTime
import io.spine.server.delivery.CatchUpStatus.COMPLETED
import io.spine.server.delivery.CatchUpStatus.FINALIZING
import io.spine.server.delivery.CatchUpStatus.IN_PROGRESS
import io.spine.server.delivery.CatchUpStatus.STARTED
import io.spine.server.delivery.InboxMessageStatus.DELIVERED
import io.spine.server.delivery.InboxMessageStatus.TO_CATCH_UP
import io.spine.server.delivery.event.catchUpStarted
import io.spine.server.delivery.given.CounterView
import io.spine.server.delivery.given.TestCatchUpJobs.catchUpJob
import io.spine.server.delivery.given.TestInboxMessages.catchingUp
import io.spine.server.delivery.given.TestInboxMessages.copyWithStatus
import io.spine.server.delivery.given.TestInboxMessages.toDeliver
import io.spine.testing.server.TestEventFactory
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * Tests how [CatchUpStation] processes the messages when several jobs are stored,
 * one of them completed.
 *
 * The job of a completed catch-up stays in the storage. So, once the same projection
 * instances are caught up again, their messages match both the completed job and the new one.
 *
 * `CatchUpStationTest` covers the cases with a single job.
 */
@DisplayName("`CatchUpStation` should")
internal class CatchUpStationSpec {

    private val type = CounterView.projectionType()
    private val action = MemoizingAction.empty()

    @ParameterizedTest(name = COMPLETED_JOB_READ)
    @EnumSource(JobOrder::class)
    fun `follow the job in progress, if a completed job matches the messages too`(
        order: JobOrder
    ) {
        val replayed = catchingUp(TARGET, type)
        val live = toDeliver(TARGET, type)
        val conveyor = conveyorWith(replayed, live)
        val jobs = order.arrange(completedJob(), job(IN_PROGRESS))

        val result = CatchUpStation(action, jobs).process(conveyor)

        result.deliveredCount() shouldBe 1
        result.errors().hasErrors() shouldBe false
        action.passedMessages().shouldNotBeNull() shouldContainExactly listOf(replayed)
        conveyor.recentlyDelivered().toList() shouldContainExactly
                listOf(copyWithStatus(replayed, DELIVERED))
        conveyor.removals().asSequence().toList() shouldContainExactly listOf(live)
    }

    @ParameterizedTest(name = COMPLETED_JOB_READ)
    @EnumSource(JobOrder::class)
    fun `follow the finalizing job, if a completed job matches the messages too`(
        order: JobOrder
    ) {
        val replayed = catchingUp(TARGET, type)
        val live = toDeliver(TARGET, type)
        val conveyor = conveyorWith(replayed, live)
        val jobs = order.arrange(completedJob(), job(FINALIZING))

        val result = CatchUpStation(action, jobs).process(conveyor)

        result.deliveredCount() shouldBe 0
        action.passedMessages().shouldBeNull()
        conveyor.toList() shouldContainExactlyInAnyOrder
                listOf(replayed, copyWithStatus(live, TO_CATCH_UP))
    }

    @ParameterizedTest(name = COMPLETED_JOB_READ)
    @EnumSource(JobOrder::class)
    fun `follow the started job, if a completed job matches the start signal too`(
        order: JobOrder
    ) {
        val started = job(STARTED)
        val signal = startSignal(started)
        val conveyor = conveyorWith(signal)
        val jobs = order.arrange(completedJob(), started)

        val result = CatchUpStation(action, jobs).process(conveyor)

        result.deliveredCount() shouldBe 1
        result.errors().hasErrors() shouldBe false
        action.passedMessages().shouldNotBeNull() shouldContainExactly listOf(signal)
        // Not kept for longer, which only the completed job does.
        conveyor.toList() shouldContainExactly listOf(copyWithStatus(signal, DELIVERED))
    }

    @ParameterizedTest(name = COMPLETED_JOB_READ)
    @EnumSource(JobOrder::class)
    fun `follow the completed job, if the job in progress does not match the message`(
        order: JobOrder
    ) {
        val replayed = catchingUp(ANOTHER_TARGET, type)
        val conveyor = conveyorWith(replayed)
        val jobs = order.arrange(completedJob(), job(IN_PROGRESS))

        val result = CatchUpStation(action, jobs).process(conveyor)

        result.deliveredCount() shouldBe 1
        action.passedMessages().shouldNotBeNull() shouldContainExactly listOf(replayed)
        val contents = conveyor.toList()
        contents shouldHaveSize 1
        val delivered = contents.single()
        delivered.status shouldBe DELIVERED
        delivered.hasKeepUntil() shouldBe true
    }

    @Test
    fun `deliver a replayed message once, if two completed jobs match it`() {
        val replayed = catchingUp(TARGET, type)
        val conveyor = conveyorWith(replayed)
        val jobs = listOf(completedJob(), completedJob())

        val result = CatchUpStation(action, jobs).process(conveyor)

        result.deliveredCount() shouldBe 1
        result.errors().hasErrors() shouldBe false
        action.passedMessages().shouldNotBeNull() shouldContainExactly listOf(replayed)
        val contents = conveyor.toList()
        contents shouldHaveSize 1
        val delivered = contents.single()
        delivered.status shouldBe DELIVERED
        delivered.hasKeepUntil() shouldBe true
    }

    /**
     * Creates a job of catching up [TARGET].
     */
    private fun job(status: CatchUpStatus): CatchUp =
        catchUpJob(type, status, currentTime(), listOf<Any>(TARGET))

    /**
     * Creates a completed job of catching up all the instances.
     */
    private fun completedJob(): CatchUp =
        catchUpJob(type, COMPLETED, currentTime(), null)

    private fun conveyorWith(vararg messages: InboxMessage): Conveyor =
        Conveyor(messages.toList(), DeliveredMessagesCache())

    /**
     * Creates the `CatchUpStarted` signal of the given job, sent to [TARGET].
     */
    private fun startSignal(job: CatchUp): InboxMessage {
        val signal = TestEventFactory.newInstance(javaClass)
            .createEvent(catchUpStarted { id = job.id })
        val target = InboxIds.wrap(TARGET, type)
        return inboxMessage {
            id = InboxMessageMixin.generateIdWith(DeliveryStrategy.newIndex(0, 1))
            signalId = inboxSignalId { value = signal.id.value }
            inboxId = target
            label = InboxLabel.CATCH_UP
            status = TO_CATCH_UP
            whenReceived = currentTime()
            event = signal
        }
    }

    /**
     * The order in which the storage returns two jobs, one of which is completed.
     */
    internal enum class JobOrder(

        /**
         * Tells where the completed job is, in the names of the parameterized tests.
         */
        private val label: String
    ) {

        COMPLETED_FIRST("first"),
        COMPLETED_LAST("last");

        /**
         * Returns the passed jobs in this order.
         */
        fun arrange(completed: CatchUp, ongoing: CatchUp): List<CatchUp> = when (this) {
            COMPLETED_FIRST -> listOf(completed, ongoing)
            COMPLETED_LAST -> listOf(ongoing, completed)
        }

        override fun toString(): String = label
    }

    private companion object {
        const val TARGET = "target"
        const val ANOTHER_TARGET = "another-target"

        /**
         * The name of a test parameterized with [JobOrder].
         */
        const val COMPLETED_JOB_READ = "with the completed job read {0}"
    }
}
