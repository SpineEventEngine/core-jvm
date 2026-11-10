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
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.spine.server.BoundedContextBuilder
import io.spine.server.serverEnvironment
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("Kotlin extensions of `BoundedContextBuilder` for tests should")
internal class TestContextsSpec {

    @Test
    fun `build the context with the technologies of the given environment`() {
        val environment = serverEnvironment {}

        val context = BoundedContextBuilder.assumingTests().buildForTests(environment)

        context.use {
            val access = it.internalAccess()
            access.storageFactory() shouldBeSameInstanceAs environment.storageFactory()
            access.delivery() shouldBeSameInstanceAs environment.delivery()
            access.transportFactory() shouldBeSameInstanceAs environment.transportFactory()
        }
    }

    @Test
    fun `build each context with a new environment by default`() {
        val first = BoundedContextBuilder.assumingTests().buildForTests()
        val second = BoundedContextBuilder.assumingTests().buildForTests()

        try {
            first.internalAccess().delivery() shouldNotBe second.internalAccess().delivery()
        } finally {
            first.close()
            second.close()
        }
    }

    @Test
    fun `leave the environment open when the context closes`() {
        val environment = serverEnvironment {}
        val context = BoundedContextBuilder.assumingTests().buildForTests(environment)

        context.close()

        environment.isOpen.shouldBeTrue()
    }

    @Test
    fun `give the application of the context, which closes the environment`() {
        val environment = serverEnvironment {}
        val builder = BoundedContextBuilder.assumingTests()

        val application = builder.applicationForTests(environment)

        application.contexts().single().name() shouldBe builder.name()
        application.close()
        environment.isOpen.shouldBeFalse()
    }
}
