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
import io.grpc.protobuf.StatusProto
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.spine.core.ErrorCase
import io.spine.core.ErrorCase.ERROR_CASE_UNSPECIFIED
import io.spine.core.ErrorCase.INTERNAL_ERROR
import io.spine.core.ErrorCase.INVALID_REQUEST
import io.spine.core.ErrorCase.REQUEST_DENIED
import io.spine.core.ErrorCase.TENANT_ERROR
import io.spine.core.ErrorCase.UNRECOGNIZED
import io.spine.core.ErrorCase.UNSUPPORTED_REQUEST
import io.spine.core.Status
import io.spine.protobuf.AnyPacker.unpack
import io.spine.testing.UtilityClassTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

@DisplayName("`Statuses` should")
internal class StatusesSpec : UtilityClassTest<Statuses>(Statuses::class.java) {

    @ParameterizedTest
    @EnumSource(value = ErrorCase::class, names = ["ERROR_CASE_UNSPECIFIED", "UNRECOGNIZED"],
        mode = EnumSource.Mode.EXCLUDE)
    fun `fail with the code of the error case and no description`(errorCase: ErrorCase) {
        val failure = Statuses.failure(errorCase)

        failure.status.code shouldBe Statuses.codeOf(errorCase)
        failure.status.description.isNullOrEmpty() shouldBe true
    }

    @ParameterizedTest
    @EnumSource(value = ErrorCase::class, names = ["ERROR_CASE_UNSPECIFIED", "UNRECOGNIZED"],
        mode = EnumSource.Mode.EXCLUDE)
    fun `carry only the error case in the status details`(errorCase: ErrorCase) {
        val details = StatusProto.fromThrowable(Statuses.failure(errorCase)).shouldNotBeNull()

        details.message shouldBe ""
        details.detailsList.map { unpack(it, Status::class.java).errorCase } shouldContainExactly
                listOf(errorCase)
    }

    @Test
    fun `report an unspecified error case as an internal error`() {
        val details = StatusProto.fromThrowable(Statuses.failure(ERROR_CASE_UNSPECIFIED))
            .shouldNotBeNull()

        details.code shouldBe Code.INTERNAL.value()
        unpack(details.getDetails(0), Status::class.java).errorCase shouldBe INTERNAL_ERROR
    }

    @Test
    fun `report an unrecognized error case as an internal error`() {
        val details = StatusProto.fromThrowable(Statuses.failure(UNRECOGNIZED)).shouldNotBeNull()

        unpack(details.getDetails(0), Status::class.java).errorCase shouldBe INTERNAL_ERROR
    }

    @Test
    fun `not send the trailer with the server-side error`() {
        val trailers = Statuses.failure(INVALID_REQUEST).trailers.shouldNotBeNull()

        trailers.keys().contains("spine-error-bin") shouldBe false
    }

    @Test
    fun `give request errors the same code, so that the code tells no more than the case`() {
        listOf(INVALID_REQUEST, UNSUPPORTED_REQUEST, TENANT_ERROR)
            .map(Statuses::codeOf)
            .distinct() shouldContainExactly listOf(Code.INVALID_ARGUMENT)
    }

    @Test
    fun `map other error cases to their gRPC codes`() {
        Statuses.codeOf(REQUEST_DENIED) shouldBe Code.PERMISSION_DENIED
        Statuses.codeOf(INTERNAL_ERROR) shouldBe Code.INTERNAL
        Statuses.codeOf(UNRECOGNIZED) shouldBe Code.INTERNAL
    }
}
