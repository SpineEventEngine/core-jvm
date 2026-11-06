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
import io.kotest.matchers.shouldBe
import io.spine.core.Ack
import io.spine.core.Command
import io.spine.core.CommandId
import io.spine.core.CommandValidationError
import io.spine.core.ErrorCase.INVALID_REQUEST
import io.spine.core.Subscribe
import io.spine.core.ack
import io.spine.core.status
import io.spine.core.toError
import io.spine.grpc.StreamObservers.noOpObserver
import io.spine.protobuf.pack
import io.spine.server.BoundedContext
import io.spine.server.BoundedContextBuilder
import io.spine.server.DefaultRepository
import io.spine.server.commandbus.CommandFilter
import io.spine.server.entity.RecordBasedRepository
import io.spine.server.event.AbstractEventSubscriber
import io.spine.server.type.CommandEnvelope
import io.spine.system.server.SystemBoundedContexts.systemOf
import io.spine.system.server.given.command.CompanyAggregate
import io.spine.system.server.given.command.CompanyNameProcman.FAULTY_NAME
import io.spine.system.server.given.command.CompanyNameProcmanRepo
import io.spine.testing.client.TestActorRequestFactory
import io.spine.testing.logging.mute.MuteLogging
import io.spine.type.TypeUrl
import io.spine.validation.ValidationError
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
            .addCommandFilter(AnsweringErrorCase)
        builder.systemSettings().enableCommandLog()
        context = builder.build()
        context.internalAccess().run {
            register(DefaultRepository.of(CompanyAggregate::class.java))
            register(CompanyNameProcmanRepo())
        }
        val repository = systemOf(context).internalAccess()
            .findRepository(CommandLog::class.java)
            .orElseThrow()
        @Suppress("UNCHECKED_CAST") // The repository of `CommandLog` is known to be such.
        val typed = repository as RecordBasedRepository<CommandId, CommandLogProjection, CommandLog>
        commandLog = typed
    }

    @AfterEach
    fun closeContext() {
        context.close()
    }

    @Test
    fun `record a handled command`() {
        val command = post(establishing(name = "Good company name"))

        val status = howHandled(command)

        status.hasOk() shouldBe true
    }

    @Test
    fun `record the rejection of a handler`() {
        val command = post(establishing(name = CompanyAggregate.TAKEN_NAME))

        val status = howHandled(command)

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

        howHandled(command).error.type shouldBe IllegalArgumentException::class.java.canonicalName
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
        howHandled(command).hasOk() shouldBe true
    }

    @Test
    fun `record the error with the details when the bus does not accept a command`() {
        val invalid = establishing(name = "Any name").toBuilder()
            .setMessage(EstablishCompany.getDefaultInstance().pack())
            .build()
        val command = post(invalid)

        val error = howHandled(command).error
        error.type shouldBe CommandValidationError.getDescriptor().fullName
        error.code shouldBe CommandValidationError.INVALID_COMMAND.number
        error.details.`is`(ValidationError::class.java) shouldBe true
    }

    @Test
    fun `record the error case when a filter answers with it`() {
        val command = post(establishing(name = AnsweringErrorCase.NAME))

        howHandled(command).error shouldBe INVALID_REQUEST.toError()
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

    private fun howHandled(command: Command): HandlingStatus {
        val timeline = commandLog.find(command.id).orElseThrow().state().timeline
        timeline.hasWhenHandled() shouldBe true
        return timeline.howHandled
    }
}

/**
 * Answers establishing a company with the [NAME] with the [INVALID_REQUEST] error case.
 */
private object AnsweringErrorCase : CommandFilter {

    const val NAME = "Answered with an error case"

    override fun filter(envelope: CommandEnvelope): Optional<Ack> {
        val message = envelope.message()
        if (message !is EstablishCompany || message.finalName != NAME) {
            return letPass()
        }
        val id = envelope.id()
        val answerStatus = status { errorCase = INVALID_REQUEST }
        val answer = ack {
            messageId = id.pack()
            status = answerStatus
        }
        return Optional.of(answer)
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
