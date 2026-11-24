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

import com.google.protobuf.util.Durations
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.spine.server.BoundedContextBuilder.assumingTests
import io.spine.server.async.given.AcmeRpcHandler
import io.spine.server.async.given.ShipmentPlanner
import io.spine.server.async.given.newOrder
import io.spine.server.async.given.RemoteService.Companion.waitSeconds
import io.spine.server.given.context.shipping.command.reserveShipment
import io.spine.server.type.CommandEnvelope
import io.spine.testing.client.TestActorRequestFactory
import io.spine.testing.logging.Interceptor
import io.spine.testing.logging.mute.MuteLogging
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Level
import kotlin.concurrent.thread
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Checks the execution that [AsyncExecution.newBuilder] builds.
 *
 * The tests do not sleep. A [Work] keeps its thread until the test lets it finish, so a test
 * decides which invocations are ongoing at each step.
 */
@DisplayName("The execution built by `AsyncExecution.newBuilder()` should")
internal class LimitingExecutionSpec {

    /**
     * The class of handlers with the limit of two in the [execution].
     */
    private val limitedToTwo = AcmeRpcHandler::class.java

    /**
     * A class of handlers for which the [execution] has only the default limit of one.
     */
    private val limitedByDefault = ShipmentPlanner::class.java

    private val execution: AsyncExecution = AsyncExecution.newBuilder()
        .setLimit(limitedToTwo, 2)
        .setLimit(1)
        .build()

    /**
     * The works submitted by the test, to let them all finish when the test ends.
     */
    private val works = CopyOnWriteArrayList<Work>()

    /**
     * The number of the works of the test that are running now.
     */
    private val ongoing = AtomicInteger()

    @AfterEach
    fun finishWorksAndClose() {
        works.forEach { it.finish() }
        execution.close()
    }

    @Test
    fun `run an invocation on a thread other than the submitting one`() {
        val work = submit(limitedToTwo)

        work.awaitStarted()

        work.thread() shouldNotBe Thread.currentThread()
    }

    @Test
    fun `return from the submission while the invocation is running`() {
        val work = submit(limitedToTwo)

        work.awaitStarted()

        // The test thread got here, so `submit()` has returned, while the work is not over.
        work.isFinished() shouldBe false
    }

    @Nested inner class
    `limit the ongoing invocations of a handler class` {

        @Test
        fun `keeping the next one waiting until a running one finishes`() {
            val first = submit(limitedToTwo)
            val second = submit(limitedToTwo)
            val third = submit(limitedToTwo)
            first.awaitStarted()
            second.awaitStarted()

            third.hasStarted() shouldBe false

            first.finish()
            third.awaitStarted()
        }

        @Test
        fun `never exceeding the limit`() {
            val all = (1..6).map { submit(limitedToTwo) }
            // The first two take both slots before any of them is told to finish.
            all[0].awaitStarted()
            all[1].awaitStarted()

            // Each work finishes only after it is told to, so they overlap as much as
            // the execution lets them.
            all.forEach {
                it.awaitStarted()
                it.finish()
                it.awaitFinished()
            }

            all.maxOf { it.ongoingAtStart() } shouldBe 2
        }

        @Test
        fun `with the default limit if the class has none of its own`() {
            val first = submit(limitedByDefault)
            val second = submit(limitedByDefault)
            first.awaitStarted()

            second.hasStarted() shouldBe false

            first.finish()
            second.awaitStarted()
        }

        @Test
        fun `apart from the invocations of other classes`() {
            val first = submit(limitedByDefault)
            first.awaitStarted()

            val ofAnotherClass = submit(limitedToTwo)

            // The only slot of `limitedByDefault` is taken, and this one still starts.
            ofAnotherClass.awaitStarted()
        }
    }

    @Test
    fun `run the invocations of a class with the limit of one in the order of submission`() {
        val order = CopyOnWriteArrayList<Int>()
        val first = submit(limitedByDefault) { order.add(0) }
        first.awaitStarted()
        // The first one keeps the slot, so the others queue up in the order of submission.
        val others = (1..5).map { number ->
            submit(limitedByDefault) { order.add(number) }
        }

        // The queued works may finish as soon as they start, so only the execution
        // decides the order in which they run.
        others.forEach { it.finish() }
        first.finish()
        others.forEach { it.awaitFinished() }

        order shouldContainExactly listOf(0, 1, 2, 3, 4, 5)
    }

    @Test
    fun `keep the slot free if no thread can take the invocation`() {
        var refusing = true
        val threads = ThreadFactory { task ->
            if (refusing) null else Thread(task).apply { isDaemon = true }
        }
        val shortOfThreads = LimitingExecution(
            defaultLimit = 1,
            limits = mapOf(),
            closeTimeout = Durations.fromSeconds(waitSeconds),
            threads = threads
        )
        shortOfThreads.use {
            shouldThrow<RejectedExecutionException> {
                submit(limitedByDefault, shortOfThreads)
            }
            val refused = works.last()

            // The refused invocation did not keep the only slot of its class.
            refusing = false
            val next = submit(limitedByDefault, shortOfThreads)
            next.awaitStarted()
            next.finish()
            next.awaitFinished()
            refused.hasStarted() shouldBe false
        }
    }

    @Test
    @MuteLogging
    fun `give the slot of a failed invocation to the next one`() {
        val failing = submit(limitedByDefault) { error("The handling fails.") }
        val next = submit(limitedByDefault)

        failing.finish()

        next.awaitStarted()
    }

    @Test
    @MuteLogging
    fun `give the slot of an invocation that throws an error to the next one`() {
        val failing = submit(limitedByDefault) { throw AssertionError("A broken model.") }
        val next = submit(limitedByDefault)

        failing.finish()

        next.awaitStarted()
        // The thread of the failed invocation logs the error and ends, while logging is muted.
        failing.thread().join(SECONDS.toMillis(waitSeconds))
    }

    @Nested inner class
    `when closed` {

        @Test
        fun `wait for the running and the waiting invocations`() {
            val running = submit(limitedByDefault)
            val waiting = submit(limitedByDefault)
            running.awaitStarted()

            val closing = thread { execution.close() }
            closing.awaitWaiting()
            running.isFinished() shouldBe false

            running.finish()
            waiting.awaitStarted()
            waiting.finish()

            closing.join(SECONDS.toMillis(waitSeconds))
            closing.isAlive shouldBe false
            running.isFinished() shouldBe true
            waiting.isFinished() shouldBe true
        }

        @Test
        fun `refuse new invocations while it waits for the submitted ones`() {
            val running = submit(limitedByDefault)
            running.awaitStarted()
            val closing = thread { execution.close() }
            closing.awaitWaiting()

            shouldThrow<IllegalStateException> {
                submit(limitedToTwo)
            }

            running.finish()
            closing.join(SECONDS.toMillis(waitSeconds))
            closing.isAlive shouldBe false
            works.last().hasStarted() shouldBe false
        }

        @Test
        @MuteLogging
        fun `not wait for the invocation that closes it`() {
            val patient = AsyncExecution.newBuilder()
                .setLimit(1)
                .setCloseTimeout(Durations.MAX_VALUE)
                .build()
            val closer = submit(limitedByDefault, patient) { patient.close() }
            // This one waits for the slot of the closing invocation, so it cannot start.
            val behind = submit(limitedByDefault, patient)

            closer.finish()

            closer.awaitFinished()
            behind.hasStarted() shouldBe false
        }

        @Test
        fun `refuse new invocations afterwards`() {
            execution.close()

            shouldThrow<IllegalStateException> {
                submit(limitedToTwo)
            }
        }

        @Test
        fun `do nothing when closed again`() {
            execution.close()

            shouldNotThrowAny {
                execution.close()
            }
        }

        @Test
        fun `let each of the threads that close it return`() {
            val running = submit(limitedByDefault)
            running.awaitStarted()
            val closing = (1..2).map { thread { execution.close() } }
            closing.forEach { it.awaitWaiting() }

            running.finish()

            closing.forEach {
                it.join(SECONDS.toMillis(waitSeconds))
                it.isAlive shouldBe false
            }
        }
    }

    @Nested inner class
    `if the invocations do not finish within the close timeout` {

        private val impatient = AsyncExecution.newBuilder()
            .setLimit(1)
            .setCloseTimeout(Durations.fromMillis(1))
            .build()

        private val interceptor = Interceptor(LimitingExecution::class.java, Level.WARNING)

        @AfterEach
        fun releaseInterceptor() {
            interceptor.release()
        }

        @Test
        @MuteLogging
        fun `interrupt the running one and drop the waiting one`() {
            val running = submit(limitedByDefault, impatient)
            val waiting = submit(limitedByDefault, impatient)
            running.awaitStarted()

            impatient.close()

            running.awaitFinished()
            running.wasInterrupted() shouldBe true
            waiting.hasStarted() shouldBe false
        }

        @Test
        @MuteLogging
        fun `not run an invocation that has not started when the wait is given up`() {
            val mayStart = CountDownLatch(1)
            val threads = CopyOnWriteArrayList<Thread>()
            // A thread that gets the invocation, but starts it only when the test says so.
            val lateThreads = ThreadFactory { task ->
                Thread {
                    mayStart.await()
                    task.run()
                }.apply { isDaemon = true }.also { threads.add(it) }
            }
            val execution = LimitingExecution(
                defaultLimit = 1,
                limits = mapOf(),
                closeTimeout = Durations.ZERO,
                threads = lateThreads
            )
            val late = submit(limitedByDefault, execution)

            execution.close()
            mayStart.countDown()
            threads.single().join(SECONDS.toMillis(waitSeconds))

            late.hasStarted() shouldBe false
        }

        @Test
        fun `spare the invocation that closes the execution`() {
            val other = submit(limitedToTwo, impatient)
            other.awaitStarted()
            var interruptedAfterClosing = true
            val closer = submit(limitedByDefault, impatient) {
                impatient.close()
                interruptedAfterClosing = Thread.currentThread().isInterrupted
            }
            interceptor.intercept()

            closer.finish()
            closer.awaitFinished()

            // Only the other invocation is interrupted and counted as lost.
            interruptedAfterClosing shouldBe false
            other.awaitFinished()
            other.wasInterrupted() shouldBe true
            interceptor.assertLog().record()
                .hasMessageThat()
                .contains("1 invocation(s)")
        }

        @Test
        fun `log how many invocations of each handler class were lost`() {
            val running = submit(limitedByDefault, impatient)
            submit(limitedByDefault, impatient)
            running.awaitStarted()
            interceptor.intercept()

            impatient.close()

            interceptor.assertLog().record().run {
                hasLevelThat().isEqualTo(Level.WARNING)
                hasMessageThat().contains("2 invocation(s)")
                hasMessageThat().contains("`${limitedByDefault.name}`: 2")
            }
        }
    }

    @Nested inner class
    `given to a Bounded Context` {

        @Test
        fun `name its threads after the context`() {
            assumingTests("Shipping")
                .setAsyncExecution(execution)
                .build().use {
                    val work = submit(limitedToTwo)
                    work.awaitStarted()

                    // Otherwise, closing the context would wait for the work.
                    work.finish()

                    work.thread().name shouldStartWith "Shipping-async-"
                }
        }

        @Test
        fun `refuse to serve another context`() {
            assumingTests("Shipping")
                .setAsyncExecution(execution)
                .build().use {
                    val failure = shouldThrow<IllegalStateException> {
                        assumingTests("Billing")
                            .setAsyncExecution(execution)
                            .build()
                    }
                    failure.message shouldContain "`Shipping`"
                    failure.message shouldContain "`Billing`"
                }
        }

        @Test
        fun `refuse to serve another context with the same name`() {
            assumingTests("Shipping")
                .setAsyncExecution(execution)
                .build().use {
                    shouldThrow<IllegalStateException> {
                        assumingTests("Shipping")
                            .setAsyncExecution(execution)
                            .build()
                    }
                }
        }
    }

    /**
     * Submits a work on behalf of a handler of the given class.
     */
    private fun submit(
        handlerClass: Class<*>,
        to: AsyncExecution = execution,
        action: () -> Unit = {}
    ): Work {
        val work = Work(ongoing, action)
        works.add(work)
        to.submit(
            AsyncInvocation(
                handlerClass = handlerClass,
                envelope = command(),
                handling = work::run
            )
        )
        return work
    }

    private fun command(): CommandEnvelope {
        val command = TestActorRequestFactory(javaClass)
            .createCommand(reserveShipment { order = newOrder() })
        return CommandEnvelope.of(command)
    }
}

/**
 * Waits until this thread waits for something with a timeout, as a thread that closes
 * an execution does while invocations are ongoing.
 */
private fun Thread.awaitWaiting() {
    val deadline = System.nanoTime() + SECONDS.toNanos(waitSeconds)
    while (state != Thread.State.TIMED_WAITING) {
        check(System.nanoTime() < deadline) {
            "The thread `$name` did not start waiting in $waitSeconds seconds."
        }
        Thread.onSpinWait()
    }
}

/**
 * A stub of the work of a handler, which keeps its thread until the test tells it to finish.
 *
 * @param ongoing Counts the works of the test that are running.
 * @param action What the work does before it finishes.
 */
private class Work(
    private val ongoing: AtomicInteger,
    private val action: () -> Unit
) {

    private val started = CountDownLatch(1)
    private val mayFinish = CountDownLatch(1)
    private val finished = CountDownLatch(1)

    @Volatile
    private var thread: Thread? = null

    @Volatile
    private var interrupted = false

    @Volatile
    private var ongoingAtStart = 0

    fun run() {
        thread = Thread.currentThread()
        ongoingAtStart = ongoing.incrementAndGet()
        started.countDown()
        try {
            mayFinish.await()
            action()
        } catch (@Suppress("SwallowedException") e: InterruptedException) {
            interrupted = true
        } finally {
            ongoing.decrementAndGet()
            finished.countDown()
        }
    }

    fun awaitStarted() = await(started, "start")

    fun awaitFinished() = await(finished, "finish")

    fun hasStarted(): Boolean = started.count == 0L

    fun isFinished(): Boolean = finished.count == 0L

    fun wasInterrupted(): Boolean = interrupted

    /**
     * Tells how many works of the test, this one included, were running when this one started.
     */
    fun ongoingAtStart(): Int = ongoingAtStart

    fun thread(): Thread = checkNotNull(thread) { "The work has not started." }

    fun finish() {
        mayFinish.countDown()
    }

    private fun await(latch: CountDownLatch, what: String) {
        withClue("The work should $what in $waitSeconds seconds") {
            latch.await(waitSeconds, SECONDS) shouldBe true
        }
    }
}
