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
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.spine.core.Ack
import io.spine.core.BoundedContextNames
import io.spine.server.bus.BusFilter
import io.spine.server.delivery.given.DeliveryTestEnv.CalculatorRepository
import io.spine.server.type.CommandEnvelope
import java.util.Optional
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("The check of duplicate context names should")
internal class DuplicateContextNameSpec {

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
    fun `reject two contexts with one name, naming it`() {
        val error = shouldThrow<DuplicateContextNameException> {
            applicationOf(context(sameName), context(sameName))
        }

        error.message shouldBe "Another Bounded Context of the application is named" +
                " `$sameName`. Please give each context a name of its own."
    }

    @Test
    fun `reject a context with the name of the System context of another one`() {
        val error = shouldThrow<DuplicateContextNameException> {
            applicationOf(context(ownerName), context(ownerSystemName))
        }

        error.message shouldContain "`$ownerSystemName`"
    }

    @Test
    fun `reject a context whose System context would have the name of another one`() {
        val error = shouldThrow<DuplicateContextNameException> {
            applicationOf(context(ownerSystemName), context(ownerName))
        }

        error.message shouldContain "`$ownerSystemName`"
    }

    @Test
    fun `allow several contexts built by 'assumingTests()', each with a name of its own`() {
        val app = applicationOf(
            BoundedContextBuilder.assumingTests(),
            BoundedContextBuilder.assumingTests(/* multitenant = */ true)
        )

        val (first, second) = app.contexts()
        first.name() shouldNotBe second.name()
        listOf(first, second).forEach {
            it.name().value shouldStartWith BoundedContextNames.assumingTestsValue() + "-"
        }
    }

    @Test
    fun `allow one name in two applications`() {
        applicationOf(context(sameName))

        shouldNotThrowAny {
            applicationOf(context(sameName))
        }
    }

    @Test
    fun `build no context, if a name is rejected`() {
        val repository = CalculatorRepository()
        val environment = serverEnvironment {}

        shouldThrow<DuplicateContextNameException> {
            applicationOf(environment, context(sameName, repository), context(sameName))
        }

        repository.isRegistered.shouldBeFalse()
        shouldNotThrowAny {
            applicationOf(environment, context(sameName))
        }
    }

    @Test
    fun `close the rest of a context whose command bus fails to close`() {
        val context = BoundedContextBuilder.assumingTests(failingName)
            .addCommandFilter(FailingToClose())
            .add(CalculatorRepository())
            .build()

        shouldThrow<IllegalStateException> {
            context.close()
        }
        withClue("The parts closed after the failing command bus") {
            context.eventBus().isOpen shouldBe false
            context.internalAccess().broker().isOpen shouldBe false
            context.stand().isOpen shouldBe false
        }
    }

    private fun applicationOf(vararg contexts: BoundedContextBuilder): Application =
        applicationOf(serverEnvironment {}, *contexts)

    private fun applicationOf(
        environment: ServerEnvironment,
        vararg contexts: BoundedContextBuilder
    ): Application = application {
        serverEnvironment = environment
        contexts.forEach { add(it) }
    }.also(built::add)

    private fun context(
        name: String,
        vararg repositories: CalculatorRepository
    ): BoundedContextBuilder {
        val builder = BoundedContext.singleTenant(name)
        repositories.forEach { builder.add(it) }
        return builder
    }

    /**
     * A command filter that fails to close, which makes closing the command bus fail.
     */
    private class FailingToClose : BusFilter<CommandEnvelope> {

        override fun filter(envelope: CommandEnvelope): Optional<Ack> = Optional.empty()

        override fun close() = error("The filter failed to close.")
    }

    private companion object {

        const val sameName = "DuplicateContextNameSpec.Same"
        const val failingName = "DuplicateContextNameSpec.Failing"
        const val ownerName = "DuplicateContextNameSpec.Owner"

        /**
         * The name of the System context of the context named [ownerName].
         */
        val ownerSystemName: String = BoundedContextNames.newName(ownerName).toSystem().value
    }
}
