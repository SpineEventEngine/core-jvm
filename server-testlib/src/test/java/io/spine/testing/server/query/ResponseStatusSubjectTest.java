/*
 * Copyright 2025, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Redistribution and use in source and/or binary forms, with or without
 * modification, must retain the above copyright notice and the following
 * disclaimer.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
    @DisplayName("check if status is `ERROR`")
    void checkError() {
        assertWithSubjectThat(error())
                .isError();

        expectSomeFailure(whenTesting -> whenTesting.that(error())
                                                    .isOk());
    }

    @Test
    @DisplayName("check if status is `REJECTION`")
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
