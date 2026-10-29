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
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.spine.base.Time.currentTime
import io.spine.server.delivery.CatchUpStatus.COMPLETED
import io.spine.server.delivery.event.FinalizationStarted
import io.spine.server.delivery.event.LiveEventsPickedUp
import io.spine.server.delivery.event.ShardProcessingRequested
import io.spine.server.delivery.given.RecordingInboxStorage
import io.spine.server.delivery.given.TallyA1Repository
import io.spine.server.delivery.given.TallyA2Repository
import io.spine.server.delivery.given.TallyContexts
import io.spine.server.delivery.given.TallyRepository
import io.spine.server.delivery.given.TallyWeight
import io.spine.server.event.AbstractEventReactor
import io.spine.server.event.NoReaction
import io.spine.server.event.React
import io.spine.test.delivery.TallyA1
import io.spine.test.delivery.TallyA2
import io.spine.testing.SlowTest
import io.spine.type.TypeUrl
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests how the messages of a catch-up reach the processes in the System context
 * of the projections.
 */
@SlowTest
@DisplayName("Catch-up in a System context should")
internal class CatchUpRoutingIgTest : AbstractCatchUpIgTest() {

    private val inboxStorage = RecordingInboxStorage()
    private val contexts = TallyContexts()

    @BeforeEach
    @AfterEach
    fun restoreTallyWeight() {
        TallyWeight.reset()
    }

    @AfterEach
    fun closeContexts() {
        contexts.close()
    }

    @Test
    fun `deliver each shard request in its shard, and its answer to its job`() {
        useDelivery(UniformAcrossAllShards.forNumber(3)) { builder ->
            builder.setInboxStorage(inboxStorage)
        }
        val first = TallyA1Repository()
        val second = TallyA2Repository()
        contexts.create(FIRST, first)
        contexts.create(SECOND, second)
        contexts.create("WithoutProjections")
        listOf(FIRST, SECOND).forEach { contexts.emit(context = it, ALPHA, BETA) }
        waitPastTurbulence()

        TallyWeight.value = CAUGHT_UP_WEIGHT
        val firstJob = first.catchUpAll(anHourAgo())
        val secondJob = second.catchUpAll(anHourAgo())

        val requests = inboxStorage.messagesSent()
            .filter { it.hasEvent() && it.event.enclosedMessage() is ShardProcessingRequested }
        assertSoftly {
            failures.shouldBeEmpty()
            jobStatuses(TypeUrl.of(TallyA1::class.java)) shouldBe listOf(COMPLETED)
            jobStatuses(TypeUrl.of(TallyA2::class.java)) shouldBe listOf(COMPLETED)
            withClue("The jobs that requested the processing of a shard") {
                requests.map { requestIn(it).process }.distinct() shouldContainAll
                        listOf(firstJob, secondJob)
            }
            requests.forEach { message ->
                val request = requestIn(message)
                withClue("The shard of the request `$request`") {
                    message.id.index shouldBe request.index
                }
                withClue("The process addressed by the request `$request`") {
                    InboxIds.unwrap(message.inboxId) shouldBe shardMaintenanceId {
                        shard = request.index
                        job = request.process
                    }
                }
            }
            first.shouldBeCaughtUp(FIRST)
            second.shouldBeCaughtUp(SECOND)
            undelivered().shouldBeEmpty()
        }
    }

    @Test
    fun `deliver the live events emitted while it finalizes`() {
        useDelivery(UniformAcrossAllShards.forNumber(3))
        val tallies = TallyA1Repository()
        val reactor = LiveEventsWhileFinalizing(FIRST)
        contexts.create(FIRST, tallies, dispatcher = reactor)
        contexts.emit(context = FIRST, ALPHA, BETA)
        waitPastTurbulence()

        TallyWeight.value = CAUGHT_UP_WEIGHT
        tallies.catchUpAll(anHourAgo())

        assertSoftly {
            failures.shouldBeEmpty()
            jobStatuses(TypeUrl.of(TallyA1::class.java)) shouldBe listOf(COMPLETED)
            withClue("The live events posted before and after the final read") {
                reactor.postedBefore shouldBe true
                reactor.postedAfter shouldBe true
            }
            tallies.shouldBeCaughtUp(FIRST)
            undelivered().shouldBeEmpty()
        }
    }

    @Test
    fun `keep the storage of the jobs open when another System context closes`() {
        val delivery = useDelivery(UniformAcrossAllShards.forNumber(3))
        contexts.create(FIRST, TallyA1Repository())
        val tallies = TallyA2Repository()
        contexts.create(SECOND, tallies)
        contexts.emit(context = SECOND, ALPHA, BETA)
        waitPastTurbulence()

        contexts.close(FIRST)
        TallyWeight.value = CAUGHT_UP_WEIGHT
        tallies.catchUpAll(anHourAgo())

        assertSoftly {
            failures.shouldBeEmpty()
            delivery.catchUpStorage().isOpen shouldBe true
            jobStatuses(TypeUrl.of(TallyA2::class.java)) shouldBe listOf(COMPLETED)
            tallies.shouldBeCaughtUp(SECOND)
        }
    }

    /**
     * Checks that the [ALPHA] and [BETA] tallies of the given context hold all their values
     * at the weight of a catch-up.
     */
    private fun TallyRepository<*, *>.shouldBeCaughtUp(context: String) {
        listOf(ALPHA, BETA).forEach { id ->
            withClue("The tally `$id` of `$context`") {
                valuesOf(id) shouldBe
                        contexts.caughtUp(context = context, id = id, weight = CAUGHT_UP_WEIGHT)
            }
        }
    }

    private fun requestIn(message: InboxMessage): ShardProcessingRequested =
        message.event.enclosedMessage() as ShardProcessingRequested

    /**
     * Posts a live event to [ALPHA] of the given context when the catch-up starts finalizing,
     * which is before the final read of the history, and once more after that read.
     */
    private inner class LiveEventsWhileFinalizing(
        private val context: String
    ) : AbstractEventReactor() {

        var postedBefore = false
        var postedAfter = false

        @React
        fun on(@Suppress("UNUSED_PARAMETER") event: FinalizationStarted): NoReaction {
            if (!postedBefore) {
                postedBefore = true
                contexts.post(context = context, id = ALPHA, live = true)
            }
            return noReaction()
        }

        @React
        fun on(@Suppress("UNUSED_PARAMETER") event: LiveEventsPickedUp): NoReaction {
            if (!postedAfter) {
                postedAfter = true
                contexts.post(context = context, id = ALPHA, live = true)
            }
            return noReaction()
        }
    }

    private companion object {

        const val FIRST = "First"
        const val SECOND = "Second"
        const val ALPHA = "alpha"
        const val BETA = "beta"
        const val CAUGHT_UP_WEIGHT = 100

        fun anHourAgo(): Timestamp = subtract(currentTime(), Durations.fromHours(1))
    }
}
