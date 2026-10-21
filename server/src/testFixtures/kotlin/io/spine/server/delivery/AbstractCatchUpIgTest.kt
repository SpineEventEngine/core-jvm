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
import io.spine.base.Time.currentTime
import io.spine.environment.Tests
import io.spine.server.ServerEnvironment
import io.spine.server.delivery.InboxMessageStatus.TO_CATCH_UP
import io.spine.server.delivery.InboxMessageStatus.TO_DELIVER
import io.spine.server.delivery.given.CounterView
import io.spine.server.under
import io.spine.test.delivery.numberAdded
import io.spine.testing.server.blackbox.BlackBox
import io.spine.type.TypeUrl
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.jvm.optionals.getOrNull
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

/**
 * An abstract base for the integration tests of a catch-up of [CounterView] projections.
 *
 * A test installs a `Delivery` with [useDelivery], catches up the [repository], and then
 * checks what the catch-up left behind: the [failures], the [jobStatuses], the [totals] of
 * the projections, and the [undelivered] messages. [emitHistory] posts the events for
 * a catch-up to replay.
 */
abstract class AbstractCatchUpIgTest : AbstractDeliveryTest() {

    /**
     * The repository of the projections to catch up.
     */
    protected val repository: CounterView.Repository = CounterView.Repository()

    private val recordedFailures = CopyOnWriteArrayList<String>()

    /**
     * The recorded failures of the delivery runs and of the receptors, and the stop of
     * the delivery by the watchdog, if any.
     */
    protected val failures: List<String>
        get() = recordedFailures

    /**
     * Sets the event weight, which `CounterView` keeps in a static field, back to
     * the default of 1.
     *
     * Runs before each test, since other suites leave the weight changed, and after each
     * test, since some tests change it.
     */
    @BeforeEach
    @AfterEach
    protected fun restoreDefaultWeight() {
        CounterView.changeWeightTo(1)
    }

    /**
     * Installs a `Delivery` that uses the given strategy, delivers synchronously
     * through the given observer, and records each failure in [failures].
     *
     * Without this recording, a failure would go unnoticed: `Delivery` only logs what
     * a shard observer throws, and `DeliveryMonitor` marks a message as delivered even when
     * its receptor failed.
     *
     * The monitor of the `Delivery` is a watchdog. Once 30 seconds have passed since this
     * call, it stops each delivery run, and the observer is no longer called. The stop is
     * recorded in [failures]. So a delivery that does not end, such as a livelock, is stopped
     * well before the test times out.
     *
     * Call it before creating a `BlackBox`: a projection repository binds its catch-up to
     * the `Delivery` in use when it is registered.
     *
     * @param strategy The strategy of the delivery.
     * @param observer The observer that delivers each message sent to an inbox,
     *   `LocalDispatchingObserver` by default.
     * @param configure Sets other properties of the delivery, such as its storages,
     *   the size of a page, or the deduplication window, which is zero by default.
     *   The monitor is set after this function runs, so the function cannot replace
     *   the watchdog. The function takes the builder as a parameter, not as a receiver:
     *   as a receiver, the builder's getters would hide the test's own properties of
     *   the same names, such as a storage.
     * @return The installed `Delivery`.
     */
    protected fun useDelivery(
        strategy: DeliveryStrategy,
        observer: ShardObserver = LocalDispatchingObserver(),
        configure: (DeliveryBuilder) -> Unit = {}
    ): Delivery {
        val watchdog = Watchdog()
        val builder = Delivery.newBuilder()
            .setStrategy(strategy)
            .setDeduplicationWindow(Durations.ZERO)
        configure(builder)
        val delivery = builder
            .setMonitor(watchdog)
            .build()
        delivery.subscribe { message ->
            if (watchdog.timeIsUp()) {
                return@subscribe
            }
            try {
                observer.onMessage(message)
            } catch (e: Exception) {
                recordedFailures.add("Delivery run: $e")
                throw e
            }
        }
        under<Tests> { use(delivery) }
        return delivery
    }

    /**
     * Returns the statuses of the catch-up jobs of the projections with the given state type,
     * those of `CounterView` by default.
     */
    protected fun jobStatuses(
        stateType: TypeUrl = CounterView.projectionType()
    ): List<CatchUpStatus> =
        ServerEnvironment.instance()
            .delivery()
            .catchUpStorage()
            .readByType(stateType)
            .asSequence()
            .map { it.status }
            .toList()

    /**
     * Returns the totals of the projections with the given IDs,
     * or `null` for those that are not stored.
     */
    protected fun totals(vararg ids: String): Map<String, Int?> =
        ids.associateWith { id ->
            repository.find(id).getOrNull()?.state()?.total
        }

    /**
     * Returns the messages that are still to be delivered, either live or for a catch-up.
     */
    protected fun undelivered(): List<InboxMessage> =
        inboxContents().filter { it.status == TO_DELIVER || it.status == TO_CATCH_UP }

    /**
     * Returns the messages of all the inboxes.
     */
    protected fun inboxContents(): List<InboxMessage> = InboxContents.get().values.flatten()

    /**
     * Emits [eventsPerTarget] events to each of the projections with the given [ids].
     *
     * Each event is posted live, so it gets a timestamp of its own. Then the function waits
     * until the events are older than the turbulence period of a catch-up. That way,
     * a catch-up replays them while `IN_PROGRESS`, rather than all at once while `FINALIZING`.
     *
     * @return The time since when to catch up, which precedes the events.
     */
    protected fun emitHistory(
        context: BlackBox,
        eventsPerTarget: Int,
        vararg ids: String
    ): Timestamp {
        val since = subtract(currentTime(), Durations.fromMinutes(1))
        repeat(eventsPerTarget) {
            ids.forEach { id ->
                context.receivesEvent(numberAdded { calculatorId = id })
            }
        }
        waitPastTurbulence()
        return since
    }

    /**
     * Waits until the events posted so far are older than the turbulence period of a catch-up.
     */
    protected fun waitPastTurbulence() {
        Thread.sleep(PAST_TURBULENCE_MILLIS)
    }

    /**
     * Records the receptor failures, which `Delivery` does not throw, and stops
     * the delivery once 30 seconds have passed since the watchdog was created.
     */
    private inner class Watchdog : DeliveryMonitor() {

        private val deadline = TimeSource.Monotonic.markNow() + WATCHDOG_SECONDS.seconds

        /**
         * Tells whether the time is up, recording it as a failure if so.
         */
        fun timeIsUp(): Boolean {
            val up = deadline.hasPassedNow()
            if (up) {
                recordedFailures.addIfAbsent(STOPPED_BY_WATCHDOG)
            }
            return up
        }

        override fun shouldContinueAfter(stage: DeliveryStage): Boolean = !timeIsUp()

        override fun onReceptionFailure(reception: FailedReception): FailedReception.Action {
            val error = reception.error()
            recordedFailures.add("Receptor: ${error.type}: ${error.message}")
            return super.onReceptionFailure(reception)
        }
    }

    private companion object {

        /**
         * A pause longer than the 500 ms turbulence period of a catch-up.
         */
        const val PAST_TURBULENCE_MILLIS = 1_000L

        /**
         * The time after which the watchdog stops a delivery that does not end.
         *
         * It is well within the timeout of a test.
         */
        const val WATCHDOG_SECONDS = 30

        const val STOPPED_BY_WATCHDOG = "Stopped by the watchdog."
    }
}
