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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.spine.base.Error
import io.spine.core.ErrorCase.ERROR_CASE_UNSPECIFIED
import io.spine.core.ErrorCase.REQUEST_DENIED
import io.spine.core.ErrorCase.UNRECOGNIZED
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`ErrorCases` should")
internal class ErrorCasesSpec {

    @Test
    fun `create an error typed after the enum, with the number and the name of the case`() {
        val error = REQUEST_DENIED.toError()

        error.type shouldBe "spine.core.ErrorCase"
        error.code shouldBe REQUEST_DENIED.number
        error.message shouldBe "REQUEST_DENIED"
    }

    @Test
    fun `tell nothing more than the error case`() {
        val error = REQUEST_DENIED.toError()

        error.hasDetails() shouldBe false
        error.attributesCount shouldBe 0
        error.stacktrace shouldBe ""
    }

    @Test
    fun `create a distinct error for each error case`() {
        val cases = ErrorCase.entries.filter { it != ERROR_CASE_UNSPECIFIED && it != UNRECOGNIZED }

        val errors: Set<Error> = cases.map { it.toError() }.toSet()

        errors.size shouldBe cases.size
    }

    @Test
    fun `not convert the reserved error case`() {
        shouldThrow<IllegalArgumentException> {
            ERROR_CASE_UNSPECIFIED.toError()
        }
    }

    @Test
    fun `not convert an unrecognized error case`() {
        shouldThrow<IllegalArgumentException> {
            UNRECOGNIZED.toError()
        }
    }
}
