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
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeInstanceOf
import io.spine.server.aggregate.AggregateRepository
import io.spine.server.delivery.given.DeliveryTestEnv.CalculatorRepository
import io.spine.server.delivery.given.ReceptionistAggregate
import io.spine.server.entity.Repository
import io.spine.test.delivery.Receptionist
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("The check of duplicate entity states should")
internal class DuplicateEntityStateSpec {

    /**
     * The applications built by the current case, closed after it.
     */
    private val built = mutableListOf<Application>()

    @AfterEach
    fun closeApplications() {
        built.forEach(Application::closeIfOpen)
        built.clear()
    }

    @Test
    fun `reject two contexts serving one type, naming the type and both contexts`() {
        val error = shouldThrow<DuplicateEntityStateException> {
            applicationOf(
                context("First", CalculatorRepository()),
                context("Second", CalculatorRepository())
            )
        }

        error.message shouldBe "The entity state type `spine.test.delivery.Calc` cannot be" +
                " served by more than one Bounded Context: `First`, `Second`." +
                " Register the repository of this type in one Bounded Context only."
    }

    @Test
    fun `allow one type in two applications`() {
        applicationOf(context("First", CalculatorRepository()))

        shouldNotThrowAny {
            applicationOf(context("Second", CalculatorRepository()))
        }
    }

    @Test
    fun `build no context, if a type is rejected`() {
        val receptionists = ReceptionistRepository()

        shouldThrow<DuplicateEntityStateException> {
            applicationOf(
                context("First", receptionists, CalculatorRepository()),
                context("Second", CalculatorRepository())
            )
        }

        receptionists.isRegistered.shouldBeFalse()
    }

    @Test
    fun `not count System contexts`() {
        shouldNotThrowAny {
            applicationOf(contextWithCommandLog("First"), contextWithCommandLog("Second"))
        }
    }

    @Test
    fun `leave a duplicate within one context to the checks of the context`() {
        val error = shouldThrow<RuntimeException> {
            applicationOf(context("Single", CalculatorRepository(), CalculatorRepository()))
        }

        error.shouldNotBeInstanceOf<DuplicateEntityStateException>()
    }

    @Test
    fun `close a context that failed to build, even if its 'onBeforeClose' action throws`() {
        val failure = IllegalStateException(REPOSITORY_FAILURE)
        val failing = BoundedContext.singleTenant("Failing")
            .setOnBeforeClose { throw AssertionError(HOOK_FAILURE) }
            .add(CalculatorsFailingOnRegistration(failure))

        val error = shouldThrow<IllegalStateException> {
            applicationOf(failing)
        }

        error shouldBeSameInstanceAs failure
        error.suppressed.map { it.message } shouldContain HOOK_FAILURE
    }

    @Test
    fun `close a context that failed to build, even if closing throws the failure itself`() {
        val failure = IllegalStateException(REPOSITORY_FAILURE)
        val failing = BoundedContext.singleTenant("Failing")
            .setOnBeforeClose { throw failure }
            .add(CalculatorsFailingOnRegistration(failure))

        val error = shouldThrow<IllegalStateException> {
            applicationOf(failing)
        }

        error shouldBeSameInstanceAs failure
    }

    @Test
    fun `close every repository of a context, even if closing them fails`() {
        val calculators = FailingCalculatorRepository()
        val receptionists = FailingReceptionistRepository()
        val context = applicationOf(context("First", calculators, receptionists))
            .contexts()
            .single()

        val error = shouldThrow<IllegalStateException> {
            context.close()
        }

        error.suppressed.map { it.message } shouldContainExactly listOf(REPOSITORY_FAILURE)
        calculators.isOpen shouldBe false
        receptionists.isOpen shouldBe false
    }

    @Test
    fun `close every repository of a context, even if they throw one exception`() {
        val failure = IllegalStateException(REPOSITORY_FAILURE)
        val calculators = FailingCalculatorRepository { failure }
        val receptionists = FailingReceptionistRepository { failure }
        val context = applicationOf(context("First", calculators, receptionists))
            .contexts()
            .single()

        val error = shouldThrow<IllegalStateException> {
            context.close()
        }

        error shouldBeSameInstanceAs failure
        error.suppressed.toList() shouldBe emptyList()
        calculators.isOpen shouldBe false
        receptionists.isOpen shouldBe false
    }

    private fun applicationOf(vararg contexts: BoundedContextBuilder): Application =
        application {
            serverEnvironment = serverEnvironment {}
            contexts.forEach { add(it) }
        }.also(built::add)

    private fun context(
        name: String,
        vararg repositories: Repository<*, *>
    ): BoundedContextBuilder {
        val builder = BoundedContext.singleTenant(name)
        repositories.forEach { builder.add(it) }
        return builder
    }

    private fun contextWithCommandLog(name: String): BoundedContextBuilder {
        val builder = BoundedContext.singleTenant(name)
        builder.systemSettings().enableCommandLog()
        return builder
    }

    /**
     * A repository of receptionists.
     */
    private class ReceptionistRepository :
        AggregateRepository<String, ReceptionistAggregate, Receptionist>()

    /**
     * A repository of calculators that throws from `close()` after closing its resources.
     *
     * @param failure Creates the failure to throw.
     */
    private class FailingCalculatorRepository(
        private val failure: () -> RuntimeException = { IllegalStateException(REPOSITORY_FAILURE) }
    ) : CalculatorRepository() {

        override fun close() {
            super.close()
            throw failure()
        }
    }

    /**
     * A repository of receptionists that throws from `close()` after closing its resources.
     *
     * @param failure Creates the failure to throw.
     */
    private class FailingReceptionistRepository(
        private val failure: () -> RuntimeException = { IllegalStateException(REPOSITORY_FAILURE) }
    ) : AggregateRepository<String, ReceptionistAggregate, Receptionist>() {

        override fun close() {
            super.close()
            throw failure()
        }
    }

    /**
     * A repository of calculators that throws the given failure once it is registered.
     */
    private class CalculatorsFailingOnRegistration(
        private val failure: RuntimeException
    ) : CalculatorRepository() {

        override fun onRegistered() {
            super.onRegistered()
            throw failure
        }
    }

    private companion object {
        const val HOOK_FAILURE = "The action before closing failed."
        const val REPOSITORY_FAILURE = "The repository failed to close."
    }
}
