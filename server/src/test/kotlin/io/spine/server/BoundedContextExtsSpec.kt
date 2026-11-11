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

package io.spine.server

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.spine.server.delivery.given.DeliveryTestEnv.CalculatorRepository
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("The Bounded Context DSL should")
internal class BoundedContextExtsSpec {

    @Nested inner class
    `create a builder of a single-tenant context` {

        @Test
        fun `with the given name`() {
            val builder = singleTenantContext("Tasks") {}

            builder.name().value shouldBe "Tasks"
            builder.isMultiTenant.shouldBeFalse()
        }

        @Test
        fun `configured by the block`() {
            val repository = CalculatorRepository()

            val builder = singleTenantContext("Tasks") {
                add(repository)
            }

            builder.repositories() shouldContainExactly listOf(repository)
        }
    }

    @Nested inner class
    `create a builder of a multi-tenant context` {

        @Test
        fun `with the given name`() {
            val builder = multiTenantContext("Tasks") {}

            builder.name().value shouldBe "Tasks"
            builder.isMultiTenant.shouldBeTrue()
        }

        @Test
        fun `configured by the block`() {
            val repository = CalculatorRepository()

            val builder = multiTenantContext("Tasks") {
                add(repository)
            }

            builder.repositories() shouldContainExactly listOf(repository)
        }
    }

    @Test
    fun `produce a builder an 'Application' accepts`() {
        application {
            serverEnvironment = serverEnvironment {}
            add(singleTenantContext("Tasks") {
                add(CalculatorRepository())
            })
        }.use { application ->
            val context = application.contexts()
                .single()

            context.name().value shouldBe "Tasks"
        }
    }
}
