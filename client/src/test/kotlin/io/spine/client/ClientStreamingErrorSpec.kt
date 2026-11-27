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
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.spine.test.client.ClProjectCreated
import io.spine.test.client.TestEntity
import io.spine.testing.logging.mute.MuteLogging
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit.SECONDS
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Checks that the streaming error handler set only on the builder of a [Client] reaches
 * the subscriptions that the single-type requests make.
 */
@MuteLogging
@DisplayName("The streaming error handler of `Client` should receive the stream error, once, of")
internal class ClientStreamingErrorSpec {

    private val errors = CopyOnWriteArrayList<Throwable>()
    private val firstError = CompletableFuture<Throwable>()
    private val server = StubSubscriptionServer(
        configureClient = {
            onStreamingError { error ->
                errors += error
                firstError.complete(error)
            }
        }
    ) { updates ->
        updates.onError(Status.UNAVAILABLE.asRuntimeException())
    }

    @AfterEach
    fun stopServer() = server.close()

    @Test
    fun `a subscription to entity states`() {
        server.client.asGuest()
            .subscribeTo(TestEntity::class.java)
            .observe { }
            .post()

        shouldBeReportedOnce()
    }

    @Test
    fun `a subscription to events`() {
        server.client.asGuest()
            .subscribeToEvent(ClProjectCreated::class.java)
            .observe { }
            .post()

        shouldBeReportedOnce()
    }

    private fun shouldBeReportedOnce() {
        val error = firstError.get(TIMEOUT, SECONDS).shouldBeInstanceOf<StatusRuntimeException>()
        error.status.code shouldBe Status.Code.UNAVAILABLE
        errors shouldHaveSize 1
    }

    private companion object {

        /**
         * The number of seconds to wait for the error of the stream.
         */
        const val TIMEOUT = 5L
    }
}
