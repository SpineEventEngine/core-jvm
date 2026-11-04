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
import io.spine.client.QueryValidationError
import io.spine.client.SubscriptionValidationError
import io.spine.client.TopicValidationError
import io.spine.core.CommandValidationError
import io.spine.core.ErrorCase
import io.spine.core.ErrorCase.INTERNAL_ERROR
import io.spine.core.ErrorCase.INVALID_REQUEST
import io.spine.core.ErrorCase.REQUEST_DENIED
import io.spine.core.ErrorCase.TENANT_ERROR
import io.spine.core.ErrorCase.UNKNOWN_SUBSCRIPTION
import io.spine.core.ErrorCase.UNSUPPORTED_REQUEST
import io.spine.core.EventValidationError

/**
 * Tells which [ErrorCase] a client receives for an [Error] that occurred on the server side.
 *
 * The errors of the framework have the full name of a Protobuf enum in [Error.getType] and
 * the number of its value in [Error.getCode]. An error of any other type is given by a filter
 * of the application, and the client receives [REQUEST_DENIED] for it.
 */
internal object ErrorCases {

    private val commandErrors = CommandValidationError.getDescriptor().fullName
    private val eventErrors = EventValidationError.getDescriptor().fullName
    private val queryErrors = QueryValidationError.getDescriptor().fullName
    private val topicErrors = TopicValidationError.getDescriptor().fullName
    private val subscriptionErrors = SubscriptionValidationError.getDescriptor().fullName

    /**
     * Obtains the error case which the client receives for the given error.
     */
    @JvmStatic
    fun of(error: Error): ErrorCase {
        val code = error.code
        return when (error.type) {
            commandErrors -> ofCommand(CommandValidationError.forNumber(code))
            eventErrors -> ofEvent(EventValidationError.forNumber(code))
            queryErrors -> ofQuery(QueryValidationError.forNumber(code))
            topicErrors -> ofTopic(TopicValidationError.forNumber(code))
            subscriptionErrors -> ofSubscription(SubscriptionValidationError.forNumber(code))
            else -> REQUEST_DENIED
        }
    }

    private fun ofCommand(error: CommandValidationError?): ErrorCase = when (error) {
        CommandValidationError.UNSUPPORTED_COMMAND -> UNSUPPORTED_REQUEST
        CommandValidationError.INVALID_COMMAND,
        CommandValidationError.EXPIRED,
        CommandValidationError.DUPLICATE_COMMAND -> INVALID_REQUEST
        CommandValidationError.TENANT_UNKNOWN,
        CommandValidationError.TENANT_INAPPLICABLE -> TENANT_ERROR
        CommandValidationError.UNKNOWN,
        CommandValidationError.UNRECOGNIZED,
        null -> INTERNAL_ERROR
    }

    private fun ofEvent(error: EventValidationError?): ErrorCase = when (error) {
        EventValidationError.UNSUPPORTED_EVENT -> UNSUPPORTED_REQUEST
        EventValidationError.INVALID_EVENT,
        EventValidationError.DUPLICATE_EVENT -> INVALID_REQUEST
        EventValidationError.UNKNOWN_REASON,
        EventValidationError.UNRECOGNIZED,
        null -> INTERNAL_ERROR
    }

    private fun ofQuery(error: QueryValidationError?): ErrorCase = when (error) {
        QueryValidationError.UNSUPPORTED_QUERY_TARGET -> UNSUPPORTED_REQUEST
        QueryValidationError.INVALID_QUERY -> INVALID_REQUEST
        QueryValidationError.QVE_UNKNOWN_REASON,
        QueryValidationError.UNRECOGNIZED,
        null -> INTERNAL_ERROR
    }

    private fun ofTopic(error: TopicValidationError?): ErrorCase = when (error) {
        TopicValidationError.UNSUPPORTED_TOPIC_TARGET -> UNSUPPORTED_REQUEST
        TopicValidationError.INVALID_TOPIC -> INVALID_REQUEST
        TopicValidationError.TVE_UNKNOWN_REASON,
        TopicValidationError.UNRECOGNIZED,
        null -> INTERNAL_ERROR
    }

    private fun ofSubscription(error: SubscriptionValidationError?): ErrorCase = when (error) {
        SubscriptionValidationError.UNSUPPORTED_SUBSCRIPTION_TARGET -> UNSUPPORTED_REQUEST
        SubscriptionValidationError.INVALID_SUBSCRIPTION -> INVALID_REQUEST
        SubscriptionValidationError.UNKNOWN_SUBSCRIPTION -> UNKNOWN_SUBSCRIPTION
        SubscriptionValidationError.SVE_UNKNOWN_REASON,
        SubscriptionValidationError.UNRECOGNIZED,
        null -> INTERNAL_ERROR
    }
}
