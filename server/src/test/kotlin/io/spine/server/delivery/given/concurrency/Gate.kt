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

// TODO:2026-10-05:claude: Replace `Gate` and `Worker` with the helpers of the same names
//  from `io.spine.server.entity.given.concurrency` once the fix for issue #1678 is merged,
//  and move the hold limit to the `Gate` there, keeping it above the wait limit.
//  See https://github.com/SpineEventEngine/core-jvm/issues/1678.

package io.spine.server.delivery.given.concurrency

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS

/**
 * The longest a test waits for something that is expected to happen, in seconds.
 *
 * Only a failing test waits this long.
 */
internal const val WAIT_LIMIT_SECONDS = 10L

/**
 * The longest a thread is held at a [Gate], in seconds.
 *
 * Exceeds the [wait limit][WAIT_LIMIT_SECONDS], so that a test waiting for a thread
 * which is stuck behind the held one fails because of that thread, and not because
 * the gate gave up first.
 */
private const val HOLD_LIMIT_SECONDS = 2 * WAIT_LIMIT_SECONDS

/**
 * A point at which a thread stops until the test lets it go.
 *
 * The thread to hold calls [pass]. The test calls [awaitReached] to learn that
 * the thread has arrived, acts while the thread is held, and then calls [open].
 */
internal class Gate {

    private val reached = CountDownLatch(1)
    private val opened = CountDownLatch(1)

    /**
     * Tells the test that the calling thread has arrived, and holds the thread
     * until the gate is [opened][open].
     *
     * @throws IllegalStateException If the gate is not opened in time.
     */
    fun pass() {
        reached.countDown()
        check(opened.await(HOLD_LIMIT_SECONDS, SECONDS)) { "The gate was not opened in time." }
    }

    /**
     * Waits until the given worker arrives at the gate.
     *
     * If the worker does not arrive in time because its action has failed,
     * rethrows the failure of the action.
     *
     * @param by The worker expected at the gate.
     * @throws IllegalStateException If the worker neither arrives nor fails in time.
     */
    fun awaitReached(by: Worker) {
        if (reached.await(WAIT_LIMIT_SECONDS, SECONDS)) {
            return
        }
        if (by.isDone) {
            by.result()
        }
        error("The worker did not reach the gate in time.")
    }

    /**
     * Lets through the thread held at the gate.
     */
    fun open() {
        opened.countDown()
    }
}
