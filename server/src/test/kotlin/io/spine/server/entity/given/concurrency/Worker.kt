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

import java.lang.Thread.State.BLOCKED
import java.lang.Thread.State.WAITING
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Runs the given action in a thread of its own, giving a test access to the outcome
 * of the action and letting the test wait until the thread stops at a lock.
 *
 * The thread is started right away.
 *
 * @param T The type of the result of the action.
 *
 * @param action The action to run in the thread.
 */
internal class Worker<T>(action: () -> T) {

    private val task = FutureTask(action)

    private val thread = Thread(task, "test-worker-${created.incrementAndGet()}").apply {
        isDaemon = true
        start()
    }

    /**
     * Tells whether the action has completed, either normally or with a failure.
     */
    val isDone: Boolean
        get() = task.isDone

    /**
     * Waits until the thread stops at a lock held by another thread.
     *
     * A thread awaiting a monitor is `BLOCKED`, and a thread awaiting
     * a `java.util.concurrent` lock is `WAITING`. A thread held at a [Gate] is neither:
     * it waits with a timeout. The state must persist over several probes, so that
     * a brief stop at an unrelated lock — e.g., the lock of a class loader — is not
     * taken for the awaited one.
     *
     * If the action completes instead, fails right away — with the failure of the action,
     * if there is one.
     */
    fun awaitBlocked() {
        val deadline = TimeSource.Monotonic.markNow() + WAIT_LIMIT_SECONDS.seconds
        var probes = 0
        while (probes < STEADY_PROBES) {
            if (isDone) {
                result()
                error("The worker completed instead of stopping at a lock.")
            }
            val state = thread.state
            check(deadline.hasNotPassedNow()) {
                "The worker did not stop at a lock in time. Its state is `$state`."
            }
            probes = if (state == BLOCKED || state == WAITING) probes + 1 else 0
            Thread.sleep(PROBE_INTERVAL_MILLIS)
        }
    }

    /**
     * Obtains the result of the action, waiting for the action to complete.
     *
     * If the action failed, rethrows its failure.
     */
    fun result(): T =
        try {
            task.get(WAIT_LIMIT_SECONDS, SECONDS)
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        } catch (e: TimeoutException) {
            throw IllegalStateException(
                "The worker did not complete in time. Its state is `${thread.state}`.", e
            )
        }

    private companion object {

        /**
         * How many times in a row the thread must be seen blocked.
         */
        const val STEADY_PROBES = 20

        /**
         * The number of workers created so far, which gives the threads distinct names.
         */
        val created = AtomicInteger()
    }
}
