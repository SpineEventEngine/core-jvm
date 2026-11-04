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

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.spine.base.Error
import io.spine.base.error
import io.spine.client.Query
import io.spine.client.QueryId
import io.spine.core.ActorContext
import io.spine.core.CommandValidationError
import io.spine.core.ErrorCase
import io.spine.core.MessageId
import io.spine.protobuf.AnyPacker
import io.spine.system.server.TenantCheckFailed.Reason.TENANT_INAPPLICABLE
import io.spine.system.server.TenantCheckFailed.Reason.TENANT_NOT_SPECIFIED
import io.spine.system.server.UnsupportedRequestReceived.Reason.NO_RECEIVER
import io.spine.validation.ConstraintViolation
import io.spine.validation.NonValidated
import io.spine.validation.ValidationError
import io.spine.validation.constraintViolation
import io.spine.validation.validationError
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`RequestDiagnostics` should")
internal class RequestDiagnosticsSpec {

    @Test
    fun `keep the violations of an invalid request`() {
        val error = frameworkError(CommandValidationError.INVALID_COMMAND, violations)

        val event = diagnosticOf(error, ErrorCase.INVALID_REQUEST)

        event.shouldBeInstanceOf<RequestValidationFailed>()
        event.request shouldBe request
        event.actorContext shouldBe actor
        event.violationList shouldContainExactly violations.constraintViolationList
    }

    @Test
    fun `tell that a request has no receiver`() {
        val error = frameworkError(CommandValidationError.UNSUPPORTED_COMMAND)

        val event = diagnosticOf(error, ErrorCase.UNSUPPORTED_REQUEST)

        event.shouldBeInstanceOf<UnsupportedRequestReceived>()
        event.reason shouldBe NO_RECEIVER
    }

    @Test
    fun `tell why the tenant of a command is not accepted`() {
        val missing = diagnosticOf(
            frameworkError(CommandValidationError.TENANT_UNKNOWN), ErrorCase.TENANT_ERROR
        )
        val inapplicable = diagnosticOf(
            frameworkError(CommandValidationError.TENANT_INAPPLICABLE), ErrorCase.TENANT_ERROR
        )

        missing.shouldBeInstanceOf<TenantCheckFailed>().reason shouldBe TENANT_NOT_SPECIFIED
        inapplicable.shouldBeInstanceOf<TenantCheckFailed>().reason shouldBe TENANT_INAPPLICABLE
    }

    @Test
    fun `keep the error and the filter of a denied request`() {
        val error = error {
            type = "com.example.AccessDenied"
            message = "The user has no access to the project."
        }

        val event = RequestDiagnostics.of(
            request, actor, error, ErrorCase.REQUEST_DENIED, RequestDiagnosticsSpec::class.java
        )

        event.shouldBeInstanceOf<RequestDenied>()
        event.error shouldBe error
        event.filter shouldBe RequestDiagnosticsSpec::class.java.name
    }

    @Test
    fun `tell that a subscription is unknown`() {
        val error = frameworkError(CommandValidationError.UNKNOWN)

        val event = diagnosticOf(error, ErrorCase.UNKNOWN_SUBSCRIPTION)

        event.shouldBeInstanceOf<UnknownSubscriptionRequested>()
        event.request shouldBe request
    }

    @Test
    fun `keep the error of an unexpected failure`() {
        val error = frameworkError(CommandValidationError.UNKNOWN)

        val event = diagnosticOf(error, ErrorCase.INTERNAL_ERROR)

        event.shouldBeInstanceOf<RequestProcessingFailed>()
        event.error shouldBe error
    }

    @Test
    fun `keep the stack trace of an unexpected failure`() {
        val failure = IllegalStateException("The storage is unavailable.")

        val event = RequestDiagnostics.processingFailed(request, actor, failure)

        event.error.stacktrace shouldContain RequestDiagnosticsSpec::class.java.name
    }

    @Test
    fun `obtain the ID of an invalid query without failing`() {
        val query = queryWithoutTarget()

        val id = RequestDiagnostics.idOf(query)

        AnyPacker.unpack(id.id, QueryId::class.java) shouldBe query.id
        id.typeUrl shouldBe ""
    }

    private fun diagnosticOf(error: Error, errorCase: ErrorCase): DiagnosticEvent =
        RequestDiagnostics.of(request, actor, error, errorCase, filter = null)

    private companion object {

        val actor: ActorContext = ActorContext.getDefaultInstance()

        val request: MessageId = requestId()

        val violation: ConstraintViolation = constraintViolation {
            typeName = "spine.test.Project"
        }

        val violations: ValidationError = validationError {
            constraintViolation += violation
        }

        fun frameworkError(
            code: CommandValidationError,
            details: ValidationError? = null
        ): Error = error {
            type = CommandValidationError.getDescriptor().fullName
            this.code = code.number
            if (details != null) {
                this.details = AnyPacker.pack(details)
            }
        }

        fun requestId(): @NonValidated MessageId = MessageId.newBuilder()
            .setTypeUrl("type.spine.io/spine.test.CreateProject")
            .buildPartial()

        fun queryWithoutTarget(): @NonValidated Query = Query.newBuilder()
            .setId(QueryId.newBuilder().setValue("q-query-1"))
            .buildPartial()
    }
}
