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

package io.spine.server

import io.grpc.stub.StreamObserver
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.concurrent.thread
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`GuardedObserver` should")
internal class GuardedObserverSpec {

    private val recorder = RecordingObserver()
    private val guarded = GuardedObserver(recorder)

    @Test
    fun `pass the values and the completion of the stream`() {
        guarded.onNext("one")
        guarded.onNext("two")
        guarded.onCompleted()

        recorder.calls shouldContainExactly listOf("one", "two", COMPLETED)
    }

    @Test
    fun `pass nothing after an error`() {
        guarded.onNext("one")
        guarded.onError(IllegalStateException("Stub failure"))
        guarded.onNext("two")
        guarded.onError(IllegalStateException("Another failure"))
        guarded.onCompleted()

        recorder.calls shouldContainExactly listOf("one", ERROR)
    }

    @Test
    fun `pass no value after an error sent from another thread`() {
        val start = CountDownLatch(1)
        val delivering = thread {
            start.await()
            repeat(VALUES) { guarded.onNext("value $it") }
        }
        val failing = thread {
            start.await()
            guarded.onError(IllegalStateException("Stub failure"))
        }

        start.countDown()
        delivering.join(SECONDS.toMillis(TIMEOUT))
        failing.join(SECONDS.toMillis(TIMEOUT))

        val calls = recorder.calls
        calls.count { it == ERROR } shouldBe 1
        calls.last() shouldBe ERROR
    }

    private companion object {
        const val ERROR = "error"
        const val COMPLETED = "completed"
        const val VALUES = 10_000
        const val TIMEOUT = 10L
    }

    /**
     * Records the calls it receives, in their order.
     */
    private class RecordingObserver : StreamObserver<String> {

        private val received = mutableListOf<String>()

        val calls: List<String>
            @Synchronized get() = received.toList()

        @Synchronized
        override fun onNext(value: String) {
            received.add(value)
        }

        @Synchronized
        override fun onError(t: Throwable) {
            received.add(ERROR)
        }

        @Synchronized
        override fun onCompleted() {
            received.add(COMPLETED)
        }
    }
}
