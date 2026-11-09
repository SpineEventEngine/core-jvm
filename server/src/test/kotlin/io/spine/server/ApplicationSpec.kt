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
import io.spine.core.boundedContextName
import io.spine.server.tenant.TenantIndex
import io.spine.server.tenant.given.InMemoryTenantIndex
import io.spine.system.server.SystemBoundedContexts.systemOf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Checks [Application].
 *
 * Most cases use an application of their own, so that closing it does not close
 * the application of the test JVM. The cases about the contexts use the application
 * of the JVM, because the contexts are recorded there.
 */
@DisplayName("`Application` should")
internal class ApplicationSpec {

    private lateinit var application: Application

    @BeforeEach
    fun createApplication() {
        application = Application()
    }

    @AfterEach
    fun closeApplication() {
        application.closeIfOpen()
    }

    @Test
    fun `be the same for the whole JVM`() {
        Application.instance() shouldBeSameInstanceAs Application.instance()
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
        fun `close the open contexts in the reverse order, and then the tenant index`() {
            val closings = mutableListOf<String>()
            application.use(object : TenantIndex by InMemoryTenantIndex() {
                override fun close() {
                    closings.add("index")
                }
            })
            val first = context { closings.add("first") }
            val second = context { closings.add("second") }
            application.contextOpened(first)
            application.contextOpened(second)

            application.close()

            closings shouldBe listOf("second", "first", "index")
            first.isOpen.shouldBeFalse()
            second.isOpen.shouldBeFalse()
        }

        @Test
        fun `refuse to build a context`() {
            application.close()

            shouldThrow<IllegalStateException> {
                application.checkNameFree(boundedContextName { value = "ApplicationSpec" })
            }
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
    }

    @Nested inner class
    `close only the tenant index` {

        @Test
        fun `creating a new one on the next call`() {
            val index = application.tenantIndex()

            application.closeIndex()

            index.isOpen.shouldBeFalse()
            application.isOpen.shouldBeTrue()
            application.tenantIndex() shouldNotBeSameInstanceAs index
        }

        @Test
        fun `which was set`() {
            val index = InMemoryTenantIndex()
            application.use(index)

            application.closeIndex()

            index.closeCount shouldBe 1
            application.tenantIndex() shouldNotBeSameInstanceAs index
        }

        @Test
        fun `forgetting it even if it fails to close`() {
            val index = InMemoryTenantIndex(failOnClose = true)
            application.use(index)

            shouldThrow<IllegalStateException> {
                application.closeIndex()
            }

            application.tenantIndex() shouldNotBeSameInstanceAs index
        }
    }

    @Nested inner class
    `give the tenant index of the JVM to multitenant contexts` {

        private val jvmApplication = Application.instance()

        @Test
        fun `and to their System contexts`() {
            multitenantContext().use { first ->
                multitenantContext().use { second ->
                    val index = jvmApplication.tenantIndex()
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

            index shouldBeSameInstanceAs jvmApplication.tenantIndex()
            index.isOpen.shouldBeTrue()
        }

        /**
         * Gives the application of the JVM an index of the test, and closes the index after.
         *
         * Closing the index also closes the one that the contexts left open by earlier tests
         * share. JUnit runs the test classes of this module one by one, so no such context
         * is in use.
         */
        @Test
        fun `including the index that was set`() {
            jvmApplication.closeIndex()
            val index = InMemoryTenantIndex()
            jvmApplication.use(index)
            try {
                multitenantContext().use { context ->
                    context.internalAccess().tenantIndex() shouldBeSameInstanceAs index
                    systemOf(context).internalAccess().tenantIndex() shouldBeSameInstanceAs index
                }
                index.closeCount shouldBe 0
            } finally {
                jvmApplication.closeIndex()
            }
        }

        @Test
        fun `but not to a single-tenant context`() {
            context().use { context ->
                context.internalAccess()
                    .tenantIndex() shouldNotBeSameInstanceAs jvmApplication.tenantIndex()
            }
        }
    }

    /**
     * Builds a single-tenant context that runs the given action when it starts closing.
     */
    private fun context(onClose: () -> Unit = {}): BoundedContext =
        BoundedContextBuilder.assumingTests()
            .setOnBeforeClose { onClose() }
            .build()

    /**
     * Builds a multitenant context.
     */
    private fun multitenantContext(): BoundedContext =
        BoundedContextBuilder.assumingTests(/* multitenant = */ true).build()
}
