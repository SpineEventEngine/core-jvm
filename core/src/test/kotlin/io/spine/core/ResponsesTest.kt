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

package io.spine.core

import com.google.common.truth.extensions.proto.ProtoTruth
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.spine.base.Error
import io.spine.core.Responses.ok
import io.spine.testing.UtilityClassTest
import io.spine.validation.NonValidated
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`Responses` utility should")
internal class ResponsesTest : UtilityClassTest<Responses>(Responses::class.java) {

    @Test
    fun `return OK response`() {
        ProtoTruth.assertThat(ok())
            .isNotEqualToDefaultInstance()
    }

    @Test
    fun `recognize OK response`() {
        val ok = ok()
        ok.isOk shouldBe true
        ok.isError shouldBe false
    }

    /**
     * This test uses partially built values even though the [Status]
     * does not set validation on the [Status.error][Status.getError] field.
     * This is so because the `base` subproject is built without using the Validation SDK.
     *
     * We want to make it future-compatible, should we decide validating
     * the types provided by `base`.
     */
    @Test
    fun `recognize not OK response`() {
        val asIfError: @NonValidated Status = Status.newBuilder()
            .setError(Error.getDefaultInstance())
            .buildPartial()

        val error: @NonValidated Response = Response.newBuilder()
            .setStatus(asIfError)
            .buildPartial()

        error.isOk shouldBe false
        error.isError shouldBe true
    }

    @Test
    fun `create a status with an error case`() {
        val status = Responses.errorWith(ErrorCase.INVALID_REQUEST)

        status.errorCase shouldBe ErrorCase.INVALID_REQUEST
    }

    @Test
    fun `not create a status with an unspecified error case`() {
        shouldThrow<IllegalArgumentException> {
            Responses.errorWith(ErrorCase.ERROR_CASE_UNSPECIFIED)
        }
        shouldThrow<IllegalArgumentException> {
            Responses.errorWith(ErrorCase.UNRECOGNIZED)
        }
    }
}
