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
import java.util.concurrent.TimeUnit.SECONDS

/**
 * The longest a test waits for something that is expected to happen, in seconds.
 *
 * Only a failing test waits this long.
 */
internal const val WAIT_LIMIT_SECONDS = 10L

/**
 * A point in the code under test at which a thread stops until the test lets it go.
 *
 * The thread under test calls [pass]. The test calls [awaitReached] to learn that
 * the thread has arrived, does what it has to do while the thread is held, and then calls [open].
 */
internal class Gate {

    private val reached = CountDownLatch(1)
    private val opened = CountDownLatch(1)

    /**
     * Tells the test that the calling thread has arrived, and holds the thread
     * until the gate is [opened][open].
     *
     * Returns right away if the gate is already open.
     */
    fun pass() {
        reached.countDown()
        check(opened.await(WAIT_LIMIT_SECONDS, SECONDS)) { "The gate was not opened in time." }
    }

    /**
     * Waits until a thread arrives at the gate.
     */
    fun awaitReached() {
        check(reached.await(WAIT_LIMIT_SECONDS, SECONDS)) { "No thread reached the gate in time." }
    }

    /**
     * Lets through the thread held at the gate, and all the threads arriving later.
     */
    fun open() {
        opened.countDown()
    }
}
