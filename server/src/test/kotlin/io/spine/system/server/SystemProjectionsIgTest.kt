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

package io.spine.system.server

import com.google.protobuf.util.Durations
import io.kotest.matchers.collections.shouldContainExactly
import io.spine.base.EntityState
import io.spine.core.Command
import io.spine.core.CommandContextKt
import io.spine.core.CommandId
import io.spine.core.copy
import io.spine.grpc.StreamObservers.noOpObserver
import io.spine.server.Application
import io.spine.server.BoundedContext
import io.spine.server.BoundedContextBuilder
import io.spine.server.DefaultRepository
import io.spine.server.application
import io.spine.server.entity.RecordBasedRepository
import io.spine.server.entity.Repository
import io.spine.server.serverEnvironment
import io.spine.system.server.SystemBoundedContexts.systemOf
import io.spine.system.server.given.command.CompanyAggregate
import io.spine.system.server.given.schedule.TestCommandScheduler
import io.spine.testing.client.TestActorRequestFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Checks that the System projections of a context store the records of the commands
 * posted to this context, when another context with the same System projections
 * shares the `Delivery` with it.
 *
 * Both contexts belong to one application, so they use the `Delivery` of its server
 * environment. The context built last registers the inboxes of its System projections
 * after the first context does.
 */
@DisplayName("The System projections of several contexts should")
internal class SystemProjectionsIgTest {

    private val requests = TestActorRequestFactory(SystemProjectionsIgTest::class.java)

    private lateinit var application: Application
    private lateinit var first: BoundedContext
    private lateinit var last: BoundedContext

    @BeforeEach
    fun buildContexts() {
        application = application {
            serverEnvironment = serverEnvironment {
                scheduleCommandsUsing { TestCommandScheduler() }
            }
            add(contextWithCommandLog(FIRST, DefaultRepository.of(CompanyAggregate::class.java)))
            add(contextWithCommandLog(LAST))
        }
        first = application.contexts().first()
        last = application.contexts().last()
    }

    @AfterEach
    fun closeApplication() {
        application.close()
    }

    @Test
    fun `store the log of a command by the System context of the context that received it`() {
        val command = establishing("A company with the logged command")

        first.commandBus().post(command, noOpObserver())

        systemContextsStoring(CommandLog::class.java, command.id) shouldContainExactly
                listOf(FIRST)
    }

    @Test
    fun `store a scheduled command by the System context of the context that received it`() {
        val command = establishing("A company with the scheduled command").copy {
            context = context.copy {
                schedule = CommandContextKt.schedule { delay = Durations.fromMinutes(1) }
            }
        }

        first.commandBus().post(command, noOpObserver())

        systemContextsStoring(ScheduledCommandRecord::class.java, command.id) shouldContainExactly
                listOf(FIRST)
    }

    private fun establishing(name: String): Command =
        requests.createCommand(establishCompany {
            id = CompanyId.generate()
            finalName = name
        })

    /**
     * Obtains the names of the domain contexts whose System contexts store
     * the entity with the given state type and ID.
     */
    private fun systemContextsStoring(
        stateType: Class<out EntityState<*>>,
        id: CommandId
    ): List<String> =
        listOf(first, last)
            .filter { systemRepositoryOf(it, stateType).find(id).isPresent }
            .map { it.name().value }

    private companion object {

        const val FIRST = "SystemProjectionsFirst"
        const val LAST = "SystemProjectionsLast"

        fun contextWithCommandLog(
            name: String,
            vararg repositories: Repository<*, *>
        ): BoundedContextBuilder {
            val builder = BoundedContext.singleTenant(name)
            builder.systemSettings().enableCommandLog()
            repositories.forEach { builder.add(it) }
            return builder
        }

        fun systemRepositoryOf(
            context: BoundedContext,
            stateType: Class<out EntityState<*>>
        ): RecordBasedRepository<CommandId, *, *> {
            val repository = systemOf(context).internalAccess()
                .findRepository(stateType)
                .orElseThrow()
            @Suppress("UNCHECKED_CAST") // The System repositories of the commands are such.
            return repository as RecordBasedRepository<CommandId, *, *>
        }
    }
}
