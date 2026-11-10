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

import com.google.protobuf.Any
import com.google.protobuf.ByteString
import io.grpc.Status.Code.INTERNAL
import io.grpc.StatusRuntimeException
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.spine.base.Identifier
import io.spine.core.Ack
import io.spine.core.Command
import io.spine.core.ErrorCase.INTERNAL_ERROR
import io.spine.core.ErrorCase.INVALID_REQUEST
import io.spine.core.ErrorCase.UNSUPPORTED_REQUEST
import io.spine.core.Status.StatusCase.ERROR_CASE
import io.spine.grpc.StreamObservers.memoizingObserver
import io.spine.server.commandbus.CommandFilter
import io.spine.server.given.RequestEventMonitor
import io.spine.server.given.errorCase
import io.spine.server.type.CommandEnvelope
import io.spine.system.server.event.CommandPostingFailed
import io.spine.test.aggregate.ProjectId
import io.spine.test.aggregate.command.AggCreateProject
import io.spine.test.commandservice.CmdServDontHandle
import io.spine.test.commandservice.command.CsSuspendProject
import io.spine.testing.client.TestActorRequestFactory
import io.spine.testing.logging.mute.MuteLogging
import io.spine.testing.server.buildForTests
import io.spine.type.TypeUrl
import io.spine.validation.NonValidated
import java.util.Optional
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Supplements the Java suite `CommandServiceTest` with the specs of what the service answers
 * to a command it does not post, and what it records.
 */
@MuteLogging
@DisplayName("`CommandService` should")
internal class CommandServiceErrorCaseSpec {

    private val requests = TestActorRequestFactory(javaClass)
    private val monitor = RequestEventMonitor()
    private lateinit var context: BoundedContext
    private lateinit var service: CommandService

    @BeforeEach
    fun createService() {
        context = BoundedContextBuilder.assumingTests()
            .add(Given.ProjectAggregateRepository())
            .addCommandFilter(FailingFilter)
            .buildForTests()
        context.internalAccess().registerEventDispatcher(monitor)
        service = CommandService.withSingle(context)
    }

    @AfterEach
    fun closeContext() {
        context.close()
    }

    @Nested inner class
    `refuse a command, recording nothing, if its type is` {

        @Test
        fun internal() {
            val command = requests.createCommand(CsSuspendProject.getDefaultInstance())

            post(command).status.errorCase shouldBe UNSUPPORTED_REQUEST

            monitor.events.shouldBeEmpty()
        }

        @Test
        fun `served by no context`() {
            val command = requests.createCommand(CmdServDontHandle.getDefaultInstance())

            post(command).status.errorCase shouldBe UNSUPPORTED_REQUEST

            monitor.events.shouldBeEmpty()
        }

        @Test
        fun `unknown to the server`() {
            val unknown = Any.newBuilder()
                .setTypeUrl("type.spine.io/spine.test.NoSuchCommand")
                .build()

            post(withMessage(unknown)).status.errorCase shouldBe UNSUPPORTED_REQUEST

            monitor.events.shouldBeEmpty()
        }

        @Test
        fun `internal, whatever the message holds`() {
            val malformed = malformedMessageOf(TypeUrl.of(CsSuspendProject::class.java))

            post(withMessage(malformed)).status.errorCase shouldBe UNSUPPORTED_REQUEST

            monitor.events.shouldBeEmpty()
        }

        @Test
        fun `unknown to the server, whatever the message holds`() {
            val unknownType = TypeUrl.parse("type.spine.io/spine.test.NoSuchCommand")
            val malformed = malformedMessageOf(unknownType)

            post(withMessage(malformed)).status.errorCase shouldBe UNSUPPORTED_REQUEST

            monitor.events.shouldBeEmpty()
        }

        @Test
        fun `not set`() {
            post(withMessage(Any.getDefaultInstance())).status.errorCase shouldBe INVALID_REQUEST

            monitor.events.shouldBeEmpty()
        }
    }

    @Test
    fun `answer with an acknowledgement that tells only the error case`() {
        val command = requests.createCommand(CmdServDontHandle.getDefaultInstance())

        val ack = post(command)

        Identifier.unpack(ack.messageId) shouldBe command.id
        ack.status.statusCase shouldBe ERROR_CASE
    }

    @Test
    fun `fail the call on an unexpected failure, and record it`() {
        val command = requests.createCommand(Given.CommandMessage.createProject(
            ProjectId.generate()
        ))
        val observer = memoizingObserver<Ack>()

        service.post(command, observer)

        val error = observer.error
        error.shouldBeInstanceOf<StatusRuntimeException>()
        error.status.code shouldBe INTERNAL
        error.errorCase() shouldBe INTERNAL_ERROR
        val failed = monitor.single<CommandPostingFailed>()
        failed.id shouldBe command.id
        failed.error.type shouldBe IllegalStateException::class.java.canonicalName
    }

    @Test
    fun `fail the call for a served command whose message cannot be read, and record it`() {
        val command = withMessage(malformedMessageOf(TypeUrl.of(AggCreateProject::class.java)))
        val observer = memoizingObserver<Ack>()

        service.post(command, observer)

        observer.error.shouldNotBeNull().errorCase() shouldBe INTERNAL_ERROR
        val failed = monitor.single<CommandPostingFailed>()
        failed.id shouldBe command.id
    }

    /**
     * Posts the given command, expecting an acknowledgement with an error case.
     */
    private fun post(command: Command): Ack {
        val observer = memoizingObserver<Ack>()
        service.post(command, observer)
        observer.error shouldBe null
        observer.isCompleted shouldBe true
        return observer.firstResponse()
    }

    /**
     * Creates a packed message of the given type, which bytes cannot be parsed.
     */
    private fun malformedMessageOf(type: TypeUrl): Any =
        Any.newBuilder()
            .setTypeUrl(type.value())
            .setValue(ByteString.copyFrom(byteArrayOf(-1)))
            .build()

    private fun withMessage(message: Any): @NonValidated Command {
        val command = requests.createCommand(CmdServDontHandle.getDefaultInstance())
        return command.toBuilder()
            .setMessage(message)
            .buildPartial()
    }

    /**
     * A filter that fails on every command, as an unexpected failure of the bus would.
     */
    private object FailingFilter : CommandFilter {

        override fun filter(envelope: CommandEnvelope): Optional<Ack> =
            error("The filter fails on purpose.")
    }
}
