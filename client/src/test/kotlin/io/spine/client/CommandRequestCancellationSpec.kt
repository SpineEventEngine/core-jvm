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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.spine.core.Ack
import io.spine.test.client.ClProjectCreated
import io.spine.test.commands.cmdCreateProject
import io.spine.testing.logging.mute.MuteLogging
import io.spine.validation.NonValidated
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@MuteLogging
@DisplayName("`CommandRequest` should cancel the subscription to the outcome of the command if")
internal class CommandRequestCancellationSpec {

    @Test
    fun `the acknowledgement of the command has no status`() {
        StubSubscriptionServer(commandAck = statuslessAck()) { /* Keep the stream open. */ }
            .use { server ->
                val error = shouldThrow<IllegalStateException> {
                    server.client.asGuest()
                        .command(cmdCreateProject { id = "statusless-ack" })
                        .observe(ClProjectCreated::class.java) { }
                        .post()
                }

                error.message shouldContain "`STATUS_NOT_SET`"
                server.subscribed shouldHaveSize 1
                server.cancelled shouldContainExactly server.subscribed
                server.client.subscriptions().isEmpty shouldBe true
            }
    }

    private fun statuslessAck(): @NonValidated Ack = Ack.newBuilder().buildPartial()
}
