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

import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.spine.base.Identifier.newUuid
import io.spine.core.Ack
import io.spine.core.Command
import io.spine.core.CommandId
import io.spine.core.ErrorCase.INVALID_REQUEST
import io.spine.core.Subscribe
import io.spine.core.TenantId
import io.spine.core.tenantId
import io.spine.grpc.StreamObservers.noOpObserver
import io.spine.protobuf.pack
import io.spine.server.BoundedContext
import io.spine.server.BoundedContextBuilder
import io.spine.server.CommandService
import io.spine.server.DefaultRepository
import io.spine.server.commandbus.CommandFilter
import io.spine.server.entity.RecordBasedRepository
import io.spine.server.event.AbstractEventSubscriber
import io.spine.server.tenant.TenantAwareRunner
import io.spine.server.type.CommandEnvelope
import io.spine.system.server.SystemBoundedContexts.systemOf
import io.spine.system.server.given.command.CompanyAggregate
import io.spine.system.server.given.command.CompanyNameProcman.FAULTY_NAME
import io.spine.system.server.given.command.CompanyNameProcmanRepo
import io.spine.test.commandbus.command.cmdBusStartProject
import io.spine.test.commandbus.projectId
import io.spine.testing.client.TestActorRequestFactory
import io.spine.testing.logging.mute.MuteLogging
import io.spine.testing.server.buildForTests
import io.spine.type.TypeUrl
import java.util.Optional
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`CommandLogProjection` should")
internal class CommandLogProjectionSpec {

    private val requests = TestActorRequestFactory(CommandLogProjectionSpec::class.java)

    private lateinit var context: BoundedContext
    private lateinit var commandLog:
            RecordBasedRepository<CommandId, CommandLogProjection, CommandLog>

    @BeforeEach
    fun createContext() {
        val builder = BoundedContextBuilder.assumingTests()
            .addCommandFilter(RefusingFilter)
            .addCommandFilter(FailingFilter)
        builder.systemSettings().enableCommandLog()
        context = builder.buildForTests()
        context.internalAccess().run {
            register(DefaultRepository.of(CompanyAggregate::class.java))
            register(CompanyNameProcmanRepo())
        }
        commandLog = commandLogOf(context)
    }

    @AfterEach
    fun closeContext() {
        context.close()
    }

    @Test
    fun `record a handled command`() {
        val command = post(establishing(name = "Good company name"))

        val status = howProcessed(command)

        status.hasOk() shouldBe true
    }

    @Test
    fun `record the rejection of a handler`() {
        val command = post(establishing(name = CompanyAggregate.TAKEN_NAME))

        val status = howProcessed(command)

        status.rejection.enclosedTypeUrl() shouldBe
                TypeUrl.of(Rejections.CompanyNameAlreadyTaken::class.java)
    }

    @Test
    @MuteLogging
    fun `record the error when the handler of a command fails`() {
        val company = CompanyId.generate()
        post(requests.createCommand(startCompanyEstablishing { id = company }))
        val command = post(requests.createCommand(proposeCompanyName {
            id = company
            name = FAULTY_NAME
        }))

        howProcessed(command).error.type shouldBe IllegalArgumentException::class.java.canonicalName
    }

    @Test
    @MuteLogging
    fun `not record the failure of an event handler in the log of the originating command`() {
        val monitor = DiagnosticMonitor()
        context.internalAccess().run {
            registerEventDispatcher(FailingOnEstablished())
            registerEventDispatcher(monitor)
        }

        val command = post(establishing(name = FailingOnEstablished.NAME))

        val failures = monitor.handlerFailureEvents()
        withClue("The subscriber must fail once, on the event produced by the command.") {
            failures shouldHaveSize 1
            val failure = failures.single()
            failure.handledSignal.isEvent shouldBe true
            failure.error.type shouldBe IllegalStateException::class.java.canonicalName
        }
        howProcessed(command).hasOk() shouldBe true
    }

    @Test
    fun `record the violations of a command that fails validation`() {
        val invalid = establishing(name = "Any name").toBuilder()
            .setMessage(EstablishCompany.getDefaultInstance().pack())
            .build()
        val command = post(invalid)

        howProcessed(command).validationError.constraintViolationList.shouldNotBeEmpty()
    }

    @Test
    fun `record a command that no handler in the context handles`() {
        val command = post(requests.createCommand(cmdBusStartProject {
            projectId = projectId { id = newUuid() }
        }))

        howProcessed(command).hasUnsupported() shouldBe true
    }

    @Test
    fun `record the tenant of a command posted to a single-tenant context`() {
        val tenant = tenantId { value = "Tenant of a single-tenant context" }
        val withTenant = TestActorRequestFactory(javaClass, tenant)
        val command = post(withTenant.createCommand(establishCompany {
            id = CompanyId.generate()
            finalName = "A company of a tenant"
        }))

        howProcessed(command).tenantNotExpected shouldBe tenant
    }

    @Test
    fun `record a command without a tenant posted to a multitenant context`() {
        val builder = BoundedContextBuilder.assumingTests(/* multitenant = */ true)
        builder.systemSettings().enableCommandLog()
        builder.buildForTests().use { multitenant ->
            // No handler is needed: the tenant is checked before the receivers of the command.
            val log = commandLogOf(multitenant)
            val command = establishing(name = "A company without a tenant")

            multitenant.commandBus().post(command, noOpObserver())

            val timeline = TenantAwareRunner.with(TenantId.getDefaultInstance()).evaluate {
                log.find(command.id).orElseThrow().state().timeline
            }
            timeline.howProcessed.hasTenantNotSpecified() shouldBe true
        }
    }

    @Test
    @MuteLogging
    fun `record the error when the 'CommandService' fails to post a command`() {
        val command = establishing(name = FailingFilter.NAME)

        CommandService.withSingle(context).post(command, noOpObserver())

        howProcessed(command).postingError.type shouldBe
                IllegalStateException::class.java.canonicalName
    }

    @Test
    fun `record nothing for a command that an application filter refuses`() {
        val command = post(establishing(name = RefusingFilter.NAME))

        timelineOf(command).hasWhenProcessed() shouldBe false
    }

    private fun establishing(name: String): Command =
        requests.createCommand(establishCompany {
            id = CompanyId.generate()
            finalName = name
        })

    private fun post(command: Command): Command {
        context.commandBus().post(command, noOpObserver())
        return command
    }

    private fun howProcessed(command: Command): ProcessingStatus {
        val timeline = timelineOf(command)
        timeline.hasWhenProcessed() shouldBe true
        return timeline.howProcessed
    }

    private fun timelineOf(command: Command): CommandTimeline =
        commandLog.find(command.id).orElseThrow().state().timeline

    private fun commandLogOf(
        context: BoundedContext
    ): RecordBasedRepository<CommandId, CommandLogProjection, CommandLog> {
        val repository = systemOf(context).internalAccess()
            .findRepository(CommandLog::class.java)
            .orElseThrow()
        @Suppress("UNCHECKED_CAST") // The repository of `CommandLog` is known to be such.
        return repository as RecordBasedRepository<CommandId, CommandLogProjection, CommandLog>
    }
}

/**
 * Refuses establishing a company with the [NAME] with the [INVALID_REQUEST] error case.
 */
private object RefusingFilter : CommandFilter {

    const val NAME = "Refused by a filter"

    override fun filter(envelope: CommandEnvelope): Optional<Ack> {
        val message = envelope.message()
        if (message !is EstablishCompany || message.finalName != NAME) {
            return letPass()
        }
        return reject(envelope, INVALID_REQUEST)
    }
}

/**
 * Fails on purpose on establishing a company with the [NAME], as an unexpected failure
 * of the bus would.
 */
private object FailingFilter : CommandFilter {

    const val NAME = "Fails the filter"

    override fun filter(envelope: CommandEnvelope): Optional<Ack> {
        val message = envelope.message()
        check(message !is EstablishCompany || message.finalName != NAME) {
            "The filter fails on purpose."
        }
        return letPass()
    }
}

/**
 * A subscriber that fails on purpose when a company named [NAME] is established.
 */
private class FailingOnEstablished : AbstractEventSubscriber() {

    @Subscribe
    fun on(event: CompanyEstablished) {
        check(event.name != NAME) { "The subscriber fails on purpose." }
    }

    companion object {
        const val NAME = "Fails the subscriber"
    }
}
