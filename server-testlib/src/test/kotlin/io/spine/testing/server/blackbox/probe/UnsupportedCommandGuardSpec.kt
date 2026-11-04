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

package io.spine.testing.server.blackbox.probe

import io.kotest.matchers.shouldBe
import io.spine.core.MessageId
import io.spine.system.server.UnsupportedRequestReceived
import io.spine.system.server.UnsupportedRequestReceived.Reason
import io.spine.system.server.UnsupportedRequestReceived.Reason.INTERNAL_TYPE
import io.spine.system.server.UnsupportedRequestReceived.Reason.NO_CONTEXT
import io.spine.system.server.UnsupportedRequestReceived.Reason.NO_RECEIVER
import io.spine.system.server.unsupportedRequestReceived
import io.spine.validation.NonValidated
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`UnsupportedCommandGuard` should")
internal class UnsupportedCommandGuardSpec {

    private lateinit var guard: UnsupportedCommandGuard

    @BeforeEach
    fun initGuard() {
        guard = UnsupportedCommandGuard(javaClass.name)
    }

    @Test
    fun `check and remember the type of a command which has no handler`() {
        val result = guard.checkAndRemember(unsupported(NO_RECEIVER))

        result shouldBe true
        guard.commandType() shouldBe COMMAND_TYPE
    }

    @Test
    fun `return 'false' and ignore a command which no context serves`() {
        val result = guard.checkAndRemember(unsupported(NO_CONTEXT))

        result shouldBe false
        guard.commandType() shouldBe null
    }

    @Test
    fun `return 'false' and ignore a command of an internal type`() {
        val result = guard.checkAndRemember(unsupported(INTERNAL_TYPE))

        result shouldBe false
        guard.commandType() shouldBe null
    }

    private companion object {

        const val COMMAND_TYPE = "spine.test.SomeCommand"

        fun unsupported(reason: Reason): UnsupportedRequestReceived =
            unsupportedRequestReceived {
                request = commandWithoutId()
                this.reason = reason
            }

        /**
         * Creates the ID of a command without the ID value, which the guard does not read.
         */
        private fun commandWithoutId(): @NonValidated MessageId = MessageId.newBuilder()
            .setTypeUrl("type.spine.io/$COMMAND_TYPE")
            .buildPartial()
    }
}
