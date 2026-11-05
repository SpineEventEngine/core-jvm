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

package io.spine.grpc

import com.google.rpc.Code
import io.grpc.Status.INTERNAL
import io.grpc.protobuf.StatusProto
import io.kotest.matchers.shouldBe
import io.spine.core.ErrorCase
import io.spine.core.ErrorCase.TENANT_ERROR
import io.spine.core.Responses
import io.spine.protobuf.AnyPacker.pack
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Supplements the Java suite `StreamObserversTest` with the specs of reading an error case.
 */
@DisplayName("`StreamObservers` should")
internal class StreamObserversSpec {

    @Test
    fun `obtain the error case of a failed call`() {
        val failure = failureWith(TENANT_ERROR)

        StreamObservers.errorCaseOf(failure).orElse(null) shouldBe TENANT_ERROR
    }

    @Test
    fun `obtain the error case of a failure wrapped into another exception`() {
        val failure = IllegalStateException(failureWith(TENANT_ERROR))

        StreamObservers.errorCaseOf(failure).orElse(null) shouldBe TENANT_ERROR
    }

    @Test
    fun `return empty for a failure whose status tells no error case`() {
        val failure = StatusProto.toStatusRuntimeException(
            com.google.rpc.Status.newBuilder()
                .setCode(Code.INTERNAL_VALUE)
                .addDetails(pack(Responses.statusOk()))
                .build()
        )

        StreamObservers.errorCaseOf(failure).isPresent shouldBe false
    }

    @Test
    fun `return empty for a failure without the details`() {
        StreamObservers.errorCaseOf(INTERNAL.asRuntimeException()).isPresent shouldBe false
    }

    @Test
    fun `return empty for a throwable which is not a failure of a call`() {
        StreamObservers.errorCaseOf(IllegalStateException("Not a call.")).isPresent shouldBe false
    }

    private companion object {

        /**
         * Creates an exception like the one the server fails a call with.
         */
        fun failureWith(errorCase: ErrorCase) = StatusProto.toStatusRuntimeException(
            com.google.rpc.Status.newBuilder()
                .setCode(Code.INVALID_ARGUMENT_VALUE)
                .addDetails(pack(Responses.errorWith(errorCase)))
                .build()
        )
    }
}
