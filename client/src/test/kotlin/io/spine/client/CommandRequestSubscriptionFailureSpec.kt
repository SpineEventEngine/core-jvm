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
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.spine.core.Ack
import io.spine.core.ErrorCase
import io.spine.core.Responses.statusOk
import io.spine.test.client.ClProjectCreated
import io.spine.test.commands.cmdCreateProject
import io.spine.testing.logging.mute.MuteLogging
import io.spine.validation.NonValidated
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@MuteLogging
@DisplayName("`CommandRequest` should not post the command if the subscription to its outcome")
internal class CommandRequestSubscriptionFailureSpec {

    @Test
    fun `fails without an error case, throwing the error`() {
        val serverErrors = CopyOnWriteArrayList<ErrorCase>()

        StubSubscriptionServer(
            commandAck = okAck(),
            subscriptionError = Status.UNAVAILABLE
        ) { /* No subscription is made. */ }.use { server ->
            val error = shouldThrow<StatusRuntimeException> {
                server.client.asGuest()
                    .command(cmdCreateProject { id = "failed-subscription" })
                    .observe(ClProjectCreated::class.java) { }
                    .onServerError { _, errorCase -> serverErrors += errorCase }
                    .post()
            }

            error.status.code shouldBe Status.Code.UNAVAILABLE
            server.posted.shouldBeEmpty()
            server.client.subscriptions().isEmpty shouldBe true
        }
        serverErrors.shouldBeEmpty()
    }

    private fun okAck(): @NonValidated Ack = Ack.newBuilder().setStatus(statusOk()).buildPartial()
}
