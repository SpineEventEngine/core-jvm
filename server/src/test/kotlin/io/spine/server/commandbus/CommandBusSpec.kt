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

package io.spine.server.commandbus

import com.google.common.collect.ImmutableSet
import com.google.protobuf.Message
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.shouldBe
import io.spine.grpc.StreamObservers.noOpObserver
import io.spine.server.BoundedContextBuilder
import io.spine.server.commandbus.given.CommandDispatcherRegistryTestEnv.CreateProjectAssignee
import io.spine.server.dispatch.DispatchOutcome
import io.spine.server.dispatch.DispatchOutcomes.successfulOutcome
import io.spine.server.type.CommandClass
import io.spine.server.type.CommandEnvelope
import io.spine.system.server.MemoizingWriteSide
import io.spine.system.server.event.commandDispatched
import io.spine.test.commandbus.command.CmdBusCreateProject
import io.spine.testing.server.testApplication
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Checks how [CommandBus] treats the tenants of the commands, and how it records
 * the dispatching of the commands in the System context.
 *
 * Supplements `MultiTenantCommandBusTest` and `SingleTenantCommandBusTest`.
 */
@DisplayName("`CommandBus` should")
internal class CommandBusSpec {

    @Test
    fun `not register the tenant of a posted command`() {
        val application = testApplication {
            add(
                BoundedContextBuilder.assumingTests(/* multiTenant = */ true)
                    .addAssignee(CreateProjectAssignee())
            )
        }
        application.use {
            val context = it.contexts().single()
            val command = Given.ACommand.createProject()

            context.commandBus().post(command, noOpObserver())

            it.tenantIndex()
                .contains(command.tenant())
                .shouldBeFalse()
        }
    }

    @Test
    fun `record the dispatching of a command before passing it to the dispatcher`() {
        val writeSide = MemoizingWriteSide.multiTenant()
        val dispatcher = RecordingDispatcher(writeSide)
        val command = Given.ACommand.createProject()
        CommandBus.newBuilder()
            .setMultiTenant(true)
            .injectSystem(writeSide)
            .build()
            .use { commandBus ->
                commandBus.register(dispatcher)

                commandBus.post(command, noOpObserver())
            }

        dispatcher.lastSystemEvent shouldBe commandDispatched { id = command.id }
    }
}

/**
 * Remembers the last event posted to the given System write side by the time
 * a command is passed to this dispatcher.
 */
private class RecordingDispatcher(private val writeSide: MemoizingWriteSide) : CommandDispatcher {

    var lastSystemEvent: Message? = null
        private set

    override fun messageClasses(): ImmutableSet<CommandClass> =
        CommandClass.setOf(CmdBusCreateProject::class.java)

    override fun dispatch(envelope: CommandEnvelope): DispatchOutcome {
        lastSystemEvent = writeSide.lastSeenEvent().message()
        return successfulOutcome(envelope)
    }
}
