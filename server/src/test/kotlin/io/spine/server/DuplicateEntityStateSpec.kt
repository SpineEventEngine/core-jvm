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
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldNotBeInstanceOf
import io.spine.core.BoundedContextName
import io.spine.core.BoundedContextNames.newName
import io.spine.environment.DefaultMode
import io.spine.environment.Environment
import io.spine.environment.Tests
import io.spine.server.aggregate.given.repo.AnemicAggregateRepository
import io.spine.server.delivery.given.DeliveryTestEnv.CalculatorRepository
import io.spine.server.delivery.given.ReceptionistAggregate
import io.spine.server.entity.Repository
import io.spine.server.storage.memory.InMemoryStorageFactory
import io.spine.server.test.shared.EmptyAggregate
import io.spine.server.transport.memory.InMemoryTransportFactory
import io.spine.system.server.CommandLog
import io.spine.test.delivery.Calc
import io.spine.test.delivery.Receptionist
import io.spine.testing.server.blackbox.BlackBox
import io.spine.type.TypeUrl
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("The check of duplicate entity states should")
internal class DuplicateEntityStateSpec {

    private val environment = ServerEnvironment.instance()
    private val built = mutableListOf<BoundedContext>()

    /**
     * Fails the case early if a context left open by another test already serves a type
     * used here, so that the case does not fail later for a misleading reason.
     */
    @BeforeEach
    fun checkTypesAreNotServed() {
        listOf(calc, receptionist).forEach { type ->
            withClue("Contexts left open by other tests serve `${type.typeName()}`") {
                environment.contextsServing(type).shouldBeEmpty()
            }
        }
    }

    /**
     * Closes the contexts built by the case and resets the environment, so that the next case
     * starts with the default value of the check, rather than with a value set explicitly.
     */
    @AfterEach
    fun closeContextsAndResetEnvironment() {
        closeContexts()
        environment.reset()
    }

    @Test
    fun `forget a type whose repository fails to register after the build`() {
        val context = context("Rejecting")

        shouldThrow<IllegalStateException> {
            context.internalAccess().register(AnemicAggregateRepository())
        }
        environment.contextsServing(emptyAggregate) shouldNotContain name("Rejecting")
    }

    @Nested inner class
    `outside of tests` {

        @BeforeEach
        fun turnToProduction() {
            Environment.instance().setTo(DefaultMode::class.java)
            under<DefaultMode> {
                use(InMemoryStorageFactory.newInstance())
                use(InMemoryTransportFactory.newInstance())
            }
        }

        @AfterEach
        fun backToTests() {
            closeContexts()
            environment.reset()
            Environment.instance().setTo(Tests::class.java)
        }

        @Test
        fun `be on by default, naming the type and both contexts`() {
            context("First", CalculatorRepository())

            val error = shouldThrow<DuplicateEntityStateException> {
                context("Second", CalculatorRepository())
            }

            error.message shouldContain "`${calc.typeName()}`"
            error.message shouldContain "`First`, `Second`"
        }
    }

    @Nested inner class
    `in tests` {

        @Test
        fun `be off by default`() {
            context("First", CalculatorRepository())

            shouldNotThrowAny {
                context("Second", CalculatorRepository())
            }
        }

        @Test
        fun `be turned on`() {
            under<Tests> { checkDuplicateEntityStates(true) }
            context("First", CalculatorRepository())

            shouldThrow<DuplicateEntityStateException> {
                context("Second", CalculatorRepository())
            }
        }
    }

    @Nested inner class
    `when on` {

        @BeforeEach
        fun turnCheckOn() {
            under<Tests> { checkDuplicateEntityStates(true) }
        }

        @Test
        fun `allow the type in another context after the first one is closed`() {
            val first = context("First", CalculatorRepository())
            first.close()

            shouldNotThrowAny {
                context("Second", CalculatorRepository())
            }
            environment.contextsServing(calc) shouldContainExactly listOf(name("Second"))
        }

        @Test
        fun `check a repository added through 'internalAccess()' after the build`() {
            context("First", CalculatorRepository())
            val second = context("Second")

            shouldThrow<DuplicateEntityStateException> {
                second.internalAccess().register(CalculatorRepository())
            }
            environment.contextsServing(calc) shouldContainExactly listOf(name("First"))
        }

        @Test
        fun `check contexts built by 'BlackBox', and forget them when it closes`() {
            BlackBox.from(BoundedContext.singleTenant("First").add(CalculatorRepository())).use {
                shouldThrow<DuplicateEntityStateException> {
                    // Closed at once if the check fails to throw, so that it does not leak.
                    BlackBox.from(BoundedContext.singleTenant("Second").add(CalculatorRepository()))
                        .close()
                }
            }

            environment.contextsServing(calc).shouldBeEmpty()
        }

        @Test
        fun `not count System contexts`() {
            shouldNotThrowAny {
                contextWithCommandLog("First")
                contextWithCommandLog("Second")
            }
            environment.contextsServing(TypeUrl.of(CommandLog::class.java)).shouldBeEmpty()
        }

        @Test
        fun `close a context that failed to build, so that it serves none of its types`() {
            context("First", CalculatorRepository())

            shouldThrow<DuplicateEntityStateException> {
                BoundedContext.singleTenant("Second")
                    .add(ReceptionistAggregate::class.java)
                    .add(CalculatorRepository())
                    .build()
                    .also(built::add)
            }
            environment.contextsServing(receptionist).shouldBeEmpty()
        }

        @Test
        fun `close a context that failed to build, even if its 'onBeforeClose' action throws`() {
            context("First", CalculatorRepository())

            val error = shouldThrow<DuplicateEntityStateException> {
                BoundedContext.singleTenant("Second")
                    .setOnBeforeClose { error(HOOK_FAILURE) }
                    .add(ReceptionistAggregate::class.java)
                    .add(CalculatorRepository())
                    .build()
                    .also(built::add)
            }
            error.suppressed.map { it.message } shouldContain HOOK_FAILURE
            environment.contextsServing(receptionist).shouldBeEmpty()
        }

        @Test
        fun `forget a context even if closing one of its repositories fails`() {
            val first = context("First", FailingToCloseRepository())

            shouldThrow<IllegalStateException> {
                first.close()
            }
            environment.contextsServing(calc).shouldBeEmpty()
        }

        @Test
        fun `leave a duplicate within one context to the checks of the context`() {
            val context = context("Single", CalculatorRepository())

            val error = shouldThrow<RuntimeException> {
                context.internalAccess().register(CalculatorRepository())
            }
            error.shouldNotBeInstanceOf<DuplicateEntityStateException>()
            environment.contextsServing(calc) shouldContainExactly listOf(name("Single"))
        }
    }

    @Nested inner class
    `tell which open contexts serve a type, reporting` {

        @Test
        fun none() {
            context("First", CalculatorRepository())

            environment.contextsServing(receptionist).shouldBeEmpty()
        }

        @Test
        fun one() {
            context("First", CalculatorRepository())

            environment.contextsServing(calc) shouldContainExactly listOf(name("First"))
        }

        @Test
        fun `several, when the check is off`() {
            context("First", CalculatorRepository())
            context("Second", CalculatorRepository())

            environment.contextsServing(calc) shouldContainExactly
                    listOf(name("First"), name("Second"))
        }
    }

    private fun context(name: String, vararg repositories: Repository<*, *>): BoundedContext {
        val builder = BoundedContext.singleTenant(name)
        repositories.forEach { builder.add(it) }
        return builder.build().also(built::add)
    }

    private fun contextWithCommandLog(name: String): BoundedContext {
        val builder = BoundedContext.singleTenant(name)
        builder.systemSettings().enableCommandLog()
        return builder.build().also(built::add)
    }

    private fun closeContexts() {
        built.forEach(BoundedContext::closeIfOpen)
        built.clear()
    }

    /**
     * A repository that throws after closing its resources.
     */
    private class FailingToCloseRepository : CalculatorRepository() {

        override fun close() {
            super.close()
            error("The repository failed to close.")
        }
    }

    private companion object {
        val calc: TypeUrl = TypeUrl.of(Calc::class.java)
        val receptionist: TypeUrl = TypeUrl.of(Receptionist::class.java)
        val emptyAggregate: TypeUrl = TypeUrl.of(EmptyAggregate::class.java)

        const val HOOK_FAILURE = "The action before closing failed."

        fun name(value: String): BoundedContextName = newName(value)
    }
}
