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

import io.kotest.matchers.shouldBe
import io.spine.core.Command
import io.spine.core.CommandId
import io.spine.core.CommandValidationError
import io.spine.grpc.StreamObservers.noOpObserver
import io.spine.protobuf.pack
import io.spine.server.BoundedContext
import io.spine.server.BoundedContextBuilder
import io.spine.server.DefaultRepository
import io.spine.server.entity.RecordBasedRepository
import io.spine.system.server.SystemBoundedContexts.systemOf
import io.spine.system.server.given.command.CompanyAggregate
import io.spine.system.server.given.command.CompanyNameProcman.FAULTY_NAME
import io.spine.system.server.given.command.CompanyNameProcmanRepo
import io.spine.testing.client.TestActorRequestFactory
import io.spine.testing.logging.mute.MuteLogging
import io.spine.type.TypeUrl
import io.spine.validation.ValidationError
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
    @MuteLogging
    fun `record the error when the handler of an accepted command fails`() {
        val id = CompanyId.generate()
        post(requests.createCommand(startCompanyEstablishing { this.id = id }))
        val command = post(requests.createCommand(proposeCompanyName {
            this.id = id
            name = FAULTY_NAME
        }))

        howHandled(command).error.type shouldBe IllegalArgumentException::class.java.canonicalName
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
