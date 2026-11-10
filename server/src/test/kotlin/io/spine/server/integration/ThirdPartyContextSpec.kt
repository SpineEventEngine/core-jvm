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

package io.spine.server.integration

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.string.shouldContain
import io.spine.server.BoundedContextBuilder
import io.spine.testing.server.testApplication
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Checks closing a [ThirdPartyContext], and giving it the context that its application built.
 *
 * Supplements `ThirdPartyContextTest`, which checks emitting events.
 */
@DisplayName("`ThirdPartyContext` should")
internal class ThirdPartyContextSpec {

    @Test
    fun `do nothing when closed before its application is built`() {
        val calendar = ThirdPartyContext.singleTenant("Calendar")

        shouldNotThrowAny {
            calendar.close()
        }
        calendar.isOpen.shouldBeFalse()
    }

    @Test
    fun `close the context that its application built, leaving the others open`() {
        val calendar = ThirdPartyContext.singleTenant("Calendar")
        val domain = BoundedContextBuilder.assumingTests()

        testApplication {
            add(domain)
            add(calendar)
        }.use { application ->
            calendar.close()

            calendar.isOpen.shouldBeFalse()
            application.contexts().first().isOpen.shouldBeTrue()
        }
    }

    @Test
    fun `refuse to be given a second context`() {
        val calendar = ThirdPartyContext.singleTenant("Calendar")

        testApplication {
            add(BoundedContextBuilder.assumingTests())
            add(calendar)
        }.use { application ->
            val error = shouldThrow<IllegalStateException> {
                calendar.attach(application.contexts().first())
            }

            error.message shouldContain "Calendar"
        }
    }
}
