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

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.spine.core.BoundedContextNames
import io.spine.server.delivery.given.DeliveryTestEnv.CalculatorRepository
import io.spine.test.delivery.Calc
import io.spine.type.TypeUrl
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("The check of duplicate context names should")
internal class DuplicateContextNameSpec {

    /**
     * The contexts built by the current case.
     *
     * They are closed after each case, so that their names are free for the cases and
     * the test classes that run later.
     */
    private val built = mutableListOf<BoundedContext>()

    @AfterEach
    fun closeContexts() {
        built.forEach(BoundedContext::closeIfOpen)
        built.clear()
    }

    @Test
    fun `reject a context with the name of another open context, naming it`() {
        context(sameName)

        val error = shouldThrow<DuplicateContextNameException> {
            context(sameName)
        }
        error.message shouldBe "A Bounded Context named `$sameName` is already open." +
                " Close it, or give the new context another name."
    }

    @Test
    fun `allow several open contexts built by 'assumingTests()', each with a name of its own`() {
        val first = BoundedContextBuilder.assumingTests().build().also(built::add)
        val second = shouldNotThrowAny {
            BoundedContextBuilder.assumingTests(/* multitenant = */ true).build().also(built::add)
        }

        first.name() shouldNotBe second.name()
        listOf(first, second).forEach {
            it.name().value shouldStartWith BoundedContextNames.assumingTestsValue() + "-"
        }
    }

    @Test
    fun `allow the name once the context with it is closed`() {
        context(reusedName).close()

        shouldNotThrowAny {
            context(reusedName)
        }
    }

    @Test
    fun `build nothing for a rejected name`() {
        val first = context(sameName, CalculatorRepository())
        val rejected = CalculatorRepository()

        shouldThrow<DuplicateContextNameException> {
            context(sameName, rejected)
        }
        rejected.isRegistered shouldBe false
        OpenContexts.findContextServing(calc) shouldBeSameInstanceAs first
    }

    @Test
    fun `free the name of a context that fails to build for another reason`() {
        context(firstName, CalculatorRepository())

        shouldThrow<DuplicateEntityStateException> {
            context(secondName, CalculatorRepository())
        }
        shouldNotThrowAny {
            context(secondName)
        }
    }

    @Test
    fun `count the name of a context while its repositories are registered`() {
        val nested = BuildingContextOnRegistration(sameName)

        shouldThrow<DuplicateContextNameException> {
            context(sameName, nested)
        }
        shouldNotThrowAny {
            context(sameName)
        }
    }

    private fun context(name: String, vararg repositories: CalculatorRepository): BoundedContext {
        val builder = BoundedContext.singleTenant(name)
        repositories.forEach { builder.add(it) }
        return builder.build().also(built::add)
    }

    /**
     * A repository that builds a context with the given name when it is registered.
     *
     * The context of the repository is open under its name by then, so this build fails.
     */
    private inner class BuildingContextOnRegistration(
        private val name: String
    ) : CalculatorRepository() {

        override fun onRegistered() {
            super.onRegistered()
            context(name)
        }
    }

    private companion object {

        val calc: TypeUrl = TypeUrl.of(Calc::class.java)

        /*
         * The names of the contexts of this spec, which no other test uses. So a context left
         * open by another test cannot fail these cases for a misleading reason.
         */
        const val sameName = "DuplicateContextNameSpec.Same"
        const val reusedName = "DuplicateContextNameSpec.Reused"
        const val firstName = "DuplicateContextNameSpec.First"
        const val secondName = "DuplicateContextNameSpec.Second"
    }
}
