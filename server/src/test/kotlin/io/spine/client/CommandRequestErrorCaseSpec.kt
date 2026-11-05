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

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.spine.base.Error
import io.spine.core.Ack
import io.spine.core.Command
import io.spine.core.ErrorCase.INVALID_REQUEST
import io.spine.core.ack
import io.spine.core.status
import io.spine.core.toError
import io.spine.protobuf.pack
import io.spine.server.BoundedContextBuilder
import io.spine.server.commandbus.CommandFilter
import io.spine.server.type.CommandEnvelope
import io.spine.test.client.ClientTestContext
import io.spine.test.client.users.command.LogInUser
import io.spine.test.client.users.event.UserLoggedIn
import io.spine.testing.core.given.GivenUserId
import java.util.Optional
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`CommandRequest` answered with an error case should")
internal class CommandRequestErrorCaseSpec : AbstractClientTest() {

    override fun contexts(): List<BoundedContextBuilder> =
        listOf(ClientTestContext.users().addCommandFilter(AnsweringInvalidRequest))

    @Test
    fun `pass an error that tells only the error case to the server error handler`() {
        var postedMessage: Any? = null
        var receivedError: Error? = null

        client().asGuest()
            .command(logInUser())
            .onServerError { message, error ->
                postedMessage = message
                receivedError = error
            }
            .postAndForget()

        postedMessage.shouldBeInstanceOf<Command>()
        receivedError shouldBe INVALID_REQUEST.toError()
    }

    @Test
    fun `cancel the subscriptions made for the command`() {
        val subscriptions = client().asGuest()
            .command(logInUser())
            .observe(UserLoggedIn::class.java) { }
            .onServerError { _, _ -> }
            .post()

        subscriptions.shouldBeEmpty()
        client().subscriptions().isEmpty shouldBe true
    }

    private fun logInUser(): LogInUser =
        LogInUser.newBuilder()
            .setUser(GivenUserId.generated())
            .build()
}

/**
 * Answers every command with the [INVALID_REQUEST] error case.
 */
private object AnsweringInvalidRequest : CommandFilter {

    override fun filter(envelope: CommandEnvelope): Optional<Ack> {
        val id = envelope.id()
        val answerStatus = status { errorCase = INVALID_REQUEST }
        val answer = ack {
            messageId = id.pack()
            status = answerStatus
        }
        return Optional.of(answer)
    }
}
