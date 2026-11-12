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

import com.google.common.collect.ImmutableSet
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldNotBeInstanceOf
import io.spine.server.commandbus.CommandDispatcher
import io.spine.server.commandbus.given.CommandDispatcherRegistryTestEnv.AddTaskDispatcher
import io.spine.server.commandbus.given.CommandDispatcherRegistryTestEnv.CreateProjectAssignee
import io.spine.server.commandbus.given.CommandDispatcherRegistryTestEnv.CreateProjectDispatcher
import io.spine.server.dispatch.DispatchOutcome
import io.spine.server.dispatch.DispatchOutcomes.successfulOutcome
import io.spine.server.type.CommandClass
import io.spine.server.type.CommandEnvelope
import io.spine.test.aggregate.command.AggCreateProject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("The check of duplicate command handlers should")
internal class DuplicateCommandHandlerSpec {

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
    fun `reject two contexts handling one command type, naming the type and both contexts`() {
        val error = shouldThrow<DuplicateCommandHandlerException> {
            applicationOf(
                context("First").addAssignee(CreateProjectAssignee()),
                context("Second").addCommandDispatcher(CreateProjectDispatcher())
            )
        }

        error.message shouldBe "The commands of the type" +
                " `spine.test.commandbus.CmdBusCreateProject` cannot be handled by more than" +
                " one Bounded Context: `First`, `Second`." +
                " Handle the commands of this type in one Bounded Context only."
    }

    @Test
    fun `count the commands that the repositories of a context handle`() {
        shouldThrow<DuplicateCommandHandlerException> {
            applicationOf(
                context("First").add(Given.ProjectAggregateRepository()),
                context("Second").addCommandDispatcher(CreateAggProject())
            )
        }
    }

    @Test
    fun `build no context, if a command type is rejected`() {
        val projects = Given.ProjectAggregateRepository()

        shouldThrow<DuplicateCommandHandlerException> {
            applicationOf(
                context("First").add(projects),
                context("Second").addCommandDispatcher(CreateAggProject())
            )
        }

        projects.isRegistered.shouldBeFalse()
    }

    @Test
    fun `allow contexts handling commands of distinct types`() {
        shouldNotThrowAny {
            applicationOf(
                context("First").addAssignee(CreateProjectAssignee()),
                context("Second").addCommandDispatcher(AddTaskDispatcher())
            )
        }
    }

    @Test
    fun `allow one command type in two applications`() {
        applicationOf(context("First").addAssignee(CreateProjectAssignee()))

        shouldNotThrowAny {
            applicationOf(context("Second").addCommandDispatcher(CreateProjectDispatcher()))
        }
    }

    @Test
    fun `leave a duplicate within one context to the checks of the context`() {
        val error = shouldThrow<RuntimeException> {
            applicationOf(
                context("Single")
                    .addAssignee(CreateProjectAssignee())
                    .addCommandDispatcher(CreateProjectDispatcher())
            )
        }

        error.shouldNotBeInstanceOf<DuplicateCommandHandlerException>()
    }

    private fun context(name: String): BoundedContextBuilder = BoundedContext.singleTenant(name)

    private fun applicationOf(vararg contexts: BoundedContextBuilder): Application =
        application {
            serverEnvironment = serverEnvironment {}
            contexts.forEach { add(it) }
        }.also(built::add)

    /**
     * Handles the commands that the aggregates of `Given.ProjectAggregateRepository`
     * handle too.
     */
    private class CreateAggProject : CommandDispatcher {

        override fun messageClasses(): ImmutableSet<CommandClass> =
            CommandClass.setOf(AggCreateProject::class.java)

        override fun dispatch(envelope: CommandEnvelope): DispatchOutcome =
            successfulOutcome(envelope)
    }
}
