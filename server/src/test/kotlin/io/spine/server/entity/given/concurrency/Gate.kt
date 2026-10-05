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

package io.spine.server.entity.given.concurrency

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * The longest time, in seconds, that a test waits for something that is expected to happen.
 *
 * Only a failing test waits this long.
 */
internal const val WAIT_LIMIT_SECONDS = 10L

/**
 * How often, in milliseconds, a test probes a [Worker] it is waiting for.
 */
internal const val PROBE_INTERVAL_MILLIS = 2L

/**
 * A point in the code under test at which a thread stops until the test lets it go.
 *
 * The thread of a [Worker] calls [pass]. The test calls [awaitReached] to learn that
 * the worker has arrived, does what it has to do while the worker is held, and then
 * calls [open].
 */
internal class Gate {

    private val reached = CountDownLatch(1)
    private val opened = CountDownLatch(1)

    /**
     * Tells the test that the calling thread has arrived, and holds the thread
     * until the gate is [opened][open].
     *
     * Returns right away if the gate is already open.
     *
     * The thread waits with a timeout, so its state is `TIMED_WAITING`. This is how
     * [Worker.awaitBlocked] tells a thread held at a gate from a thread stopped at a lock.
     */
    fun pass() {
        reached.countDown()
        check(opened.await(WAIT_LIMIT_SECONDS, SECONDS)) { "The gate was not opened in time." }
    }

    /**
     * Waits until the given worker arrives at the gate.
     *
     * If the worker completes without arriving, fails right away — with the failure
     * of the worker, if there is one.
     *
     * @param by The worker expected at the gate.
     */
    fun awaitReached(by: Worker<*>) {
        val deadline = TimeSource.Monotonic.markNow() + WAIT_LIMIT_SECONDS.seconds
        while (!reached.await(PROBE_INTERVAL_MILLIS, MILLISECONDS)) {
            // A completed worker has made all its calls, so the count is final.
            if (by.isDone && reached.count > 0) {
                by.result()
                error("The worker completed without reaching the gate.")
            }
            check(deadline.hasNotPassedNow()) { "The worker did not reach the gate in time." }
        }
    }

    /**
     * Lets through the thread held at the gate, and all the threads arriving later.
     */
    fun open() {
        opened.countDown()
    }
}
