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

import io.grpc.StatusRuntimeException
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.spine.core.Ack
import io.spine.core.ErrorCase.INTERNAL_ERROR
import io.spine.grpc.StreamObservers
import io.spine.server.bus.BusFilter
import io.spine.server.type.CommandEnvelope
import io.spine.testing.logging.mute.MuteLogging
import java.util.Optional
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@MuteLogging
@DisplayName("`CommandService` should")
internal class CommandServiceFailureSpec : CommandServiceTenancyTest() {

    override fun createContext(): BoundedContext {
        val ctx = BoundedContext.multitenant("Projects")
            .addCommandFilter(FailingFilter)
            .build()
        ctx.register(Given.ProjectAggregateRepository())
        return ctx
    }

    @Test
    fun `fail the call with 'INTERNAL_ERROR' and nothing else on an unexpected failure`() {
        service.post(Given.ACommand.createProject(), responseObserver)

        responseObserver.responses().shouldBeEmpty()
        val error = responseObserver.error.shouldBeInstanceOf<StatusRuntimeException>()
        StreamObservers.errorCaseOf(error).orElse(null) shouldBe INTERNAL_ERROR
        error.message.orEmpty() shouldNotContain FAILURE
    }

    /**
     * A filter that fails on every command.
     */
    private object FailingFilter : BusFilter<CommandEnvelope> {

        override fun filter(envelope: CommandEnvelope): Optional<Ack> =
            throw IllegalStateException(FAILURE)
    }

    private companion object {
        const val FAILURE = "The storage of filters is unavailable."
    }
}
