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

package io.spine.server.bus

import io.kotest.matchers.shouldBe
import io.spine.base.Identifier.newUuid
import io.spine.core.Ack
import io.spine.core.Command
import io.spine.core.CommandId
import io.spine.core.ErrorCase
import io.spine.core.Status.StatusCase
import io.spine.protobuf.AnyPacker
import io.spine.server.event.reject
import io.spine.test.bus.command.ShareCannotBeTraded
import io.spine.test.bus.shareId
import io.spine.testing.client.TestActorRequestFactory
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`MessageIdExtensions` should")
internal class MessageIdExtensionsSpec {

    @Test
    fun `acknowledge() with OK status`() {
        val ack = ID.acknowledge()
        assertIdEquals(ack)
        assertStatusCase(ack, StatusCase.OK)
    }

    @Test
    fun `reject() with ERROR_CASE status`() {
        val ack = ID.causedError(ErrorCase.INVALID_REQUEST)
        assertIdEquals(ack)
        assertStatusCase(ack, StatusCase.ERROR_CASE)
        ack.status.errorCase shouldBe ErrorCase.INVALID_REQUEST
    }

    @Test
    @Suppress("DEPRECATION") // Reason: covers the carrier of a filter error until its removal.
    fun `reject() with ERROR status`() {
        val error = io.spine.base.error {
            type = MessageIdExtensionsSpec::class.java.canonicalName
            message = "A test error."
        }
        val ack = ID.causedError(error)
        assertIdEquals(ack)
        assertStatusCase(ack, StatusCase.ERROR)
    }

    @Test
    fun `reject() a command with REJECTION status`() {
        val shareId = shareId { value = newUuid() }
        val rejection = ShareCannotBeTraded.newBuilder()
            .setShare(shareId)
            .setReason("Ack factory test.")
            .build()
        val command = generateCommand()
        val re = reject(command, rejection)
        val ack = ID.reject(re)
        assertIdEquals(ack)
        assertStatusCase(ack, StatusCase.REJECTION)
    }

    private fun generateCommand(): Command {
        val requestFactory = TestActorRequestFactory(javaClass)
        val command = requestFactory.generateCommand()
        return command
    }

    companion object {

        val ID: CommandId = CommandId.generate()

        private fun assertIdEquals(ack: Ack) {
            val id = AnyPacker.unpack(ack.messageId)
            id shouldBe ID
        }

        private fun assertStatusCase(ack: Ack, status: StatusCase) {
            ack.status.statusCase shouldBe status
        }
    }
}
