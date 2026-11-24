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

package io.spine.server.async.given

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.spine.core.TenantId
import io.spine.server.tenant.TenantFunction
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger

/**
 * A stub of an external service called by the handlers of the Shipping context.
 *
 * A [responding] service returns from a call at once. A [held] service keeps each call,
 * and so the handler that makes it, until the test [releases][release] the calls.
 * This way a test decides when a handler finishes, without sleeping.
 *
 * The service remembers the thread and the tenant of each call.
 */
internal class RemoteService private constructor(held: Boolean) {

    /**
     * Gets a permit for each call that has started.
     */
    private val started = Semaphore(0)

    /**
     * Opens when the calls can return.
     */
    private val released = CountDownLatch(if (held) 1 else 0)

    private val calls = CopyOnWriteArrayList<Call>()

    /**
     * The number of calls that have started and have not returned yet.
     */
    private val ongoing = AtomicInteger()

    /**
     * The largest value [ongoing] has had.
     */
    private val mostAtOnce = AtomicInteger()

    /**
     * Creates the failure that each call throws instead of returning.
     * If `null`, the calls return.
     */
    @Volatile
    private var failure: (() -> Throwable)? = null

    /**
     * Makes a call to the service.
     *
     * A handler method calls this method the way it would call a real service: the method
     * returns when the service has "responded".
     */
    fun call() {
        calls.add(Call(Thread.currentThread(), currentTenant()))
        mostAtOnce.accumulateAndGet(ongoing.incrementAndGet(), ::maxOf)
        started.release()
        try {
            check(released.await(waitSeconds, SECONDS)) {
                "The call to the service was not released in $waitSeconds seconds."
            }
            failure?.let { throw it() }
        } finally {
            ongoing.decrementAndGet()
        }
    }

    /**
     * Makes each call throw what the given function creates.
     */
    fun failWith(failure: () -> Throwable) {
        this.failure = failure
    }

    /**
     * Waits until the given number of calls, not awaited before, have started.
     */
    fun awaitCalls(count: Int = 1) {
        withClue("$count call(s) of the service should have started") {
            started.tryAcquire(count, waitSeconds, SECONDS) shouldBe true
        }
    }

    /**
     * Tells if a call has started which was not [awaited][awaitCalls] yet.
     */
    fun hasNewCall(): Boolean = started.availablePermits() > 0

    /**
     * Lets the calls return: both those that have started and the future ones.
     */
    fun release() {
        released.countDown()
    }

    /**
     * Obtains the largest number of calls the service has had at once.
     */
    fun mostCallsAtOnce(): Int = mostAtOnce.get()

    /**
     * Obtains the calls made so far, in the order they started.
     */
    fun calls(): List<Call> = calls.toList()

    /**
     * A call to the service.
     *
     * @property thread The thread that made the call.
     * @property tenant The tenant set for the thread, or `null` if none was set.
     */
    data class Call(val thread: Thread, val tenant: TenantId?)

    companion object {

        /**
         * How many seconds a test waits for something that happens on another thread
         * before it fails.
         */
        const val waitSeconds = 10L

        /**
         * Creates a service that returns from each call at once.
         */
        fun responding(): RemoteService = RemoteService(held = false)

        /**
         * Creates a service that keeps each call until [release] is called.
         */
        fun held(): RemoteService = RemoteService(held = true)
    }
}

/**
 * Obtains the tenant set for the current thread, or `null` if there is none.
 */
private fun currentTenant(): TenantId? =
    try {
        // The function takes the tenant of the thread when it is created,
        // and fails if the thread has none.
        val function = object : TenantFunction<TenantId>(/* multiTenant = */ true) {
            override fun apply(tenant: TenantId): TenantId = tenant
        }
        function.execute()
    } catch (@Suppress("SwallowedException") e: IllegalStateException) {
        null
    }
