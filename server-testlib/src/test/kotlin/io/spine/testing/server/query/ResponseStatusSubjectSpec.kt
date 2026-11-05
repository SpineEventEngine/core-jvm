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

package io.spine.testing.server.query

import io.kotest.assertions.throwables.shouldThrow
import io.spine.core.ErrorCase.INVALID_REQUEST
import io.spine.core.ErrorCase.TENANT_ERROR
import io.spine.core.Responses
import io.spine.testing.server.query.ResponseStatusSubject.assertResponseStatus
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Supplements the Java suite `ResponseStatusSubjectTest` with the specs of error cases.
 */
@DisplayName("`ResponseStatusSubject` should")
internal class ResponseStatusSubjectSpec {

    @Test
    fun `check the error case of the status`() {
        assertResponseStatus(Responses.errorWith(INVALID_REQUEST))
            .hasErrorCase(INVALID_REQUEST)
    }

    @Test
    fun `fail when the error case differs`() {
        shouldThrow<AssertionError> {
            assertResponseStatus(Responses.errorWith(INVALID_REQUEST))
                .hasErrorCase(TENANT_ERROR)
        }
    }

    @Test
    fun `fail when the status is not an error`() {
        shouldThrow<AssertionError> {
            assertResponseStatus(Responses.statusOk())
                .hasErrorCase(INVALID_REQUEST)
        }
    }
}
