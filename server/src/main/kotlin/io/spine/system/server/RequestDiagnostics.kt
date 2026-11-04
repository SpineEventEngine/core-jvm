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
import io.spine.client.Query
import io.spine.client.Subscription
import io.spine.client.Topic
import io.spine.core.ActorContext
import io.spine.core.Command
import io.spine.core.CommandValidationError
import io.spine.core.ErrorCase
import io.spine.core.MessageId
import io.spine.protobuf.AnyPacker
import io.spine.system.server.TenantCheckFailed.Reason.REASON_UNSPECIFIED
import io.spine.system.server.TenantCheckFailed.Reason.TENANT_INAPPLICABLE
import io.spine.system.server.TenantCheckFailed.Reason.TENANT_NOT_SPECIFIED
import io.spine.validation.ConstraintViolation
import io.spine.validation.NonValidated
import io.spine.validation.ValidationError

/**
 * Creates the diagnostic events that keep the details of a request the server did not accept.
 *
 * The client receives only an [ErrorCase] for such a request. The event refers to the request
 * by its ID, which the client knows.
 */
internal object RequestDiagnostics {

    /**
     * Creates the event for an error which the server reports with the given error case.
     *
     * @param request The ID and the type of the request.
     * @param actor The actor context of the request.
     * @param error The error that occurred on the server side.
     * @param errorCase The kind of the error, as the client receives it.
     * @param filter The class of the filter which refused the request, if a filter did.
     */
    @JvmStatic
    fun of(
        request: MessageId,
        actor: ActorContext,
        error: Error,
        errorCase: ErrorCase,
        filter: Class<*>?
    ): DiagnosticEvent = when (errorCase) {
        ErrorCase.INVALID_REQUEST -> validationFailed(request, actor, violationsOf(error))
        ErrorCase.UNSUPPORTED_REQUEST ->
            unsupported(request, actor, UnsupportedRequestReceived.Reason.NO_RECEIVER)
        ErrorCase.TENANT_ERROR -> tenantCheckFailed(request, actor, tenantReasonOf(error))
        ErrorCase.UNKNOWN_SUBSCRIPTION -> unknownSubscription(request, actor)
        ErrorCase.REQUEST_DENIED -> denied(request, actor, error, filter)
        ErrorCase.INTERNAL_ERROR,
        ErrorCase.ERROR_CASE_UNSPECIFIED,
        ErrorCase.UNRECOGNIZED -> processingFailed(request, actor, error)
    }

    /**
     * Creates the event for a request that fails validation.
     */
    @JvmStatic
    fun validationFailed(
        request: MessageId,
        actor: ActorContext,
        violations: Iterable<ConstraintViolation>
    ): RequestValidationFailed = RequestValidationFailed.newBuilder()
        .setRequest(request)
        .setActorContext(actor)
        .addAllViolation(violations)
        .build()

    /**
     * Creates the event for a request of a type which the server does not serve.
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
     * Creates the event for a request whose tenant is not accepted.
     */
    @JvmStatic
    fun tenantCheckFailed(
        request: MessageId,
        actor: ActorContext,
        reason: TenantCheckFailed.Reason
    ): TenantCheckFailed = TenantCheckFailed.newBuilder()
        .setRequest(request)
        .setActorContext(actor)
        .setReason(reason)
        .build()

    /**
     * Creates the event for a request to activate or cancel an unknown subscription.
     */
    @JvmStatic
    fun unknownSubscription(
        request: MessageId,
        actor: ActorContext
    ): UnknownSubscriptionRequested = UnknownSubscriptionRequested.newBuilder()
        .setRequest(request)
        .setActorContext(actor)
        .build()

    /**
     * Creates the event for a request refused by a filter of the application.
     */
    @JvmStatic
    fun denied(
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

    /**
     * Creates the event for an unexpected failure that occurred while serving a request.
     */
    @JvmStatic
    fun processingFailed(
        request: MessageId,
        actor: ActorContext,
        error: Error
    ): RequestProcessingFailed = RequestProcessingFailed.newBuilder()
        .setRequest(request)
        .setActorContext(actor)
        .setError(error)
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
    ): RequestProcessingFailed = processingFailed(request, actor, Errors.fromThrowable(failure))

    /**
     * Obtains the ID of the given command and the type URL of its message.
     *
     * The command may be invalid, so the result is not validated.
     */
    @JvmStatic
    fun idOf(command: Command): @NonValidated MessageId =
        messageId(AnyPacker.pack(command.id), command.message.typeUrl)

    /**
     * Obtains the ID of the given query and the type of its target.
     *
     * The query may be invalid, so the result is not validated.
     */
    @JvmStatic
    fun idOf(query: Query): @NonValidated MessageId =
        messageId(AnyPacker.pack(query.id), query.target.type)

    /**
     * Obtains the ID of the given topic and the type of its target.
     *
     * The topic may be invalid, so the result is not validated.
     */
    @JvmStatic
    fun idOf(topic: Topic): @NonValidated MessageId =
        messageId(AnyPacker.pack(topic.id), topic.target.type)

    /**
     * Obtains the ID of the given subscription and the type of the target of its topic.
     *
     * The subscription may be invalid, so the result is not validated.
     */
    @JvmStatic
    fun idOf(subscription: Subscription): @NonValidated MessageId =
        messageId(AnyPacker.pack(subscription.id), subscription.topic.target.type)

    private fun messageId(id: com.google.protobuf.Any, typeUrl: String): @NonValidated MessageId =
        MessageId.newBuilder()
            .setId(id)
            .setTypeUrl(typeUrl)
            .buildPartial()

    private fun violationsOf(error: Error): List<ConstraintViolation> {
        val details = error.details
        return if (details.`is`(ValidationError::class.java)) {
            details.unpack(ValidationError::class.java).constraintViolationList
        } else {
            emptyList()
        }
    }

    private fun tenantReasonOf(error: Error): TenantCheckFailed.Reason =
        when (CommandValidationError.forNumber(error.code)) {
            CommandValidationError.TENANT_UNKNOWN -> TENANT_NOT_SPECIFIED
            CommandValidationError.TENANT_INAPPLICABLE -> TENANT_INAPPLICABLE
            else -> REASON_UNSPECIFIED
        }
}
