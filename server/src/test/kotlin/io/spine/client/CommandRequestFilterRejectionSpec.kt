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

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.spine.core.Ack
import io.spine.server.BoundedContextBuilder
import io.spine.server.commandbus.CommandFilter
import io.spine.server.type.CommandEnvelope
import io.spine.test.client.ClientTestContext
import io.spine.test.client.users.command.LogInUser
import io.spine.test.client.users.command.logInUser
import io.spine.test.client.users.rejection.Rejections
import io.spine.test.client.users.rejection.UserAlreadyLoggedIn
import io.spine.testing.core.given.GivenUserId
import io.spine.testing.logging.mute.MuteLogging
import java.util.Optional
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@MuteLogging
@DisplayName("`CommandRequest` should")
internal class CommandRequestFilterRejectionSpec : AbstractClientTest() {

    override fun contexts(): List<BoundedContextBuilder> = listOf(
        ClientTestContext.users().addCommandFilter(RejectingFilter)
    )

    @Test
    fun `deliver a rejection of a filter once, even if the subscription is not active yet`() {
        val user = GivenUserId.generated()
        val rejections = CopyOnWriteArrayList<Rejections.UserAlreadyLoggedIn>()

        client().asGuest()
            .command(logInUser { this.user = user })
            .observe(Rejections.UserAlreadyLoggedIn::class.java) { rejections += it }
            .post()

        rejections shouldHaveSize 1
        rejections[0].user shouldBe user
    }

    /**
     * Rejects every command to log in a user.
     */
    private object RejectingFilter : CommandFilter {

        override fun filter(envelope: CommandEnvelope): Optional<Ack> {
            val command = envelope.message() as? LogInUser ?: return letPass()
            val rejection = UserAlreadyLoggedIn.newBuilder()
                .setUser(command.user)
                .build()
            return reject(envelope, rejection)
        }
    }
}
