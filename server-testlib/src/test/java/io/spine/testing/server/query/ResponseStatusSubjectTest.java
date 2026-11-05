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

package io.spine.testing.server.query;

import com.google.common.testing.NullPointerTester;
import com.google.common.truth.Subject;
import io.spine.core.Status;
import io.spine.testing.SubjectTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.spine.testing.DisplayNames.NOT_ACCEPT_NULLS;
import static io.spine.testing.server.query.GivenResponseStatus.error;
import static io.spine.testing.server.query.GivenResponseStatus.ok;
import static io.spine.testing.server.query.GivenResponseStatus.rejection;
import static io.spine.testing.server.query.ResponseStatusSubject.assertResponseStatus;
import static io.spine.testing.server.query.ResponseStatusSubject.responseStatus;

@DisplayName("`ResponseStatusSubject` should")
class ResponseStatusSubjectTest extends SubjectTest<ResponseStatusSubject, Status> {

    @Test
    @DisplayName(NOT_ACCEPT_NULLS)
    void passNullToleranceCheck() {
        new NullPointerTester()
                .testAllPublicInstanceMethods(assertResponseStatus(ok()));
    }

    @Test
    @DisplayName("check if status is `OK`")
    void checkOk() {
        assertWithSubjectThat(ok())
                .isOk();

        expectSomeFailure(whenTesting -> whenTesting.that(ok())
                                                    .isError());
    }

    @Test
    @DisplayName("check if status reports an error case")
    void checkError() {
        assertWithSubjectThat(error())
                .isError();

        expectSomeFailure(whenTesting -> whenTesting.that(error())
                                                    .isOk());
    }

    @Test
    @DisplayName("check if status is `REJECTION`")
    @SuppressWarnings("deprecation") // Checks the deprecated `isRejection()`.
    void checkRejection() {
        assertWithSubjectThat(rejection())
                .isRejection();

        expectSomeFailure(whenTesting -> whenTesting.that(rejection())
                                                    .isOk());
    }

    @Test
    @DisplayName("fail status check if the value is `null`")
    void failCheckIfNull() {
        expectSomeFailure(whenTesting -> whenTesting.that(null)
                                                    .isOk());
    }

    @Override
    protected Subject.Factory<ResponseStatusSubject, Status> subjectFactory() {
        return responseStatus();
    }
}
