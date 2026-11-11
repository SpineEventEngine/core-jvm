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

package io.spine.server.delivery

import io.kotest.matchers.string.shouldContain
import io.spine.base.Identifier
import io.spine.server.BoundedContext
import io.spine.server.BoundedContextBuilder.assumingTests
import io.spine.system.server.SystemBoundedContexts.systemOf
import io.spine.test.delivery.DTask
import io.spine.type.TypeUrl
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

@DisplayName("`CatchUpProcess` should")
internal class CatchUpProcessSpec {

    private lateinit var context: BoundedContext

    @BeforeEach
    fun createContext() {
        context = assumingTests("CatchUpProcessSpec").build()
    }

    @AfterEach
    fun closeContext() {
        context.close()
    }

    @Nested
    @DisplayName("take the domain context whose projections it catches up")
    inner class DomainContext {

        @Test
        fun `accepting a domain context`() {
            assertDoesNotThrow {
                process().injectDomainContext(context)
            }
        }

        @Test
        fun `refusing a System context`() {
            val system = systemOf(context)

            val error = assertThrows<IllegalArgumentException> {
                process().injectDomainContext(system)
            }

            error.message shouldContain "`$system`"
            error.message shouldContain "`$context`"
        }
    }

    private fun process(): CatchUpProcess = CatchUpProcess(catchUpId {
        uuid = Identifier.newUuid()
        projectionType = TypeUrl.of(DTask::class.java).value()
    })
}
