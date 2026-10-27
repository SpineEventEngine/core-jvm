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

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.spine.core.Event
import io.spine.core.Subscribe
import io.spine.grpc.StreamObservers.memoizingObserver
import io.spine.server.BoundedContext
import io.spine.server.BoundedContextBuilder
import io.spine.server.delivery.CatchUpStatus.COMPLETED
import io.spine.server.delivery.event.AllShardsFinalized
import io.spine.server.delivery.event.CatchUpCompleted
import io.spine.server.delivery.event.CatchUpRequested
import io.spine.server.delivery.event.CatchUpStarted
import io.spine.server.delivery.event.EntityPreparedForCatchUp
import io.spine.server.delivery.event.FinalizationStarted
import io.spine.server.delivery.event.HistoryEventsRecalled
import io.spine.server.delivery.event.HistoryFullyRecalled
import io.spine.server.delivery.event.LiveEventsPickedUp
import io.spine.server.delivery.event.ShardProcessed
import io.spine.server.delivery.event.ShardProcessingRequested
import io.spine.server.event.AbstractEventSubscriber
import io.spine.server.event.EventStore
import io.spine.server.event.EventStreamQuery
import io.spine.system.server.SystemBoundedContexts.systemOf
import io.spine.testing.SlowTest
import io.spine.testing.server.blackbox.BlackBox
import io.spine.type.TypeUrl
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests where the events of a catch-up are posted and stored.
 *
 * They are posted in the System context of the projections, so they are stored only if
 * that context is told to store its events. The dispatchers of the context of
 * the projections are registered in its System context too, so they get these events.
 */
@SlowTest
@DisplayName("Catch-up should")
internal class CatchUpEventsIgTest : AbstractCatchUpIgTest() {

    @Test
    fun `store none of its events by default`() {
        useDelivery(UniformAcrossAllShards.forNumber(1))
        val context = BoundedContextBuilder.assumingTests()
            .add(repository)
            .build()
        BlackBox.from(context).use { box ->
            val since = emitHistory(box, EVENTS_PER_TARGET, FIRST, SECOND)
            repository.catchUpAll(since)

            assertSoftly {
                failures.shouldBeEmpty()
                jobStatuses() shouldBe listOf(COMPLETED)
                withClue("The catch-up events in the domain event store") {
                    catchUpEventsIn(context.eventBus().eventStore()).shouldBeEmpty()
                }
                withClue("The catch-up events in the system event store") {
                    catchUpEventsIn(systemOf(context).eventBus().eventStore()).shouldBeEmpty()
                }
            }
        }
    }

    @Test
    fun `store its events in the System context told to store its events`() {
        useDelivery(UniformAcrossAllShards.forNumber(1))
        val builder = BoundedContextBuilder.assumingTests().add(repository)
        builder.systemSettings().persistEvents()
        val context = builder.build()
        BlackBox.from(context).use { box ->
            val since = emitHistory(box, EVENTS_PER_TARGET, FIRST, SECOND)
            repository.catchUpAll(since)

            assertSoftly {
                failures.shouldBeEmpty()
                jobStatuses() shouldBe listOf(COMPLETED)
                withClue("The catch-up events in the domain event store") {
                    catchUpEventsIn(context.eventBus().eventStore()).shouldBeEmpty()
                }
                withClue("The catch-up events in the system event store") {
                    catchUpEventsIn(systemOf(context).eventBus().eventStore())
                        .distinct() shouldContainAll CATCH_UP_EVENTS
                }
            }
        }
    }

    @Test
    fun `pass its completion to a subscriber in the context of the projections`() {
        useDelivery(UniformAcrossAllShards.forNumber(1))
        val subscriber = CompletionSubscriber()
        val context = BoundedContext.singleTenant("CatchUpCompletion")
            .add(repository)
            .addEventDispatcher(subscriber)
            .build()
        BlackBox.from(context).use { box ->
            val since = emitHistory(box, EVENTS_PER_TARGET, FIRST, SECOND)
            val job = repository.catchUpAll(since)

            assertSoftly {
                failures.shouldBeEmpty()
                subscriber.completed shouldContainExactly listOf(job)
            }
        }
    }

    /**
     * Returns the simple names of the catch-up events in the given store, one per event.
     */
    private fun catchUpEventsIn(store: EventStore): List<String> {
        val observer = memoizingObserver<Event>()
        store.read(EventStreamQuery.getDefaultInstance(), observer)
        return observer.responses()
            .map { TypeUrl.parse(it.message.typeUrl).toJavaClass().simpleName }
            .filter { it in CATCH_UP_EVENTS }
    }

    /**
     * Remembers the IDs of the completed catch-ups.
     */
    private class CompletionSubscriber : AbstractEventSubscriber() {

        val completed = mutableListOf<CatchUpId>()

        @Subscribe
        fun on(event: CatchUpCompleted) {
            completed.add(event.id)
        }
    }

    private companion object {

        const val FIRST = "first"
        const val SECOND = "second"
        const val EVENTS_PER_TARGET = 3

        /**
         * The simple names of the events, which a catch-up of all the stored instances emits.
         */
        val CATCH_UP_EVENTS = listOf(
            CatchUpRequested::class, CatchUpStarted::class, EntityPreparedForCatchUp::class,
            HistoryEventsRecalled::class, HistoryFullyRecalled::class, FinalizationStarted::class,
            LiveEventsPickedUp::class, ShardProcessingRequested::class, ShardProcessed::class,
            AllShardsFinalized::class, CatchUpCompleted::class
        ).map { it.java.simpleName }
    }
}
