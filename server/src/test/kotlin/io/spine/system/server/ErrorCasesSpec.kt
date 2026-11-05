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

import com.google.protobuf.ProtocolMessageEnum
import io.kotest.matchers.shouldBe
import io.spine.base.Error
import io.spine.base.error
import io.spine.client.QueryValidationError
import io.spine.client.SubscriptionValidationError
import io.spine.client.TopicValidationError
import io.spine.core.CommandValidationError
import io.spine.core.ErrorCase
import io.spine.core.ErrorCase.INTERNAL_ERROR
import io.spine.core.ErrorCase.INVALID_REQUEST
import io.spine.core.ErrorCase.REQUEST_DENIED
import io.spine.core.ErrorCase.TENANT_ERROR
import io.spine.core.ErrorCase.UNSUPPORTED_REQUEST
import io.spine.core.EventValidationError
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`ErrorCases` should")
internal class ErrorCasesSpec {

    @Test
    fun `tell the error case of a command validation error`() {
        assertCases(
            CommandValidationError.UNSUPPORTED_COMMAND to UNSUPPORTED_REQUEST,
            CommandValidationError.INVALID_COMMAND to INVALID_REQUEST,
            CommandValidationError.EXPIRED to INVALID_REQUEST,
            CommandValidationError.DUPLICATE_COMMAND to INVALID_REQUEST,
            CommandValidationError.TENANT_UNKNOWN to TENANT_ERROR,
            CommandValidationError.TENANT_INAPPLICABLE to TENANT_ERROR,
            CommandValidationError.UNKNOWN to INTERNAL_ERROR,
        )
    }

    @Test
    fun `tell the error case of an event validation error`() {
        assertCases(
            EventValidationError.UNSUPPORTED_EVENT to UNSUPPORTED_REQUEST,
            EventValidationError.INVALID_EVENT to INVALID_REQUEST,
            EventValidationError.DUPLICATE_EVENT to INVALID_REQUEST,
            EventValidationError.UNKNOWN_REASON to INTERNAL_ERROR,
        )
    }

    @Test
    fun `tell the error case of a query validation error`() {
        assertCases(
            QueryValidationError.UNSUPPORTED_QUERY_TARGET to UNSUPPORTED_REQUEST,
            QueryValidationError.INVALID_QUERY to INVALID_REQUEST,
            QueryValidationError.QVE_UNKNOWN_REASON to INTERNAL_ERROR,
        )
    }

    @Test
    fun `tell the error case of a topic validation error`() {
        assertCases(
            TopicValidationError.UNSUPPORTED_TOPIC_TARGET to UNSUPPORTED_REQUEST,
            TopicValidationError.INVALID_TOPIC to INVALID_REQUEST,
            TopicValidationError.TVE_UNKNOWN_REASON to INTERNAL_ERROR,
        )
    }

    @Test
    fun `tell the error case of a subscription validation error`() {
        assertCases(
            SubscriptionValidationError.UNSUPPORTED_SUBSCRIPTION_TARGET to UNSUPPORTED_REQUEST,
            SubscriptionValidationError.INVALID_SUBSCRIPTION to INVALID_REQUEST,
            SubscriptionValidationError.UNKNOWN_SUBSCRIPTION to UNSUPPORTED_REQUEST,
            SubscriptionValidationError.SVE_UNKNOWN_REASON to INTERNAL_ERROR,
        )
    }

    @Test
    fun `report an unknown code of a framework error as an internal error`() {
        val error = error {
            type = CommandValidationError.getDescriptor().fullName
            code = UNKNOWN_CODE
        }

        ErrorCases.of(error) shouldBe INTERNAL_ERROR
    }

    @Test
    fun `report an error of an application filter as a denied request`() {
        val error = error {
            type = "com.example.AccessDenied"
            code = 1
            message = "The user has no access to the project."
        }

        ErrorCases.of(error) shouldBe REQUEST_DENIED
    }

    private fun assertCases(vararg cases: Pair<ProtocolMessageEnum, ErrorCase>) {
        cases.forEach { (code, expected) ->
            ErrorCases.of(errorOf(code)) shouldBe expected
        }
    }

    private companion object {

        const val UNKNOWN_CODE = 1000

        fun errorOf(code: ProtocolMessageEnum): Error = error {
            type = code.descriptorForType.fullName
            this.code = code.number
        }
    }
}
