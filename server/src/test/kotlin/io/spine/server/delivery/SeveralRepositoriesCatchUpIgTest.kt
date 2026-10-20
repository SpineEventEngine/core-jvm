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
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.spine.base.Identifier
import io.spine.base.Time.currentTime
import io.spine.environment.Tests
import io.spine.server.BoundedContext
import io.spine.server.delivery.CatchUpStatus.COMPLETED
import io.spine.server.delivery.DeliveryStrategy.newIndex
import io.spine.server.delivery.InboxMessageStatus.TO_CATCH_UP
import io.spine.server.delivery.InboxMessageStatus.TO_DELIVER
import io.spine.server.delivery.event.CatchUpCompleted
import io.spine.server.delivery.event.ShardProcessed
import io.spine.server.delivery.event.historyEventsRecalled
import io.spine.server.delivery.event.shardProcessingRequested
import io.spine.server.delivery.given.FirstTally
import io.spine.server.delivery.given.FirstTallyRepository
import io.spine.server.delivery.given.SecondTally
import io.spine.server.delivery.given.SecondTallyRepository
import io.spine.server.event.AbstractEventReactor
import io.spine.server.event.NoReaction
import io.spine.server.event.React
import io.spine.server.under
import io.spine.test.delivery.numberAdded
import io.spine.testing.SlowTest
import io.spine.testing.server.blackbox.BlackBox
import io.spine.type.TypeUrl
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.jvm.optionals.getOrNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Tests the catch-up of a projection type, when the `Delivery` of the JVM serves
 * more than one projection repository, or more than one Bounded Context.
 *
 * Each projection repository creates a `CatchUpProcess` of its own, and each `CatchUpProcess`
 * a `ShardMaintenanceProcess` of its own. The tests check that a catch-up completes and
 * rebuilds the instances of its repository, regardless of the order in which
 * the repositories and the contexts are created.
 *
 * Both projection types, [FirstTally] and [SecondTally], count the same events of
 * the same calculators. Thus, their instances have the same IDs.
 */
@SlowTest
@DisplayName("Catch-up of a projection that shares the `Delivery` with others should")
internal class SeveralRepositoriesCatchUpIgTest : AbstractDeliveryTest() {

    private val first = FirstTallyRepository()
    private val second = SecondTallyRepository()

    /**
     * The failures of the delivery runs and of the receptors.
     */
    private val failures = CopyOnWriteArrayList<String>()

    private lateinit var delivery: Delivery

    @AfterEach
    fun resetWeights() {
        FirstTally.weight = 1
        SecondTally.weight = 1
    }

    @ParameterizedTest(name = WITH_SHARDS)
    @ValueSource(ints = [1, 3])
    fun `rebuild the instances of the repository registered last`(shards: Int) {
        useDelivery(shards)
        BlackBox.singleTenantWith(first, second).use { context ->
            emit(context, EVENTS_PER_TARGET)
            waitPastTurbulence()

            SecondTally.weight = 100
            second.catchUpAll(anHourAgo())
            context.receivesEvent(numberAdded { calculatorId = ALPHA })

            assertSoftly {
                failures.shouldBeEmpty()
                jobStatuses(SecondTally.type) shouldBe listOf(COMPLETED)
                secondTotals() shouldBe mapOf(ALPHA to 400, BETA to 300)
                firstTotals() shouldBe mapOf(ALPHA to 4, BETA to 3)
                undelivered().shouldBeEmpty()
            }
        }
    }

    @ParameterizedTest(name = WITH_SHARDS)
    @ValueSource(ints = [1, 3])
    fun `rebuild the instances of a repository registered before another one`(shards: Int) {
        useDelivery(shards)
        BlackBox.singleTenantWith(first, second).use { context ->
            emit(context, EVENTS_PER_TARGET)
            waitPastTurbulence()

            FirstTally.weight = 100
            first.catchUpAll(anHourAgo())
            context.receivesEvent(numberAdded { calculatorId = ALPHA })

            assertSoftly {
                failures.shouldBeEmpty()
                jobStatuses(FirstTally.type) shouldBe listOf(COMPLETED)
                firstTotals() shouldBe mapOf(ALPHA to 400, BETA to 300)
                secondTotals() shouldBe mapOf(ALPHA to 4, BETA to 3)
                undelivered().shouldBeEmpty()
            }
        }
    }

    @ParameterizedTest(name = WITH_SHARDS)
    @ValueSource(ints = [1, 3])
    fun `rebuild the selected instances of a repository registered before another one`(
        shards: Int
    ) {
        useDelivery(shards)
        BlackBox.singleTenantWith(first, second).use { context ->
            emit(context, EVENTS_PER_TARGET)
            waitPastTurbulence()

            FirstTally.weight = 100
            first.catchUp(anHourAgo(), setOf(ALPHA))
            context.receivesEvent(numberAdded { calculatorId = BETA })

            assertSoftly {
                failures.shouldBeEmpty()
                jobStatuses(FirstTally.type) shouldBe listOf(COMPLETED)
                firstTotals() shouldBe mapOf(ALPHA to 300, BETA to 103)
                secondTotals() shouldBe mapOf(ALPHA to 3, BETA to 4)
                undelivered().shouldBeEmpty()
            }
        }
    }

    @ParameterizedTest(name = WITH_SHARDS)
    @ValueSource(ints = [1, 3])
    fun `rebuild the instances of both repositories, one after the other`(shards: Int) {
        useDelivery(shards)
        BlackBox.singleTenantWith(first, second).use { context ->
            emit(context, EVENTS_PER_TARGET)
            waitPastTurbulence()

            FirstTally.weight = 100
            first.catchUpAll(anHourAgo())
            SecondTally.weight = 10
            second.catchUpAll(anHourAgo())
            context.receivesEvent(numberAdded { calculatorId = ALPHA })

            assertSoftly {
                failures.shouldBeEmpty()
                jobStatuses(FirstTally.type) shouldBe listOf(COMPLETED)
                jobStatuses(SecondTally.type) shouldBe listOf(COMPLETED)
                firstTotals() shouldBe mapOf(ALPHA to 400, BETA to 300)
                secondTotals() shouldBe mapOf(ALPHA to 40, BETA to 30)
                undelivered().shouldBeEmpty()
            }
        }
    }

    /**
     * Emits more events to the second context than to the first one. Thus, a catch-up
     * that read the history of the other context would count a wrong total.
     */
    @ParameterizedTest(name = WITH_SHARDS)
    @ValueSource(ints = [1, 3])
    fun `rebuild the instances of a repository whose context is built before another one`(
        shards: Int
    ) {
        useDelivery(shards)
        BlackBox.singleTenant(FIRST_CONTEXT, first).use { firstContext ->
            BlackBox.singleTenant(SECOND_CONTEXT, second).use { secondContext ->
                emit(firstContext, EVENTS_PER_TARGET)
                emit(secondContext, MORE_EVENTS_PER_TARGET)
                waitPastTurbulence()

                FirstTally.weight = 100
                first.catchUpAll(anHourAgo())
                firstContext.receivesEvent(numberAdded { calculatorId = ALPHA })

                assertSoftly {
                    failures.shouldBeEmpty()
                    jobStatuses(FirstTally.type) shouldBe listOf(COMPLETED)
                    firstTotals() shouldBe mapOf(ALPHA to 400, BETA to 300)
                    secondTotals() shouldBe mapOf(ALPHA to 5, BETA to 5)
                    undelivered().shouldBeEmpty()
                }
            }
        }
    }

    /**
     * Registers a witness in each context to record the catch-up signals posted there:
     * `ShardProcessed` by the shard maintenance process, and `CatchUpCompleted` by
     * the catch-up process. Only the context of the repository should see them.
     */
    @ParameterizedTest(name = WITH_SHARDS)
    @ValueSource(ints = [1, 3])
    fun `post the signals of a catch-up only in the context of its repository`(shards: Int) {
        useDelivery(shards)
        val firstWitness = SignalWitness()
        val secondWitness = SignalWitness()
        val firstSetup = BoundedContext.singleTenant(FIRST_CONTEXT)
            .add(first)
            .addEventDispatcher(firstWitness)
        val secondSetup = BoundedContext.singleTenant(SECOND_CONTEXT)
            .add(second)
            .addEventDispatcher(secondWitness)
        BlackBox.from(firstSetup).use { firstContext ->
            BlackBox.from(secondSetup).use {
                emit(firstContext, EVENTS_PER_TARGET)
                waitPastTurbulence()

                first.catchUpAll(anHourAgo())

                assertSoftly {
                    failures.shouldBeEmpty()
                    jobStatuses(FirstTally.type) shouldBe listOf(COMPLETED)
                    firstWitness.signals shouldContainAll listOf(
                        ShardProcessed::class.java,
                        CatchUpCompleted::class.java
                    )
                    secondWitness.signals.shouldBeEmpty()
                }
            }
        }
    }

    /**
     * Posts a request without delivering it, and checks how many times it is stored.
     */
    @Test
    fun `store a shard maintenance request once, in a context of several repositories`() {
        useDelivery(shards = 1, dispatchOnWrite = false)
        BlackBox.singleTenantWith(first, second).use { context ->
            context.receivesEvent(shardProcessingRequested {
                process = jobOf(FirstTally.type.value())
                index = theOnlyShard()
            })

            undelivered() shouldHaveSize 1
        }
    }

    @ParameterizedTest(name = WITH_SHARDS)
    @ValueSource(ints = [1, 3])
    fun `complete if a context without projections is built first`(shards: Int) {
        useDelivery(shards)
        BlackBox.singleTenant(EMPTY_CONTEXT).use {
            BlackBox.singleTenant(FIRST_CONTEXT, first).use { context ->
                catchUpTheOnlyRepository(context)
            }
        }
    }

    @ParameterizedTest(name = WITH_SHARDS)
    @ValueSource(ints = [1, 3])
    fun `complete if a context without projections is built last`(shards: Int) {
        useDelivery(shards)
        BlackBox.singleTenant(FIRST_CONTEXT, first).use { context ->
            BlackBox.singleTenant(EMPTY_CONTEXT).use {
                catchUpTheOnlyRepository(context)
            }
        }
    }

    /**
     * Delivers a signal of a catch-up of an unknown projection type together with
     * a live event in a single run.
     */
    @Test
    fun `fail the reception of a catch-up signal that no process serves`() {
        useDelivery(shards = 1, dispatchOnWrite = false)
        BlackBox.singleTenantWith(first).use { context ->
            context.receivesEvent(historyEventsRecalled { id = jobOf(UNKNOWN_PROJECTION_TYPE) })
            context.receivesEvent(numberAdded { calculatorId = ALPHA })

            delivery.deliverMessagesFrom(theOnlyShard())

            assertSoftly {
                failures shouldHaveSize 1
                failures.forAll { it shouldContain UNKNOWN_PROJECTION_TYPE }
                firstTotals() shouldBe mapOf(ALPHA to 1, BETA to null)
                undelivered().shouldBeEmpty()
            }
        }
    }

    @Test
    fun `fail the reception of a shard maintenance request of a closed repository`() {
        useDelivery(shards = 1, dispatchOnWrite = false)
        BlackBox.singleTenantWith(first).use { context ->
            context.receivesEvent(shardProcessingRequested {
                process = jobOf(FirstTally.type.value())
                index = theOnlyShard()
            })
        }

        // The context, and so its repository, is closed by now.
        delivery.deliverMessagesFrom(theOnlyShard())

        assertSoftly {
            failures shouldHaveSize 1
            failures.forAll { it shouldContain FirstTally.type.value() }
            undelivered().shouldBeEmpty()
        }
    }

    /**
     * Makes the `Delivery` know the second repository, as the repository does when it is
     * registered, but registers the repository nowhere. So, it has no catch-up process
     * to serve the job.
     */
    @Test
    fun `fail the reception of a catch-up signal of a repository not registered yet`() {
        useDelivery(shards = 1, dispatchOnWrite = false)
        delivery.newCatchUpProcess(second)
        BlackBox.singleTenantWith(first).use { context ->
            context.receivesEvent(historyEventsRecalled { id = jobOf(SecondTally.type.value()) })

            delivery.deliverMessagesFrom(theOnlyShard())

            assertSoftly {
                failures shouldHaveSize 1
                failures.forAll { it shouldContain SecondTally.type.value() }
                undelivered().shouldBeEmpty()
            }
        }
    }

    /**
     * Uses a `Delivery` with the given number of shards, and records each failure.
     *
     * A failure would go unnoticed otherwise: `Delivery` only logs what a shard observer
     * throws, and `DeliveryMonitor` marks a message as delivered when its receptor fails.
     *
     * Unless told otherwise, the `Delivery` delivers synchronously, through
     * `LocalDispatchingObserver`, each time a message is written.
     */
    private fun useDelivery(shards: Int, dispatchOnWrite: Boolean = true) {
        delivery = Delivery.newBuilder()
            .setStrategy(UniformAcrossAllShards.forNumber(shards))
            .setDeduplicationWindow(Durations.ZERO)
            .setMonitor(FailureRecorder())
            .build()
        if (dispatchOnWrite) {
            val dispatching = LocalDispatchingObserver()
            delivery.subscribe { message ->
                try {
                    dispatching.onMessage(message)
                } catch (e: RuntimeException) {
                    failures.add("Delivery run: $e")
                    throw e
                }
            }
        }
        under<Tests> { use(delivery) }
    }

    private fun catchUpTheOnlyRepository(context: BlackBox) {
        emit(context, EVENTS_PER_TARGET)
        waitPastTurbulence()

        FirstTally.weight = 100
        first.catchUpAll(anHourAgo())
        context.receivesEvent(numberAdded { calculatorId = ALPHA })

        assertSoftly {
            failures.shouldBeEmpty()
            jobStatuses(FirstTally.type) shouldBe listOf(COMPLETED)
            firstTotals() shouldBe mapOf(ALPHA to 400, BETA to 300)
            undelivered().shouldBeEmpty()
        }
    }

    /**
     * Emits the given number of events to each of [ALPHA] and [BETA].
     *
     * Each event is posted live, so it gets a timestamp of its own.
     */
    private fun emit(context: BlackBox, eventsPerTarget: Int) {
        repeat(eventsPerTarget) {
            listOf(ALPHA, BETA).forEach { id ->
                context.receivesEvent(numberAdded { calculatorId = id })
            }
        }
    }

    /**
     * Waits until the emitted events are older than the turbulence period of a catch-up.
     *
     * That way, a catch-up replays them while `IN_PROGRESS`, rather than all at once
     * while `FINALIZING`.
     */
    private fun waitPastTurbulence() = Thread.sleep(PAST_TURBULENCE_MILLIS)

    private fun jobStatuses(projectionType: TypeUrl): List<CatchUpStatus> =
        delivery.catchUpStorage()
            .readByType(projectionType)
            .asSequence()
            .map { it.status }
            .toList()

    /**
     * Returns the totals of [FirstTally] projections, or `null` for those not stored.
     */
    private fun firstTotals(): Map<String, Int?> =
        listOf(ALPHA, BETA).associateWith { id ->
            first.find(id).getOrNull()?.state?.total
        }

    /**
     * Returns the totals of [SecondTally] projections, or `null` for those not stored.
     */
    private fun secondTotals(): Map<String, Int?> =
        listOf(ALPHA, BETA).associateWith { id ->
            second.find(id).getOrNull()?.state?.lastValue
        }

    /**
     * Describes the messages that are still to be delivered, either live or for a catch-up.
     */
    private fun undelivered(): List<String> =
        InboxContents.get().values.flatten()
            .filter { it.status == TO_DELIVER || it.status == TO_CATCH_UP }
            .map { it.describe() }

    /**
     * Records the failures of the receptors, which `Delivery` does not throw.
     */
    private inner class FailureRecorder : DeliveryMonitor() {

        override fun onReceptionFailure(reception: FailedReception): FailedReception.Action {
            failures.add("Receptor: ${reception.error().message}")
            return super.onReceptionFailure(reception)
        }
    }

    /**
     * Records the classes of the catch-up signals posted in its Bounded Context.
     *
     * Reacts to `ShardProcessed`, emitted by the shard maintenance process, and to
     * `CatchUpCompleted`, emitted by the catch-up process.
     */
    private class SignalWitness : AbstractEventReactor() {

        val signals = CopyOnWriteArrayList<Class<*>>()

        @React
        fun on(event: ShardProcessed): NoReaction {
            signals.add(event.javaClass)
            return noReaction()
        }

        @React
        fun on(event: CatchUpCompleted): NoReaction {
            signals.add(event.javaClass)
            return noReaction()
        }
    }

    private companion object {
        const val ALPHA = "alpha"
        const val BETA = "beta"
        const val EVENTS_PER_TARGET = 3
        const val MORE_EVENTS_PER_TARGET = 5
        const val FIRST_CONTEXT = "First"
        const val SECOND_CONTEXT = "Second"
        const val EMPTY_CONTEXT = "Empty"
        const val WITH_SHARDS = "with {0} shard(s)"

        /**
         * The type URL of a projection that no repository serves.
         */
        const val UNKNOWN_PROJECTION_TYPE = "type.spine.io/spine.test.delivery.UnknownView"

        /**
         * Exceeds the 500 ms turbulence period of a catch-up.
         */
        const val PAST_TURBULENCE_MILLIS = 1_000L

        fun anHourAgo(): Timestamp = subtract(currentTime(), Durations.fromHours(1))

        fun theOnlyShard(): ShardIndex = newIndex(0, 1)

        /**
         * Creates the ID of a catch-up job for the projections of the given type.
         */
        fun jobOf(type: String): CatchUpId = catchUpId {
            uuid = Identifier.newUuid()
            projectionType = type
        }
    }
}

/**
 * Describes this message by the type of its signal, and by the type and the ID of its target.
 */
private fun InboxMessage.describe(): String {
    val signal = if (hasEvent()) event.enclosedMessage().javaClass.simpleName else "Command"
    val target = inboxId.typeUrl.substringAfterLast('.')
    val id = Identifier.unpack(inboxId.entityId.id)
    return "$signal to $target `$id` ($status)"
}
