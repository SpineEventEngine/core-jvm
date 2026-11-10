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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.spine.server.delivery.given.DeliveryTestEnv.CalculatorRepository
import io.spine.server.integration.ThirdPartyContext
import io.spine.server.tenant.TenantIndex
import io.spine.server.tenant.given.InMemoryTenantIndex
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("`Application` should")
internal class ApplicationSpec {

    @Nested inner class
    `refuse to be built` {

        @Test
        fun `without a server environment`() {
            shouldThrow<IllegalStateException> {
                application {
                    add(BoundedContextBuilder.assumingTests())
                }
            }
        }

        @Test
        fun `without a Bounded Context`() {
            shouldThrow<IllegalStateException> {
                application {
                    serverEnvironment = serverEnvironment {}
                }
            }
        }

        @Test
        fun `with a closed server environment`() {
            val environment = serverEnvironment {}
            environment.close()

            shouldThrow<IllegalStateException> {
                applicationOf(environment, BoundedContextBuilder.assumingTests())
            }
        }

        @Test
        fun `with a server environment of another application`() {
            val environment = serverEnvironment {}
            applicationOf(environment, BoundedContextBuilder.assumingTests()).use {
                shouldThrow<IllegalStateException> {
                    applicationOf(environment, BoundedContextBuilder.assumingTests())
                }
            }
        }

        @Test
        fun `by a builder that already built one`() {
            val builder = Application.newBuilder()
                .setServerEnvironment(serverEnvironment {})
                .add(BoundedContextBuilder.assumingTests())
            builder.build().use {
                shouldThrow<IllegalStateException> {
                    builder.build()
                }
            }
        }
    }

    @Test
    fun `build its contexts in the order of adding`() {
        val first = BoundedContextBuilder.assumingTests()
        val second = BoundedContextBuilder.assumingTests()

        applicationOf(serverEnvironment {}, first, second).use { app ->
            app.contexts().map { it.name() } shouldContainExactly
                    listOf(first.name(), second.name())
            app.contexts().forEach { it.isOpen.shouldBeTrue() }
        }
    }

    @Test
    fun `give its contexts the technologies of its server environment`() {
        val environment = serverEnvironment {}

        applicationOf(environment, BoundedContextBuilder.assumingTests()).use { app ->
            val access = app.contexts().single().internalAccess()
            access.storageFactory() shouldBeSameInstanceAs environment.storageFactory()
            access.delivery() shouldBeSameInstanceAs environment.delivery()
            access.transportFactory() shouldBeSameInstanceAs environment.transportFactory()
        }
    }

    @Test
    fun `release its server environment, if building a context fails`() {
        val environment = serverEnvironment {}
        val failing = BoundedContextBuilder.assumingTests()
            .add(CalculatorsFailingOnRegistration())

        shouldThrow<IllegalStateException> {
            applicationOf(environment, failing)
        }

        environment.isOpen.shouldBeTrue()
        applicationOf(environment, BoundedContextBuilder.assumingTests()).close()
    }

    @Test
    fun `be built with the Kotlin DSL`() {
        val index = InMemoryTenantIndex()
        val builder = BoundedContextBuilder.assumingTests()

        val app = application {
            serverEnvironment = serverEnvironment {}
            tenantIndex = index
            add(builder)
        }

        app.use {
            it.contexts().single().name() shouldBe builder.name()
            it.tenantIndex() shouldBeSameInstanceAs index
        }
    }

    @Test
    fun `build a third-party context added to it`() {
        val calendar = ThirdPartyContext.singleTenant("Calendar")
        calendar.isOpen.shouldBeFalse()

        val app = application {
            serverEnvironment = serverEnvironment {}
            add(calendar)
        }

        calendar.isOpen.shouldBeTrue()
        app.close()
        calendar.isOpen.shouldBeFalse()
    }

    @Nested inner class
    `obtain the tenant index` {

        @Test
        fun `creating one on the first call, and giving it afterwards`() {
            applicationOf(serverEnvironment {}, BoundedContextBuilder.assumingTests()).use {
                val index = it.tenantIndex()

                index.isOpen.shouldBeTrue()
                it.tenantIndex() shouldBeSameInstanceAs index
            }
        }

        @Test
        fun `which was set`() {
            val index = InMemoryTenantIndex()

            applicationOf(index).use {
                it.tenantIndex() shouldBeSameInstanceAs index
            }
        }
    }

    @Nested inner class
    `when closed` {

        @Test
        fun `close its contexts in reverse order, then the tenant index and the environment`() {
            val closings = mutableListOf<String>()
            val environment = serverEnvironment {}
            val index = object : TenantIndex by InMemoryTenantIndex() {
                override fun close() {
                    closings.add("index")
                }
            }
            val app = applicationOf(
                index,
                environment,
                context { closings.add("first") },
                context { closings.add("second") }
            )

            app.close()

            closings shouldBe listOf("second", "first", "index")
            app.contexts().forEach { it.isOpen.shouldBeFalse() }
            environment.isOpen.shouldBeFalse()
            app.isOpen.shouldBeFalse()
        }

        @Test
        fun `keep closing the contexts and the tenant index after an error`() {
            val index = InMemoryTenantIndex()
            val app = applicationOf(
                index,
                serverEnvironment {},
                context(),
                context { throw AssertionError("Closing `second` failed.") }
            )

            val error = shouldThrow<AssertionError> {
                app.close()
            }

            error.message shouldBe "Closing `second` failed."
            app.contexts().forEach { it.isOpen.shouldBeFalse() }
            index.isOpen.shouldBeFalse()
        }

        @Test
        fun `keep closing the contexts after two of them throw one exception instance`() {
            val failure = IllegalStateException("Closing failed.")
            // Closed in the reverse order: the third, the second, and then the first one.
            val app = applicationOf(
                InMemoryTenantIndex(),
                serverEnvironment {},
                context(),
                context { throw failure },
                context { throw failure }
            )

            val error = shouldThrow<IllegalStateException> {
                app.close()
            }

            error shouldBeSameInstanceAs failure
            error.suppressed.shouldBeEmpty()
            app.contexts().first().isOpen.shouldBeFalse()
        }

        @Test
        fun `add a later failure to the first one as suppressed`() {
            val earlier = IllegalStateException("The first closing failed.")
            val later = IllegalStateException("The second closing failed.")
            val app = applicationOf(
                InMemoryTenantIndex(),
                serverEnvironment {},
                context { throw later },
                context { throw earlier }
            )

            val error = shouldThrow<IllegalStateException> {
                app.close()
            }

            error shouldBeSameInstanceAs earlier
            error.suppressed.toList() shouldBe listOf(later)
        }

        @Test
        fun `refuse to give the tenant index`() {
            val app = applicationOf(InMemoryTenantIndex())

            app.close()

            shouldThrow<IllegalStateException> {
                app.tenantIndex()
            }
        }

        @Test
        fun `close the tenant index once`() {
            val index = InMemoryTenantIndex()
            val app = applicationOf(index)

            app.close()
            app.close()

            index.isOpen.shouldBeFalse()
            index.closeCount shouldBe 1
        }

        @Test
        fun `stay closed even if the tenant index fails to close`() {
            val app = applicationOf(InMemoryTenantIndex(failOnClose = true))

            shouldThrow<IllegalStateException> {
                app.close()
            }

            app.isOpen.shouldBeFalse()
        }
    }

    /**
     * Builds an application of the given contexts over the given environment.
     */
    private fun applicationOf(
        environment: ServerEnvironment,
        vararg contexts: BoundedContextBuilder
    ): Application = application {
        serverEnvironment = environment
        contexts.forEach { add(it) }
    }

    /**
     * Builds an application with the given tenant index, a new environment, and the given
     * contexts, or a single test context if none are given.
     */
    private fun applicationOf(
        index: TenantIndex,
        environment: ServerEnvironment = serverEnvironment {},
        vararg contexts: BoundedContextBuilder
    ): Application = application {
        serverEnvironment = environment
        tenantIndex = index
        contexts.ifEmpty { arrayOf(BoundedContextBuilder.assumingTests()) }
            .forEach { add(it) }
    }

    /**
     * Creates the builder of a single-tenant context that runs the given action
     * when it starts closing.
     */
    private fun context(onClose: () -> Unit = {}): BoundedContextBuilder =
        BoundedContextBuilder.assumingTests()
            .setOnBeforeClose { onClose() }

    /**
     * A repository of calculators that throws once it is registered.
     */
    private class CalculatorsFailingOnRegistration : CalculatorRepository() {

        override fun onRegistered() {
            super.onRegistered()
            error("The repository failed on registration.")
        }
    }
}
