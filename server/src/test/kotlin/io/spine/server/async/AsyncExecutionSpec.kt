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
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.spine.server.async.given.AcmeRpcHandler
import io.spine.server.async.given.newOrder
import io.spine.server.given.context.shipping.command.reserveShipment
import io.spine.server.type.CommandEnvelope
import io.spine.testing.client.TestActorRequestFactory
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("`AsyncExecution` should")
internal class AsyncExecutionSpec {

    @Nested inner class
    `have a builder which` {

        @Test
        fun `refuses a limit that is not positive`() {
            val builder = AsyncExecution.newBuilder()

            shouldThrow<IllegalArgumentException> { builder.setLimit(0) }
            shouldThrow<IllegalArgumentException> {
                builder.setLimit(AcmeRpcHandler::class.java, -1)
            }
        }

        @Test
        fun `refuses a negative close timeout`() {
            val builder = AsyncExecution.newBuilder()

            shouldThrow<IllegalArgumentException> {
                builder.setCloseTimeout(Durations.fromSeconds(-1))
            }
        }

        @Test
        fun `accepts a zero close timeout`() {
            AsyncExecution.newBuilder()
                .setCloseTimeout(Durations.ZERO)
                .build()
                .close()
        }

        @Test
        fun `accepts the longest close timeout`() {
            shouldNotThrowAny {
                AsyncExecution.newBuilder()
                    .setCloseTimeout(Durations.MAX_VALUE)
                    .build()
                    .close()
            }
        }
    }

    @Nested inner class
    `be built by the 'asyncExecution' function, whose block` {

        @Test
        fun `starts with the default values`() {
            asyncExecution {
                limit shouldBe 8
                closeTimeout shouldBe Durations.fromSeconds(30)
            }.close()
        }

        @Test
        fun `keeps the assigned values`() {
            asyncExecution {
                limit = 16
                closeTimeout = Durations.fromSeconds(10)

                limit shouldBe 16
                closeTimeout shouldBe Durations.fromSeconds(10)
            }.close()
        }

        @Test
        fun `may be empty`() {
            val execution = asyncExecution { }

            execution.shouldBeInstanceOf<LimitingExecution>()
            execution.close()
        }

        @Test
        fun `cannot assign a limit that is not positive`() {
            shouldThrow<IllegalArgumentException> {
                asyncExecution { limit = 0 }
            }
            shouldThrow<IllegalArgumentException> {
                asyncExecution { limit<AcmeRpcHandler>(-1) }
            }
        }

        @Test
        fun `cannot assign a negative close timeout`() {
            shouldThrow<IllegalArgumentException> {
                asyncExecution { closeTimeout = Durations.fromSeconds(-1) }
            }
        }
    }

    @Nested inner class
    `provide the execution on the calling thread which` {

        private val execution = AsyncExecution.onCallingThread()

        @Test
        fun `runs an invocation before the submission returns`() {
            var thread: Thread? = null
            val invocation = invocation { thread = Thread.currentThread() }

            execution.submit(invocation)

            thread shouldBe Thread.currentThread()
        }

        @Test
        fun `is one instance`() {
            AsyncExecution.onCallingThread() shouldBeSameInstanceAs execution
        }
    }

    @Test
    fun `need nothing to close by default`() {
        val custom = AsyncExecution { it.run() }

        custom.close()
    }

    private fun invocation(handling: () -> Unit): AsyncInvocation {
        val command = TestActorRequestFactory(javaClass)
            .createCommand(reserveShipment { order = newOrder() })
        return AsyncInvocation(
            handlerClass = AcmeRpcHandler::class.java,
            envelope = CommandEnvelope.of(command),
            handling = handling
        )
    }
}
