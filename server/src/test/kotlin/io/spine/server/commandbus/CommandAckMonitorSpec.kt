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
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.spine.base.Identifier.newUuid
import io.spine.base.error
import io.spine.core.Ack
import io.spine.core.Command
import io.spine.core.ErrorCase.ERROR_CASE_UNSPECIFIED
import io.spine.core.ErrorCase.INVALID_REQUEST
import io.spine.core.Event
import io.spine.core.StatusKt
import io.spine.core.TenantId
import io.spine.core.ack
import io.spine.core.status
import io.spine.core.toError
import io.spine.protobuf.pack
import io.spine.server.bus.causedError
import io.spine.system.server.MemoizingWriteSide
import io.spine.system.server.event.CommandErrored
import io.spine.test.commandbus.ProjectId
import io.spine.test.commandbus.command.CmdBusStartProject
import io.spine.testing.client.TestActorRequestFactory
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Supplements the Java suite `CommandAckMonitorTest` with the specs of error cases.
 */
@DisplayName("`CommandAckMonitor` should")
internal class CommandAckMonitorSpec {

    private val command: Command = TestActorRequestFactory(javaClass).command().create(
        CmdBusStartProject.newBuilder()
            .setProjectId(ProjectId.newBuilder().setId(newUuid()))
            .build()
    )
    private val writeSide = MemoizingWriteSide.singleTenant()
    private val monitor = CommandAckMonitor.newBuilder()
        .setSystemWriteSide(writeSide)
        .setTenantId(TenantId.getDefaultInstance())
        .setPostedCommands(ImmutableSet.of(command))
        .build()

    @Test
    fun `post 'CommandErrored' without an error case for an error the poster receives`() {
        val error = error {
            type = CommandAckMonitorSpec::class.java.canonicalName
            message = "A test error."
        }

        monitor.onNext(command.id().causedError(error))

        val event = lastErrored()
        event.error shouldBe error
        event.errorCase shouldBe ERROR_CASE_UNSPECIFIED
    }

    @Test
    fun `post 'CommandErrored' with an error made of the error case of an acknowledgement`() {
        monitor.onNext(ackWith { errorCase = INVALID_REQUEST })

        val event = lastErrored()
        event.id shouldBe command.id()
        event.error shouldBe INVALID_REQUEST.toError()
        event.errorCase shouldBe INVALID_REQUEST
    }

    @Test
    // Reason: builds an ack with the deprecated `Status.rejection`, until the field is removed.
    @Suppress("DEPRECATION")
    fun `refuse an acknowledgement with a rejection`() {
        val ack = ackWith { rejection = Event.getDefaultInstance() }

        shouldThrow<IllegalArgumentException> {
            monitor.onNext(ack)
        }
    }

    private fun ackWith(configure: StatusKt.Dsl.() -> Unit): Ack {
        val id = command.id()
        val ackStatus = status(configure)
        return ack {
            messageId = id.pack()
            status = ackStatus
        }
    }

    private fun lastErrored(): CommandErrored =
        writeSide.lastSeenEvent()
            .message()
            .shouldBeInstanceOf<CommandErrored>()
}
