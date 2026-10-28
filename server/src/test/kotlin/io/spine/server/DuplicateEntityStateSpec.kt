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
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeInstanceOf
import io.spine.environment.DefaultMode
import io.spine.environment.Environment
import io.spine.environment.Tests
import io.spine.server.aggregate.AggregateRepository
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

    /**
     * The contexts built by the current case.
     *
     * They are closed after each case. Otherwise they would keep serving their entity state
     * types for the rest of the JVM, and fail the cases and the test classes that run later.
     * A case that expects a build to fail adds its context too, so that the context is closed
     * if the build succeeds by mistake.
     */
    private val built = mutableListOf<BoundedContext>()

    /**
     * Fails the case early if a context left open by another test already serves a type
     * used here, so that the case does not fail later for a misleading reason.
     */
    @BeforeEach
    fun checkTypesAreNotServed() {
        listOf(calc, receptionist, emptyAggregate).forEach { type ->
            withClue("A context left open by another test serves `${type.typeName()}`") {
                contextServing(type).shouldBeNull()
            }
        }
    }

    @AfterEach
    fun closeContexts() {
        built.forEach(BoundedContext::closeIfOpen)
        built.clear()
    }

    @Test
    fun `reject a context serving a type that another open context serves`() {
        val first = context("First", CalculatorRepository())

        shouldThrow<DuplicateEntityStateException> {
            context("Second", CalculatorRepository())
        }
        contextServing(calc) shouldBeSameInstanceAs first
    }

    @Test
    fun `allow the type in another context after the first one is closed`() {
        val first = context("First", CalculatorRepository())
        first.close()

        val second = shouldNotThrowAny {
            context("Second", CalculatorRepository())
        }
        contextServing(calc) shouldBeSameInstanceAs second
    }

    @Test
    fun `check a repository added through 'internalAccess()' after the build`() {
        val first = context("First", CalculatorRepository())
        val second = context("Second")

        shouldThrow<DuplicateEntityStateException> {
            second.internalAccess().register(CalculatorRepository())
        }
        contextServing(calc) shouldBeSameInstanceAs first
    }

    @Test
    fun `not let a closed context take a type`() {
        val closed = context("Closed").also { it.close() }

        val error = shouldThrow<IllegalStateException> {
            closed.internalAccess().register(CalculatorRepository())
        }
        error.message shouldContain "already closed"
        contextServing(calc).shouldBeNull()
        shouldNotThrowAny {
            context("Open", CalculatorRepository())
        }
    }

    @Test
    fun `forget a type whose repository fails to register after the build`() {
        val context = context("Rejecting")

        shouldThrow<IllegalStateException> {
            context.internalAccess().register(AnemicAggregateRepository())
        }
        contextServing(emptyAggregate).shouldBeNull()
    }

    @Test
    fun `check contexts built by 'BlackBox', and forget them when it closes`() {
        BlackBox.from(BoundedContext.singleTenant("First").add(CalculatorRepository())).use {
            shouldThrow<DuplicateEntityStateException> {
                // Closed at once in case the build succeeds by mistake, so that the `BlackBox`
                // does not leak.
                BlackBox.from(BoundedContext.singleTenant("Second").add(CalculatorRepository()))
                    .close()
            }
        }

        contextServing(calc).shouldBeNull()
    }

    @Test
    fun `not count System contexts`() {
        shouldNotThrowAny {
            contextWithCommandLog("First")
            contextWithCommandLog("Second")
        }
        contextServing(TypeUrl.of(CommandLog::class.java)).shouldBeNull()
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
        contextServing(receptionist).shouldBeNull()
    }

    @Test
    fun `close a context that failed to build, even if its 'onBeforeClose' action throws`() {
        context("First", CalculatorRepository())

        val error = shouldThrow<DuplicateEntityStateException> {
            BoundedContext.singleTenant("Second")
                .setOnBeforeClose { throw AssertionError(HOOK_FAILURE) }
                .add(ReceptionistAggregate::class.java)
                .add(CalculatorRepository())
                .build()
                .also(built::add)
        }
        error.suppressed.map { it.message } shouldContain HOOK_FAILURE
        contextServing(receptionist).shouldBeNull()
    }

    @Test
    fun `close every repository and forget the context, even if closing them fails`() {
        val calculators = FailingCalculatorRepository()
        val receptionists = FailingReceptionistRepository()
        val first = context("First", calculators, receptionists)

        val error = shouldThrow<IllegalStateException> {
            first.close()
        }
        error.suppressed.map { it.message } shouldContainExactly listOf(REPOSITORY_FAILURE)
        calculators.isOpen shouldBe false
        receptionists.isOpen shouldBe false
        contextServing(calc).shouldBeNull()
        contextServing(receptionist).shouldBeNull()
    }

    @Test
    fun `leave a duplicate within one context to the checks of the context`() {
        val context = context("Single", CalculatorRepository())

        val error = shouldThrow<RuntimeException> {
            context.internalAccess().register(CalculatorRepository())
        }
        error.shouldNotBeInstanceOf<DuplicateEntityStateException>()
        contextServing(calc) shouldBeSameInstanceAs context
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
            try {
                closeContexts()
            } finally {
                environment.reset()
                Environment.instance().setTo(Tests::class.java)
            }
        }

        @Test
        fun `reject a second context, naming the type and both contexts`() {
            context("First", CalculatorRepository())

            val error = shouldThrow<DuplicateEntityStateException> {
                context("Second", CalculatorRepository())
            }

            error.message shouldBe "The entity state type `spine.test.delivery.Calc` cannot be" +
                    " served by more than one Bounded Context: `First`, `Second`." +
                    " Register the repository of this type in one Bounded Context only."
        }
    }

    @Nested inner class
    `find the open context serving a type, reporting` {

        @Test
        fun none() {
            context("First", CalculatorRepository())

            contextServing(receptionist).shouldBeNull()
        }

        @Test
        fun `the serving one`() {
            val first = context("First", CalculatorRepository())

            contextServing(calc) shouldBeSameInstanceAs first
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

    private fun contextServing(type: TypeUrl): BoundedContext? =
        OpenContexts.findContextServing(type)

    /**
     * A repository of calculators that throws from `close()` after closing its resources.
     */
    private class FailingCalculatorRepository : CalculatorRepository() {

        override fun close() {
            super.close()
            error(REPOSITORY_FAILURE)
        }
    }

    /**
     * A repository of receptionists that throws from `close()` after closing its resources.
     */
    private class FailingReceptionistRepository :
        AggregateRepository<String, ReceptionistAggregate, Receptionist>() {

        override fun close() {
            super.close()
            error(REPOSITORY_FAILURE)
        }
    }

    private companion object {
        val calc: TypeUrl = TypeUrl.of(Calc::class.java)
        val receptionist: TypeUrl = TypeUrl.of(Receptionist::class.java)
        val emptyAggregate: TypeUrl = TypeUrl.of(EmptyAggregate::class.java)

        const val HOOK_FAILURE = "The action before closing failed."
        const val REPOSITORY_FAILURE = "The repository failed to close."
    }
}
