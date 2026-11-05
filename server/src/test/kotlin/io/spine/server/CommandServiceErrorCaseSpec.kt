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

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.spine.base.Error
import io.spine.base.error
import io.spine.core.Ack
import io.spine.core.Command
import io.spine.core.ErrorCase.INVALID_REQUEST
import io.spine.core.ErrorCase.REQUEST_DENIED
import io.spine.core.Status.StatusCase.ERROR_CASE
import io.spine.server.bus.BusFilter
import io.spine.server.given.diagnostics.RequestDiagnosticsMonitor
import io.spine.server.type.CommandEnvelope
import io.spine.system.server.RequestDenied
import io.spine.system.server.RequestValidationFailed
import io.spine.test.aggregate.command.AggCreateProject
import io.spine.testing.logging.mute.MuteLogging
import io.spine.validation.NonValidated
import java.util.Optional
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@MuteLogging
@DisplayName("`CommandService` should report errors with `ErrorCase`, and")
internal class CommandServiceErrorCaseSpec : CommandServiceTenancyTest() {

    override fun createContext(): BoundedContext {
        val ctx = BoundedContext.multitenant("Projects")
            .addCommandFilter(DenyingFilter)
            .build()
        ctx.register(Given.ProjectAggregateRepository())
        ctx.register(Given.CustomerAggregateRepository())
        return ctx
    }

    @Test
    fun `answer an invalid command with 'INVALID_REQUEST', keeping the violations`() {
        val monitor = RequestDiagnosticsMonitor.of(context)

        service.post(commandWithoutId(), responseObserver)

        val ack = responseObserver.firstResponse()
        ack.status.statusCase shouldBe ERROR_CASE
        ack.status.errorCase shouldBe INVALID_REQUEST
        ack.text() shouldNotContain "Command ID"

        val diagnostics = monitor.diagnostics
        diagnostics shouldHaveSize 1
        val event = diagnostics[0].shouldBeInstanceOf<RequestValidationFailed>()
        event.violationList.shouldNotBeEmpty()
    }

    @Test
    fun `answer a command denied by a filter with 'REQUEST_DENIED', keeping the error`() {
        val monitor = RequestDiagnosticsMonitor.of(context)

        service.post(Given.ACommand.createProject(), responseObserver)

        val ack = responseObserver.firstResponse()
        ack.status.statusCase shouldBe ERROR_CASE
        ack.status.errorCase shouldBe REQUEST_DENIED
        ack.text() shouldNotContain DENIAL.message

        val diagnostics = monitor.diagnostics
        diagnostics shouldHaveSize 1
        val event = diagnostics[0].shouldBeInstanceOf<RequestDenied>()
        event.error shouldBe DENIAL
        event.filter shouldBe DenyingFilter::class.java.name
    }

    @Test
    fun `post no diagnostic event for an accepted command`() {
        val monitor = RequestDiagnosticsMonitor.of(context)

        service.post(Given.ACommand.createCustomer(), responseObserver)

        responseObserver.firstResponse().status.hasOk() shouldBe true
        monitor.diagnostics.shouldBeEmpty()
    }

    /**
     * A filter that denies the commands to create a project.
     */
    private object DenyingFilter : BusFilter<CommandEnvelope> {

        override fun filter(envelope: CommandEnvelope): Optional<Ack> =
            if (envelope.message() is AggCreateProject) {
                reject(envelope, DENIAL)
            } else {
                letPass()
            }
    }

    private companion object {

        val DENIAL: Error = error {
            type = "spine.test.server.AccessDenied"
            message = "The user cannot create projects."
        }

        /**
         * Creates a command whose ID is not set, which `CommandValidator` reports.
         */
        fun commandWithoutId(): @NonValidated Command = Given.ACommand.createProject()
            .toBuilder()
            .clearId()
            .buildPartial()

        fun Ack.text(): String = toByteString().toStringUtf8()
    }
}
