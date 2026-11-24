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

package io.spine.server.async

import com.google.protobuf.Duration
import com.google.protobuf.util.Durations
import io.spine.logging.WithLogging
import io.spine.server.BoundedContext
import io.spine.server.ContextAware
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * The execution that runs the invocations on its own threads and limits the number of
 * ongoing invocations per handler class.
 *
 * Each handler class has a [Lane]: the number of its ongoing invocations, and the queue of
 * the invocations that wait for a slot. A slot is taken when an invocation is passed to
 * a thread, and is released when [AsyncInvocation.run] returns. So the time a handler waits
 * for a remote service counts towards the limit.
 *
 * The threads are daemon threads, created on demand and named after the Bounded Context
 * the execution is [registered with][registerWith]. An idle thread ends after a minute.
 * The limits bound the number of threads.
 *
 * @param defaultLimit The limit for a handler class that is not in [limits].
 * @param limits The limits by handler class.
 * @param closeTimeout How long [close] waits for the invocations to finish.
 * @param threads Creates the threads. If `null`, the execution creates daemon threads
 *   named after its context.
 */
internal class LimitingExecution(
    private val defaultLimit: Int,
    private val limits: Map<Class<*>, Int>,
    private val closeTimeout: Duration,
    private val threads: ThreadFactory? = null
) : AsyncExecution, ContextAware, WithLogging {

    /**
     * Guards the state of the execution.
     */
    private val lock = ReentrantLock()

    /**
     * Signalled when an invocation finishes, and when the execution is closed.
     */
    private val idle = lock.newCondition()

    /**
     * The lanes by handler class, created when the first invocation of a class is submitted.
     */
    private val lanes = mutableMapOf<Class<*>, Lane>()

    /**
     * Tells if the execution accepts invocations. Turns `false` when [close] is called.
     */
    private var open = true

    /**
     * Turns `true` when [close] has finished waiting for the invocations.
     * From then on, no invocation is run.
     */
    private var closed = false

    /**
     * The context this execution serves, if it is registered with one.
     */
    private var context: BoundedContext? = null

    /**
     * The threads that run the invocations.
     *
     * They are created under the [lock] when the first invocation is submitted, which is
     * after the execution is registered with its context.
     */
    private val workers = WorkerPool {
        threads ?: WorkerThreads(context?.name()?.value)
    }

    /**
     * The lane of the invocation that the current thread runs, if the thread is one of
     * the [workers] and runs an invocation now.
     */
    private val laneOfThread = ThreadLocal<Lane?>()

    /**
     * Remembers the context, to name the threads after it.
     *
     * Two contexts may have the same name, for example, in two applications of one JVM.
     * So the execution tells its context from another one by the instance.
     *
     * @throws IllegalStateException If the execution already serves another context.
     */
    override fun registerWith(context: BoundedContext): Unit = lock.withLock {
        val served = this.context
        check(served == null || served === context) {
            "The asynchronous execution serves the context `${served?.name()?.value}`," +
                    " so it cannot serve another context, `${context.name().value}`."
        }
        this.context = context
    }

    override fun isRegistered(): Boolean = lock.withLock { context != null }

    /**
     * Puts the invocation into the queue of its handler class, and passes it to a thread
     * at once if the class has a free slot.
     */
    override fun submit(invocation: AsyncInvocation): Unit = lock.withLock {
        check(open) {
            "The asynchronous execution accepts no invocations after its closing" +
                    " has started, so it refuses `$invocation`."
        }
        val handlerClass = invocation.handlerClass()
        val lane = lanes.getOrPut(handlerClass) {
            Lane(limits[handlerClass] ?: defaultLimit)
        }
        lane.waiting.addLast(invocation)
        startWaiting(lane)
    }

    /**
     * Passes the longest-waiting invocations of the lane to threads, one per free slot.
     *
     * If a thread cannot take an invocation, for example, because the thread cannot be
     * created, the invocation is dropped, its slot stays free, and the failure goes to
     * the caller.
     *
     * Called under the [lock].
     */
    private fun startWaiting(lane: Lane) {
        while (lane.ongoing < lane.limit) {
            val next = lane.waiting.removeFirstOrNull() ?: break
            workers.execute { runAndRelease(lane, next) }
            // The slot is taken only now, when a thread has the invocation. The thread
            // cannot release the slot earlier, because it needs the lock for that.
            lane.ongoing++
        }
    }

    /**
     * Runs the invocation, and then releases its slot, whatever the outcome is.
     *
     * An invocation that was passed to a thread, but did not start before the execution
     * gave up waiting for it, is not run. It is already counted as lost.
     */
    private fun runAndRelease(lane: Lane, invocation: AsyncInvocation) {
        try {
            if (lock.withLock { !closed }) {
                laneOfThread.set(lane)
                invocation.run()
            }
        } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception) {
            // The invocation has logged the failure. The thread goes on to serve the others.
        } finally {
            laneOfThread.remove()
            release(lane)
        }
    }

    /**
     * Frees the slot of a finished invocation, and gives it to the invocation that waits
     * the longest in the lane, if any.
     */
    private fun release(lane: Lane): Unit = lock.withLock {
        lane.ongoing--
        try {
            startWaiting(lane)
        } finally {
            // A thread that waits in `close()` checks if it still has something to wait for.
            idle.signalAll()
        }
    }

    /**
     * Tells if `close()` has an invocation to wait for.
     *
     * @param own The lane of the invocation that calls `close()`, or `null` if the method
     *   is not called by an invocation of this execution. That invocation cannot finish
     *   before `close()` returns, and the invocations of its lane that wait for its slot
     *   cannot start. So in this lane only the other ongoing invocations count.
     */
    private fun busy(own: Lane?): Boolean =
        lanes.values.any { lane ->
            if (lane === own) lane.ongoing > 1 else lane.busy()
        }

    /**
     * Stops accepting invocations, and waits for the submitted ones to finish.
     *
     * The execution refuses an invocation from the moment the method is called. This
     * includes an invocation caused by a handler that finishes while the method waits.
     *
     * If the invocations do not finish within the close timeout, the method interrupts
     * the running ones, drops the waiting ones, and logs how many of them were lost.
     *
     * If several threads call the method at once, each of them returns when the execution
     * is closed.
     *
     * A handler run by this execution may call the method, for example, by closing its
     * Bounded Context. The method does not wait for that handler, which cannot finish
     * before the method returns, and does not interrupt it.
     */
    override fun close() {
        val own: Lane? = laneOfThread.get()
        val (lost, contextName) = lock.withLock {
            open = false
            awaitIdle(own)
            if (closed) {
                // Another call has closed the execution, earlier or while this one waited.
                return
            }
            closed = true
            val remaining = dropRemaining(own)
            workers.shutDown(interrupt = remaining.isNotEmpty(), callerIsWorker = own != null)
            // Wakes the other threads that wait in this method.
            idle.signalAll()
            remaining to context?.name()?.value
        }
        if (lost.isNotEmpty()) {
            logger.atWarning().log {
                val ofContext = contextName?.let { " of the context `$it`" } ?: ""
                "The asynchronous execution$ofContext was closed before" +
                        " ${lost.values.sum()} invocation(s) finished. Lost invocations" +
                        " by handler class: ${lost.entries.joinToString { it.printed() }}."
            }
        }
    }

    /**
     * Waits until no invocation is ongoing or waiting, but not longer than the close timeout.
     * Does not wait if the execution is closed.
     *
     * Called under the [lock].
     *
     * @param own The lane of the invocation that calls `close()`, if any.
     */
    private fun awaitIdle(own: Lane?) {
        var remaining = closeTimeout.toNanosOrMax()
        try {
            while (!closed && busy(own) && remaining > 0) {
                remaining = idle.awaitNanos(remaining)
            }
        } catch (@Suppress("SwallowedException") e: InterruptedException) {
            // The closing thread is asked to stop, so it does not wait any longer.
            Thread.currentThread().interrupt()
        }
    }

    /**
     * Empties the queues of the lanes.
     *
     * Called under the [lock].
     *
     * @param own The lane of the invocation that calls `close()`, if any. That invocation
     *   goes on after `close()` returns, so it is not lost.
     * @return The number of ongoing and waiting invocations by handler class, for
     *   the classes that have such invocations.
     */
    private fun dropRemaining(own: Lane?): Map<Class<*>, Int> {
        val lost = lanes
            .mapValues { (_, lane) ->
                val goingOn = if (lane === own) lane.ongoing - 1 else lane.ongoing
                goingOn + lane.waiting.size
            }
            .filterValues { it > 0 }
        lanes.values.forEach { it.waiting.clear() }
        return lost
    }

    /**
     * The invocations of one handler class.
     *
     * @property limit The number of invocations of the class that can be ongoing at once.
     */
    private class Lane(val limit: Int) {

        /**
         * The number of invocations that have a slot: those passed to a thread
         * and not finished yet.
         */
        var ongoing = 0

        /**
         * The invocations that wait for a slot, in the order of submission.
         */
        val waiting = ArrayDeque<AsyncInvocation>()

        /**
         * Tells if the lane has an invocation that is ongoing or waiting.
         */
        fun busy(): Boolean = ongoing > 0 || waiting.isNotEmpty()
    }
}

/**
 * The threads of a [LimitingExecution], created when the first task arrives.
 *
 * @param factory Obtains the factory of the threads when the first task arrives.
 */
private class WorkerPool(factory: () -> ThreadFactory) {

    private val pool: Lazy<ExecutorService> = lazy {
        Executors.newCachedThreadPool(factory())
    }

    /**
     * Passes the task to a thread, creating one if all the threads are busy.
     */
    fun execute(task: Runnable) {
        pool.value.execute(task)
    }

    /**
     * Makes the threads end: after the tasks they run now, or at once.
     *
     * @param interrupt If `true`, the threads are interrupted.
     * @param callerIsWorker Tells if the calling thread is one of these threads.
     *   Such a thread is interrupted with the others, so its interruption status is
     *   put back to what it was before the call.
     */
    fun shutDown(interrupt: Boolean, callerIsWorker: Boolean) {
        if (!pool.isInitialized()) {
            return
        }
        if (!interrupt) {
            pool.value.shutdown()
            return
        }
        val wasInterrupted = Thread.currentThread().isInterrupted
        pool.value.shutdownNow()
        if (callerIsWorker && !wasInterrupted) {
            // Clears the status set by `shutdownNow()`.
            Thread.interrupted()
        }
    }
}

/**
 * Creates the daemon threads of a [LimitingExecution].
 *
 * A thread is named after the context the execution serves, so that a thread dump tells
 * which context waits for an external service.
 *
 * @param contextName The name of the context, or `null` if the execution serves none.
 */
private class WorkerThreads(contextName: String?) : ThreadFactory, WithLogging {

    private val prefix = contextName?.let { "$it-async" } ?: "async"
    private val counter = AtomicInteger()

    override fun newThread(task: Runnable): Thread {
        val thread = Thread(task, "$prefix-${counter.incrementAndGet()}")
        thread.isDaemon = true
        thread.setUncaughtExceptionHandler { failed, error ->
            logger.atError().withCause(error).log {
                "The thread `${failed.name}` of an asynchronous execution has failed."
            }
        }
        return thread
    }
}

/**
 * Converts this duration to nanoseconds.
 *
 * A duration too long to be counted in nanoseconds gives [Long.MAX_VALUE], which
 * a wait takes as "for as long as it takes".
 */
private fun Duration.toNanosOrMax(): Long =
    try {
        Durations.toNanos(this)
    } catch (@Suppress("SwallowedException") e: ArithmeticException) {
        Long.MAX_VALUE
    }

/**
 * Prints the name of the handler class and the number of its lost invocations.
 */
private fun Map.Entry<Class<*>, Int>.printed(): String = "`${key.name}`: $value"
