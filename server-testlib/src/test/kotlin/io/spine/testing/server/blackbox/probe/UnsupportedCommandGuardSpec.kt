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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.spine.core.CommandId
import io.spine.system.server.event.unsupportedCommandReceived
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
    fun `fail the test and remember the type of the unsupported command`() {
        val event = unsupportedCommandReceived {
            id = CommandId.generate()
            typeUrl = "type.spine.io/spine.test.SomeCommand"
        }

        val failure = shouldThrow<AssertionError> {
            guard.on(event)
        }

        failure.message shouldContain "spine.test.SomeCommand"
        guard.commandType() shouldBe "spine.test.SomeCommand"
    }

    @Test
    fun `remember nothing before an unsupported command is received`() {
        guard.commandType() shouldBe null
    }
}
