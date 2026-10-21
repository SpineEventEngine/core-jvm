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

package io.spine.server.delivery.given

import java.util.concurrent.Executor

/**
 * Runs each task in the calling thread or, while holding, keeps it until released.
 *
 * Holding stands for an executor that posts the system events after the delivery run ends.
 *
 * The executor is not synchronized, so all the calls must come from one thread. That holds
 * when the delivery runs in the test thread, as the system events are then posted from it too.
 */
class HoldingExecutor : Executor {

    private val held = ArrayDeque<Runnable>()
    private var holding = false

    /**
     * The number of tasks held.
     */
    val heldCount: Int
        get() = held.size

    /**
     * Holds each task to come, until released.
     */
    fun hold() {
        holding = true
    }

    /**
     * Runs the first of the held tasks.
     *
     * While holding, the tasks it adds are held too.
     */
    fun releaseFirst() {
        held.removeFirst().run()
    }

    /**
     * Runs the given action with holding switched off, so each task it adds runs
     * in the calling thread at once.
     */
    fun withoutHolding(action: () -> Unit) {
        val wasHolding = holding
        holding = false
        try {
            action()
        } finally {
            holding = wasHolding
        }
    }

    /**
     * Runs the held tasks, and each task to come, in the calling thread.
     */
    fun release() {
        holding = false
        while (held.isNotEmpty()) {
            held.removeFirst().run()
        }
    }

    override fun execute(task: Runnable) {
        if (holding) {
            held.addLast(task)
        } else {
            task.run()
        }
    }
}
