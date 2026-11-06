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

import com.google.protobuf.Message
import com.google.protobuf.StringValue
import com.google.rpc.Status
import io.grpc.Status.Code.INVALID_ARGUMENT
import io.grpc.protobuf.StatusProto
import io.kotest.matchers.optional.shouldBeEmpty
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.spine.core.ErrorCase.UNSUPPORTED_REQUEST
import io.spine.core.Responses
import io.spine.core.Responses.errorWith
import io.spine.grpc.StreamObservers.errorCaseOf
import io.spine.protobuf.pack
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Supplements the Java suite `StreamObserversTest` with the specs of
 * [StreamObservers.errorCaseOf].
 */
@DisplayName("`StreamObservers` should")
internal class StreamObserversSpec {

    @Nested inner class
    `obtain the error case from` {

        @Test
        fun `a 'StatusRuntimeException'`() {
            val status = statusWith(errorWith(UNSUPPORTED_REQUEST))
            val failure = StatusProto.toStatusRuntimeException(status)

            errorCaseOf(failure).shouldBePresent() shouldBe UNSUPPORTED_REQUEST
        }

        @Test
        fun `a 'StatusException'`() {
            val status = statusWith(errorWith(UNSUPPORTED_REQUEST))
            val failure = StatusProto.toStatusException(status)

            errorCaseOf(failure).shouldBePresent() shouldBe UNSUPPORTED_REQUEST
        }
    }

    @Nested inner class
    `find no error case in` {

        @Test
        fun `an exception that carries no status`() {
            errorCaseOf(IllegalStateException("Not a status.")).shouldBeEmpty()
        }

        @Test
        fun `a status without details`() {
            errorCaseOf(io.grpc.Status.INTERNAL.asRuntimeException()).shouldBeEmpty()
        }

        @Test
        fun `the details without a Spine status`() {
            val failure = StatusProto.toStatusRuntimeException(
                statusWith(StringValue.of("Not a Spine status."))
            )

            errorCaseOf(failure).shouldBeEmpty()
        }

        @Test
        fun `a Spine status without an error case`() {
            val failure = StatusProto.toStatusRuntimeException(statusWith(Responses.statusOk()))

            errorCaseOf(failure).shouldBeEmpty()
        }
    }

    private fun statusWith(details: Message): Status =
        Status.newBuilder()
            .setCode(INVALID_ARGUMENT.value())
            .addDetails(details.pack())
            .build()
}
