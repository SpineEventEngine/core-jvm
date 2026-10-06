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

package io.spine.server.entity

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.spine.server.entity.given.concurrency.Gate
import io.spine.server.entity.given.concurrency.Worker
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests [KeyLocks] by holding one thread inside an action at a [Gate], and then
 * checking what another thread is able to do meanwhile.
 */
@DisplayName("`KeyLocks` should")
internal class KeyLocksSpec {

    private val locks = KeyLocks<String>()

    @Test
    fun `return the result of an action`() {
        locks.evaluate(A) { "result" } shouldBe "result"
    }

    @Test
    fun `run actions on different keys in parallel`() {
        val gate = Gate()
        val holding = Worker { locks.run(A) { gate.pass() } }
        gate.awaitReached(by = holding)

        Worker { locks.evaluate(B) { "done" } }.result() shouldBe "done"

        holding.isDone shouldBe false
        gate.open()
        holding.result()
    }

    @Test
    fun `run actions on the same key one at a time`() {
        val gate = Gate()
        val started = AtomicInteger()
        val first = Worker {
            locks.run(A) {
                started.incrementAndGet()
                gate.pass()
            }
        }
        gate.awaitReached(by = first)

        val second = Worker { locks.run(A) { started.incrementAndGet() } }
        second.awaitBlocked()

        started.get() shouldBe 1
        gate.open()
        first.result()
        second.result()
        started.get() shouldBe 2
    }

    /**
     * Runs in a [Worker], so that a deadlock fails the test instead of hanging it.
     */
    @Test
    fun `let an action lock its key again`() {
        val result = Worker {
            locks.evaluate(A) { locks.evaluate(A) { "inner" } }
        }.result()

        result shouldBe "inner"
        locks.size() shouldBe 0
    }

    /**
     * A lock must not be disposed when its holder leaves while another thread awaits it.
     * Otherwise, a thread arriving later gets a new lock for the same key, and runs
     * side by side with the thread that has been waiting.
     */
    @Test
    fun `keep a lock awaited by another thread`() {
        val firstGate = Gate()
        val first = Worker { locks.run(A) { firstGate.pass() } }
        firstGate.awaitReached(by = first)
        val secondGate = Gate()
        val second = Worker { locks.run(A) { secondGate.pass() } }
        second.awaitBlocked()

        // The first thread leaves, and the second one takes over the lock.
        firstGate.open()
        first.result()
        secondGate.awaitReached(by = second)

        // The third thread must wait for the second one.
        val thirdStarted = AtomicBoolean(false)
        val third = Worker { locks.run(A) { thirdStarted.set(true) } }
        third.awaitBlocked()

        thirdStarted.get() shouldBe false
        locks.size() shouldBe 1
        secondGate.open()
        second.result()
        third.result()
        thirdStarted.get() shouldBe true
        locks.size() shouldBe 0
    }

    @Test
    fun `keep a lock only while it is in use`() {
        locks.size() shouldBe 0

        locks.run(A) { locks.size() shouldBe 1 }

        locks.size() shouldBe 0
    }

    @Test
    fun `release the lock of a failed action`() {
        val failure = IllegalStateException("The action has failed.")

        val thrownByRun = shouldThrow<IllegalStateException> {
            locks.run(A) { throw failure }
        }
        val thrownByEvaluate = shouldThrow<IllegalStateException> {
            locks.evaluate<Unit>(A) { throw failure }
        }

        thrownByRun shouldBeSameInstanceAs failure
        thrownByEvaluate shouldBeSameInstanceAs failure
        locks.size() shouldBe 0
        // Another thread can take the lock, so the failed actions have released it.
        Worker { locks.evaluate(A) { "free" } }.result() shouldBe "free"
    }

    /**
     * Unlike the cases above, which arrange particular interleavings, this one lets
     * the threads race freely.
     *
     * The counters are not thread-safe on purpose: an increment can be lost when
     * two actions on the same key overlap.
     */
    @Test
    fun `keep the actions on a key apart when many threads compete`() {
        val keys = listOf(A, B, C)
        val counters = keys.associateWith { IntArray(1) }
        val workers = List(THREADS) {
            Worker {
                repeat(ROUNDS) { round ->
                    val key = keys[round % keys.size]
                    locks.run(key) { counters.getValue(key)[0]++ }
                }
            }
        }

        workers.forEach { it.result() }

        counters.values.sumOf { it[0] } shouldBe THREADS * ROUNDS
        locks.size() shouldBe 0
    }

    private companion object {
        const val A = "A"
        const val B = "B"
        const val C = "C"
        const val THREADS = 8
        const val ROUNDS = 10_000
    }
}
