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

package io.spine.server.transport

import io.grpc.Status.Code
import io.grpc.Status.Code.INTERNAL
import io.grpc.Status.Code.INVALID_ARGUMENT
import io.grpc.Status.Code.PERMISSION_DENIED
import io.grpc.protobuf.StatusProto
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.spine.core.ErrorCase
import io.spine.core.ErrorCase.INTERNAL_ERROR
import io.spine.core.ErrorCase.INVALID_REQUEST
import io.spine.core.ErrorCase.REQUEST_DENIED
import io.spine.core.ErrorCase.TENANT_ERROR
import io.spine.core.ErrorCase.UNSUPPORTED_REQUEST
import io.spine.core.Responses.errorWith
import io.spine.protobuf.pack
import io.spine.testing.UtilityClassTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource

@DisplayName("`Statuses` should")
internal class StatusesSpec : UtilityClassTest<Statuses>(Statuses::class.java) {

    @ParameterizedTest
    @MethodSource("codes")
    fun `fail a call with the code of the error case`(errorCase: ErrorCase, code: Code) {
        Statuses.failure(errorCase).status.code shouldBe code
    }

    @ParameterizedTest
    @EnumSource(ErrorCase::class, names = ["ERROR_CASE_UNSPECIFIED", "UNRECOGNIZED"])
    fun `report an unspecified error case as the internal error`(errorCase: ErrorCase) {
        val failure = Statuses.failure(errorCase)

        failure.status.code shouldBe INTERNAL
        StatusProto.fromThrowable(failure).shouldNotBeNull().detailsList shouldBe
                listOf(errorWith(INTERNAL_ERROR).pack())
    }

    @Test
    fun `tell only the error case`() {
        val failure = Statuses.failure(UNSUPPORTED_REQUEST)

        failure.status.description.isNullOrEmpty() shouldBe true
        failure.cause shouldBe null
        val status = StatusProto.fromThrowable(failure).shouldNotBeNull()
        status.message shouldBe ""
        status.detailsList shouldBe listOf(errorWith(UNSUPPORTED_REQUEST).pack())
    }

    companion object {

        @JvmStatic
        @Suppress("unused") // Called by JUnit.
        fun codes() = listOf<Arguments>(
            arguments(INVALID_REQUEST, INVALID_ARGUMENT),
            arguments(UNSUPPORTED_REQUEST, INVALID_ARGUMENT),
            arguments(TENANT_ERROR, INVALID_ARGUMENT),
            arguments(REQUEST_DENIED, PERMISSION_DENIED),
            arguments(INTERNAL_ERROR, INTERNAL),
        )
    }
}
