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

package io.spine.testing.server.blackbox

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.spine.server.BoundedContextBuilder
import io.spine.server.serverEnvironment
import io.spine.testing.server.testApplication
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`BlackBox` created from an application should")
internal class BlackBoxApplicationSpec {

    @Test
    fun `test the only context of the application`() {
        val application = testApplication { add(BoundedContextBuilder.assumingTests()) }

        BlackBox.from(application).use { box ->
            box.context() shouldBeSameInstanceAs application.contexts().single()
        }
    }

    @Test
    fun `close the application, together with its environment`() {
        val environment = serverEnvironment {}
        val application = testApplication {
            serverEnvironment = environment
            add(BoundedContextBuilder.assumingTests())
        }

        BlackBox.from(application).close()

        application.isOpen.shouldBeFalse()
        environment.isOpen.shouldBeFalse()
    }

    @Test
    fun `reject an application of several contexts`() {
        testApplication {
            add(BoundedContextBuilder.assumingTests())
            add(BoundedContextBuilder.assumingTests())
        }.use { application ->
            val exception = shouldThrow<IllegalArgumentException> {
                BlackBox.from(application)
            }

            exception.message shouldContain "2 contexts"
        }
    }
}
