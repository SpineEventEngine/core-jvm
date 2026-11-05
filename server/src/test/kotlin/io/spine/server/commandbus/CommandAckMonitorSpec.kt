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
import io.spine.base.Identifier.newUuid
import io.spine.base.error
import io.spine.core.CommandValidationError
import io.spine.core.TenantId
import io.spine.server.bus.causedError
import io.spine.system.server.MemoizingWriteSide
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

    @Test
    @Suppress("DEPRECATION") // Reason: covers an ack carrying the deprecated `Status.error`.
    fun `refuse an ack with the error that the bus did not convert to an error case`() {
        val command = TestActorRequestFactory(javaClass).command().create(
            CmdBusStartProject.newBuilder()
                .setProjectId(ProjectId.newBuilder().setId(newUuid()))
                .build()
        )
        val monitor = CommandAckMonitor.newBuilder()
            .setSystemWriteSide(MemoizingWriteSide.singleTenant())
            .setTenantId(TenantId.getDefaultInstance())
            .setPostedCommands(ImmutableSet.of(command))
            .build()
        val error = error {
            type = CommandValidationError.getDescriptor().fullName
            code = CommandValidationError.INVALID_COMMAND.number
        }

        shouldThrow<IllegalArgumentException> {
            monitor.onNext(command.id().causedError(error))
        }
    }
}
