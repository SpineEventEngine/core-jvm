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

import io.spine.base.Error
import io.spine.base.Errors
import io.spine.client.SubscriptionValidationError
import io.spine.core.ActorContext
import io.spine.core.CommandValidationError
import io.spine.core.ErrorCase
import io.spine.core.MessageId
import io.spine.system.server.TenantCheckFailed.Reason.REASON_UNSPECIFIED
import io.spine.system.server.TenantCheckFailed.Reason.TENANT_INAPPLICABLE
import io.spine.system.server.TenantCheckFailed.Reason.TENANT_NOT_SPECIFIED
import io.spine.system.server.UnsupportedRequestReceived.Reason.NO_RECEIVER
import io.spine.validation.ValidationError

/**
 * Creates the diagnostic events that keep the details of a request the server did not accept.
 *
 * The client receives only an [ErrorCase] for such a request. The event refers to the request
 * by its ID, which the client knows.
 */
internal object RequestDiagnostics {

    private val commandErrors = CommandValidationError.getDescriptor().fullName
    private val subscriptionErrors = SubscriptionValidationError.getDescriptor().fullName

    /**
     * Creates the event for an error that the server reports with the given error case.
     *
     * @param request The ID and the type of the request.
     * @param actor The actor context of the request.
     * @param error The error that occurred on the server side.
     * @param errorCase The kind of the error, as the client receives it.
     * @param filter The class of the filter that refused the request, if a filter did.
     */
    @JvmStatic
    @JvmOverloads
    fun of(
        request: MessageId,
        actor: ActorContext,
        error: Error,
        errorCase: ErrorCase,
        filter: Class<*>? = null
    ): DiagnosticEvent = when (errorCase) {
        ErrorCase.INVALID_REQUEST ->
            validationFailed(request = request, actor = actor, error = error)
        ErrorCase.UNSUPPORTED_REQUEST ->
            notServed(request = request, actor = actor, error = error)
        ErrorCase.TENANT_ERROR ->
            tenantCheckFailed(request = request, actor = actor, reason = tenantReasonOf(error))
        ErrorCase.REQUEST_DENIED ->
            denied(request = request, actor = actor, error = error, filter = filter)
        ErrorCase.INTERNAL_ERROR,
        ErrorCase.ERROR_CASE_UNSPECIFIED,
        ErrorCase.UNRECOGNIZED -> processingFailed(request = request, actor = actor, error = error)
    }

    /**
     * Creates the event for a request of a type that the server does not serve.
     */
    @JvmStatic
    fun unsupported(
        request: MessageId,
        actor: ActorContext,
        reason: UnsupportedRequestReceived.Reason
    ): UnsupportedRequestReceived = UnsupportedRequestReceived.newBuilder()
        .setRequest(request)
        .setActorContext(actor)
        .setReason(reason)
        .build()

    /**
     * Creates the event for an unexpected failure that occurred while serving a request.
     *
     * The error of the event has the stack trace of the given throwable.
     */
    @JvmStatic
    fun processingFailed(
        request: MessageId,
        actor: ActorContext,
        failure: Throwable
    ): RequestProcessingFailed = processingFailed(
        request = request,
        actor = actor,
        error = Errors.fromThrowable(failure)
    )

    /**
     * Creates the event for a request that fails validation.
     *
     * The violations in the details of the error go to the event, and the error keeps
     * neither the details nor the message, which repeats them.
     */
    private fun validationFailed(
        request: MessageId,
        actor: ActorContext,
        error: Error
    ): RequestValidationFailed {
        val details = error.details
        val builder = RequestValidationFailed.newBuilder()
            .setRequest(request)
            .setActorContext(actor)
        if (details.`is`(ValidationError::class.java)) {
            val violations = details.unpack(ValidationError::class.java).constraintViolationList
            builder.addAllViolation(violations)
                .setError(error.toBuilder().clearDetails().clearMessage())
        } else {
            builder.setError(error)
        }
        return builder.build()
    }

    private fun tenantCheckFailed(
        request: MessageId,
        actor: ActorContext,
        reason: TenantCheckFailed.Reason
    ): TenantCheckFailed = TenantCheckFailed.newBuilder()
        .setRequest(request)
        .setActorContext(actor)
        .setReason(reason)
        .build()

    /**
     * Creates the event for a request that the server does not serve.
     *
     * The client receives the same error case for an unknown subscription and for a type
     * that is not served. The event tells them apart.
     */
    private fun notServed(
        request: MessageId,
        actor: ActorContext,
        error: Error
    ): DiagnosticEvent {
        val unknownSubscription = error.type == subscriptionErrors
                && error.code == SubscriptionValidationError.UNKNOWN_SUBSCRIPTION.number
        return if (unknownSubscription) {
            unknownSubscription(request, actor)
        } else {
            unsupported(request = request, actor = actor, reason = NO_RECEIVER)
        }
    }

    private fun unknownSubscription(
        request: MessageId,
        actor: ActorContext
    ): UnknownSubscriptionRequested = UnknownSubscriptionRequested.newBuilder()
        .setRequest(request)
        .setActorContext(actor)
        .build()

    private fun denied(
        request: MessageId,
        actor: ActorContext,
        error: Error,
        filter: Class<*>?
    ): RequestDenied = RequestDenied.newBuilder()
        .setRequest(request)
        .setActorContext(actor)
        .setError(error)
        .setFilter(filter?.name ?: "")
        .build()

    private fun processingFailed(
        request: MessageId,
        actor: ActorContext,
        error: Error
    ): RequestProcessingFailed = RequestProcessingFailed.newBuilder()
        .setRequest(request)
        .setActorContext(actor)
        .setError(error)
        .build()

    private fun tenantReasonOf(error: Error): TenantCheckFailed.Reason {
        if (error.type != commandErrors) {
            return REASON_UNSPECIFIED
        }
        return when (CommandValidationError.forNumber(error.code)) {
            CommandValidationError.TENANT_UNKNOWN -> TENANT_NOT_SPECIFIED
            CommandValidationError.TENANT_INAPPLICABLE -> TENANT_INAPPLICABLE
            else -> REASON_UNSPECIFIED
        }
    }
}
