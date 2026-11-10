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
import io.spine.core.Ack
import io.spine.core.CommandId
import io.spine.core.ErrorCase
import io.spine.core.Status.StatusCase
import io.spine.protobuf.AnyPacker
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
    fun `causedError() with ERROR_CASE status`() {
        val ack = ID.causedError(ErrorCase.INVALID_REQUEST)
        assertIdEquals(ack)
        assertStatusCase(ack, StatusCase.ERROR_CASE)
        ack.status.errorCase shouldBe ErrorCase.INVALID_REQUEST
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
