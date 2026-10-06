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

package io.spine.server.delivery.given.concurrency

import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.TimeoutException

/**
 * Runs the given action in a thread of its own, giving a test access to the outcome
 * of the action.
 *
 * The thread is started right away.
 *
 * @param action The action to run in the thread.
 */
internal class Worker(action: () -> Unit) {

    private val task = FutureTask(action)

    init {
        Thread(task, "test-worker").apply {
            isDaemon = true
            start()
        }
    }

    /**
     * Tells whether the action has completed, either normally or with a failure.
     */
    val isDone: Boolean
        get() = task.isDone

    /**
     * Waits for the action to complete.
     *
     * If the action failed, rethrows its failure.
     *
     * @throws IllegalStateException If the action does not complete in time.
     */
    fun result() {
        try {
            task.get(WAIT_LIMIT_SECONDS, SECONDS)
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        } catch (e: TimeoutException) {
            throw IllegalStateException("The worker did not complete in time.", e)
        }
    }
}
