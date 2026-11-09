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
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import io.spine.server.tenant.TenantIndex
import io.spine.server.tenant.given.InMemoryTenantIndex
import io.spine.system.server.SystemBoundedContexts.systemOf
import io.spine.testing.SingletonTest
import java.util.function.Supplier
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("`Application` should")
internal class ApplicationSpec : SingletonTest<Application>(
    Application::class.java,
    Supplier { Application.instance() }
) {

    private val application = Application.instance()

    /**
     * Resets the application of the test JVM.
     *
     * Closes the index that the contexts built by the tests before this one share. JUnit runs
     * the test classes of this module one by one, so no such context is in use: it is either
     * closed, or left open by a test that has finished.
     */
    @BeforeEach
    fun startWithNoTenantIndex() {
        application.reset()
    }

    @AfterEach
    fun leaveNoTenantIndex() {
        application.reset()
    }

    @Nested inner class
    `obtain the tenant index` {

        @Test
        fun `creating one on the first call, and giving it afterwards`() {
            val index = application.tenantIndex()

            index.isOpen.shouldBeTrue()
            application.tenantIndex() shouldBeSameInstanceAs index
        }

        @Test
        fun `which was set`() {
            val index = InMemoryTenantIndex()
            application.use(index)

            application.tenantIndex() shouldBeSameInstanceAs index
        }
    }

    @Test
    fun `not replace the tenant index in use`() {
        val index = application.tenantIndex()

        shouldThrow<IllegalStateException> {
            application.use(InMemoryTenantIndex())
        }
        application.tenantIndex() shouldBeSameInstanceAs index
    }

    @Nested inner class
    `when closed` {

        @Test
        fun `close the tenant index once`() {
            val index = InMemoryTenantIndex()
            application.use(index)

            application.close()
            application.close()

            index.isOpen.shouldBeFalse()
            index.closeCount shouldBe 1
        }

        @Test
        fun `stay closed even if the tenant index fails to close`() {
            application.use(InMemoryTenantIndex(failOnClose = true))

            shouldThrow<IllegalStateException> {
                application.close()
            }

            application.isOpen.shouldBeFalse()
        }

        @Test
        fun `refuse to give or take the tenant index`() {
            application.close()

            application.isOpen.shouldBeFalse()
            shouldThrow<IllegalStateException> {
                application.tenantIndex()
            }
            shouldThrow<IllegalStateException> {
                application.use(InMemoryTenantIndex())
            }
        }
    }

    @Nested inner class
    `when reset` {

        @Test
        fun `close the tenant index, and create a new one on the next call`() {
            val index = application.tenantIndex()

            application.reset()

            index.isOpen.shouldBeFalse()
            application.tenantIndex() shouldNotBeSameInstanceAs index
        }

        @Test
        fun `close the tenant index that was set`() {
            val index = InMemoryTenantIndex()
            application.use(index)

            application.reset()

            index.closeCount shouldBe 1
            application.tenantIndex() shouldNotBeSameInstanceAs index
        }

        @Test
        fun `open again after being closed`() {
            application.close()

            application.reset()

            application.isOpen.shouldBeTrue()
        }

        @Test
        fun `open again even if the tenant index fails to close`() {
            application.use(InMemoryTenantIndex(failOnClose = true))

            shouldThrow<IllegalStateException> {
                application.reset()
            }

            application.isOpen.shouldBeTrue()
            application.tenantIndex().isOpen.shouldBeTrue()
        }
    }

    @Nested inner class
    `give its tenant index to multitenant contexts` {

        @Test
        fun `and to their System contexts`() {
            multitenantContext().use { first ->
                multitenantContext().use { second ->
                    val index = application.tenantIndex()
                    listOf(first, second, systemOf(first), systemOf(second)).forEach {
                        it.internalAccess().tenantIndex() shouldBeSameInstanceAs index
                    }
                }
            }
        }

        @Test
        fun `which do not close the index`() {
            val context = multitenantContext()
            val index = context.internalAccess().tenantIndex()

            context.close()

            index shouldBeSameInstanceAs application.tenantIndex()
            index.isOpen.shouldBeTrue()
        }
    }

    @Nested inner class
    `let a multitenant context use an index of its own` {

        @Test
        fun `shared with its System context`() {
            val index = InMemoryTenantIndex()
            multitenantContext(index).use { context ->
                context.internalAccess().tenantIndex() shouldBeSameInstanceAs index
                systemOf(context).internalAccess().tenantIndex() shouldBeSameInstanceAs index
            }
        }

        @Test
        fun `which the context closes once`() {
            val index = InMemoryTenantIndex()

            multitenantContext(index).close()

            index.closeCount shouldBe 1
        }
    }

    @Test
    fun `not give its tenant index to a single-tenant context`() {
        BoundedContextBuilder.assumingTests(/* multitenant = */ false).build().use { context ->
            context.internalAccess()
                .tenantIndex() shouldNotBeSameInstanceAs application.tenantIndex()
        }
    }

    private fun multitenantContext(index: TenantIndex? = null): BoundedContext {
        val builder = BoundedContextBuilder.assumingTests(/* multitenant = */ true)
        index?.let { builder.setTenantIndex(it) }
        return builder.build()
    }
}
