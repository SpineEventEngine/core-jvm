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

package io.spine.server.event

import com.google.common.collect.ImmutableSet
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.spine.environment.Tests
import io.spine.server.BoundedContextBuilder
import io.spine.server.delivery.AbstractDeliveryTest
import io.spine.server.delivery.Delivery
import io.spine.server.delivery.DeliveryStrategy
import io.spine.server.delivery.DeliveryStrategy.newIndex
import io.spine.server.delivery.LocalDispatchingObserver
import io.spine.server.delivery.ShardIndex
import io.spine.server.type.EventEnvelope
import io.spine.server.under
import io.spine.test.delivery.DCounter
import io.spine.test.delivery.NumberAdded
import io.spine.test.delivery.numberAdded
import io.spine.testing.server.blackbox.BlackBox
import io.spine.type.TypeUrl
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The calculator whose event a test holds in the handler.
 */
private const val HELD = "held"

/**
 * How long a test waits for a thread to reach a point.
 */
private const val WAIT_SECONDS = 10L

@DisplayName("`AbstractStatefulReactor` should")
internal class AbstractStatefulReactorSpec : AbstractDeliveryTest() {

    @Test
    fun `dispatch the events delivered through its inbox to itself, by default`() {
        under<Tests> { use(Delivery.local()) }
        val reactor = CountingReactor()
        val setup = BoundedContextBuilder.assumingTests()
            .addEventDispatcher(reactor)
        BlackBox.from(setup).use { context ->
            repeat(2) {
                context.receivesEvent(numberAdded { calculatorId = CALCULATOR })
            }

            reactor.totalOf(CALCULATOR) shouldBe 2
        }
    }

    /**
     * Holds the dispatching of one event in its handler, while a reactor of the same state
     * type takes over the delivery, and another event of another shard arrives.
     *
     * Without a lock shared by the reactors of the type, the second event would be dispatched
     * to the serving reactor at once, through the endpoint of the newer reactor, alongside
     * the first one.
     */
    @Test
    fun `dispatch the events of a state type one at a time, whichever reactor delivers them`() {
        under<Tests> { use(deliveryOfTwoShards()) }
        val serving = TakenOverReactor(servedBy = null)
        val setup = BoundedContextBuilder.assumingTests()
            .addEventDispatcher(serving)
        BlackBox.from(setup).use { context ->
            val held = thread { context.receivesEvent(numberAdded { calculatorId = HELD }) }
            serving.entered.await(WAIT_SECONDS, SECONDS) shouldBe true

            // Takes over the delivery of the events of the state type.
            TakenOverReactor(servedBy = serving)
            val overlapping = thread {
                context.receivesEvent(numberAdded { calculatorId = OVERLAPPING })
            }
            Thread.sleep(OVERLAP_MILLIS)
            serving.release.countDown()
            held.join()
            overlapping.join()

            assertSoftly {
                serving.mostAtOnce shouldBe 1
                serving.totalOf(HELD) shouldBe 1
                serving.totalOf(OVERLAPPING) shouldBe 1
            }
        }
    }

    private companion object {
        const val CALCULATOR = "calculator"
        const val OVERLAPPING = "overlapping"

        /**
         * Gives the second event the time to be dispatched, if nothing holds it back.
         */
        const val OVERLAP_MILLIS = 500L

        /**
         * Creates a `Delivery` that delivers synchronously, putting [HELD] to the first of
         * two shards, and any other target to the second one.
         */
        fun deliveryOfTwoShards(): Delivery {
            val delivery = Delivery.newBuilder()
                .setStrategy(HeldApart())
                .build()
            delivery.subscribe(LocalDispatchingObserver())
            return delivery
        }
    }

    private class HeldApart : DeliveryStrategy() {

        override fun indexFor(entityId: Any, entityStateType: TypeUrl): ShardIndex =
            newIndex(if (entityId == HELD) 0 else 1, shardCount())

        override fun shardCount(): Int = 2
    }
}

/**
 * Counts the [NumberAdded] events of each calculator, keeping the counters in memory.
 *
 * Keeps the default choice of the reactor receiving an event, so it dispatches each event
 * delivered through its inbox to itself.
 */
private class CountingReactor :
    AbstractStatefulReactor<String, DCounter, DCounter.Builder>(TypeUrl.of(DCounter::class.java)) {

    private val counters = ConcurrentHashMap<String, DCounter>()

    @React
    fun on(@Suppress("UNUSED_PARAMETER") event: NumberAdded): NoReaction {
        builder().total += 1
        return noReaction()
    }

    fun totalOf(calculator: String): Int? = counters[calculator]?.total

    override fun load(id: String): Optional<DCounter> = Optional.ofNullable(counters[id])

    override fun store(updatedState: DCounter) {
        counters[updatedState.id] = updatedState
    }

    override fun newStateBuilderWith(id: String): DCounter.Builder =
        DCounter.newBuilder().setId(id)

    override fun route(event: EventEnvelope): ImmutableSet<String> {
        val message = event.message() as NumberAdded
        return ImmutableSet.of(message.calculatorId)
    }
}

/**
 * Counts the [NumberAdded] events of each calculator, and records how many of them
 * are dispatched at once.
 *
 * Holds the event of the [HELD] calculator in the handler until [release] opens.
 *
 * @param servedBy The reactor serving the targets of this one, or `null` if this reactor
 *   serves them itself.
 */
private class TakenOverReactor(private val servedBy: TakenOverReactor?) :
    AbstractStatefulReactor<String, DCounter, DCounter.Builder>(TypeUrl.of(DCounter::class.java)) {

    val entered = CountDownLatch(1)
    val release = CountDownLatch(1)
    private val inside = AtomicInteger()
    private val most = AtomicInteger()
    private val counters = ConcurrentHashMap<String, DCounter>()

    /**
     * The most events this reactor dispatched at once.
     */
    val mostAtOnce: Int
        get() = most.get()

    @React
    fun on(event: NumberAdded): NoReaction {
        most.accumulateAndGet(inside.incrementAndGet(), ::maxOf)
        if (event.calculatorId == HELD) {
            entered.countDown()
            release.await(WAIT_SECONDS, SECONDS)
        }
        builder().total += 1
        inside.decrementAndGet()
        return noReaction()
    }

    fun totalOf(calculator: String): Int? = counters[calculator]?.total

    override fun reactorFor(
        targetId: String
    ): AbstractStatefulReactor<String, DCounter, DCounter.Builder> = servedBy ?: this

    override fun load(id: String): Optional<DCounter> = Optional.ofNullable(counters[id])

    override fun store(updatedState: DCounter) {
        counters[updatedState.id] = updatedState
    }

    override fun newStateBuilderWith(id: String): DCounter.Builder =
        DCounter.newBuilder().setId(id)

    override fun route(event: EventEnvelope): ImmutableSet<String> {
        val message = event.message() as NumberAdded
        return ImmutableSet.of(message.calculatorId)
    }
}
