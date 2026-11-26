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

package io.spine.client

import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.spine.test.client.ClProjectCreated
import io.spine.test.client.TestEntity
import io.spine.test.commands.CmdCreateProject
import io.spine.testing.logging.mute.MuteLogging
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit.SECONDS
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@MuteLogging
@DisplayName("A client request should pass the error of the stream of updates to its handler")
internal class RequestStreamingErrorSpec {

    private val server = StubSubscriptionServer { updates ->
        updates.onError(Status.UNAVAILABLE.asRuntimeException())
    }
    private val streamingError = CompletableFuture<Throwable>()

    @AfterEach
    fun stopServer() = server.close()

    @Test
    fun `when subscribing to several targets`() {
        server.client.asGuest().subscribe {
            event<ClProjectCreated> { it.observe { } }
            state<TestEntity> { it.observe { } }
            onStreamingError { streamingError.complete(it) }
        }

        streamingError.shouldBeUnavailable()
    }

    @Test
    fun `when observing the outcome of a command, cancelling it if the command is not sent`() {
        val command = CmdCreateProject.newBuilder()
            .setId("streaming-error-spec")
            .build()

        val failure = shouldThrow<StatusRuntimeException> {
            server.client.asGuest()
                .command(command)
                .observe(ClProjectCreated::class.java) { }
                .onStreamingError { streamingError.complete(it) }
                .post()
        }

        failure.status.code shouldBe Status.Code.UNIMPLEMENTED
        streamingError.shouldBeUnavailable()
        server.client.subscriptions().isEmpty shouldBe true
    }

    private fun CompletableFuture<Throwable>.shouldBeUnavailable() {
        val error = get(TIMEOUT, SECONDS).shouldBeInstanceOf<StatusRuntimeException>()
        error.status.code shouldBe Status.Code.UNAVAILABLE
    }

    private companion object {

        /**
         * The number of seconds to wait for the error of the stream.
         */
        const val TIMEOUT = 5L
    }
}
