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

package io.spine.testing.server

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.spine.server.Application
import io.spine.server.BoundedContextBuilder
import io.spine.server.serverEnvironment
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Applications for tests should")
internal class TestApplicationsSpec {

    @Test
    fun `build the added contexts in the order of adding`() {
        val first = BoundedContextBuilder.assumingTests()
        val second = BoundedContextBuilder.assumingTests()

        val application = testApplication {
            add(first)
            add(second)
        }

        application.use {
            it.contexts().map { context -> context.name() } shouldContainExactly
                    listOf(first.name(), second.name())
        }
    }

    @Test
    fun `have a new environment for each application by default`() {
        val first = testApplication { add(BoundedContextBuilder.assumingTests()) }
        val second = testApplication { add(BoundedContextBuilder.assumingTests()) }

        try {
            delivery(first) shouldNotBe delivery(second)
        } finally {
            first.close()
            second.close()
        }
    }

    @Test
    fun `use the environment that the block sets`() {
        val environment = serverEnvironment {}

        val application = testApplication {
            serverEnvironment = environment
            add(BoundedContextBuilder.assumingTests())
        }

        application.use {
            delivery(it) shouldBeSameInstanceAs environment.delivery()
        }
    }

    @Test
    fun `close the environment with the application`() {
        val environment = serverEnvironment {}
        val application = testApplication {
            serverEnvironment = environment
            add(BoundedContextBuilder.assumingTests())
        }

        application.close()

        environment.isOpen.shouldBeFalse()
    }

    @Test
    fun `give Java a builder with an environment set`() {
        val application = newTestApplicationBuilder()
            .add(BoundedContextBuilder.assumingTests())
            .build()

        application.use {
            it.contexts().single().isOpen.shouldBeTrue()
        }
    }

    private fun delivery(application: Application) =
        application.contexts()
            .single()
            .internalAccess()
            .delivery()
}
