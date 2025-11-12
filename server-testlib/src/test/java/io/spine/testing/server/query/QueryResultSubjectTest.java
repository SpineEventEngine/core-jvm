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
import io.spine.base.EntityState;
import io.spine.client.QueryResponse;
import io.spine.testing.SubjectTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.ExpectFailure.assertThat;
import static com.google.common.truth.Truth.assertThat;
import static io.spine.core.Status.StatusCase.ERROR;
import static io.spine.core.Status.StatusCase.OK;
import static io.spine.testing.DisplayNames.NOT_ACCEPT_NULLS;
import static io.spine.testing.server.query.QueryResultSubject.assertQueryResult;
import static io.spine.testing.server.query.QueryResultSubject.queryResult;
import static io.spine.testing.server.query.QueryResultSubjectTestEnv.responseWithMultipleEntities;
import static io.spine.testing.server.query.QueryResultSubjectTestEnv.responseWithSingleEntity;
import static io.spine.testing.server.query.QueryResultSubjectTestEnv.state2;
import static io.spine.testing.server.query.QueryResultSubjectTestEnv.version2;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The test for the {@link QueryResultSubject}.
 *
 * <p>This test uses own {@code static} versions for some of the methods provided in
 * {@link SubjectTest} due to the non-standard
 * {@linkplain QueryResultSubject#assertQueryResult(QueryResponse) way} of subject creation.
 */
@DisplayName("`QueryResultSubject` should")
class QueryResultSubjectTest extends SubjectTest<QueryResultSubject, Iterable<EntityState<?>>> {

    private static final String EXPECTED_ASSERTION_ERROR_TO_BE_THROWN =
            "Expected `AssertionError` to be thrown.";

    @Override
    protected Subject.Factory<QueryResultSubject, Iterable<EntityState<?>>> subjectFactory() {
        return queryResult();
    }

    @Test
    @DisplayName(NOT_ACCEPT_NULLS)
    void passNullToleranceCheck() {
        new NullPointerTester()
                .testAllPublicStaticMethods(QueryResultSubject.class);
        new NullPointerTester()
                .testAllPublicInstanceMethods(assertWithSubjectThat(responseWithSingleEntity()));
    }

    @Test
    @DisplayName("check the response status")
    void checkResponseStatus() {
        var error = expectFailure(
                () -> assertWithSubjectThat(responseWithSingleEntity())
                        .hasStatus(ERROR)
        );
        assertThat(error).factValue(EXPECTED)
                         .isEqualTo(ERROR.toString());
        assertThat(error).factValue(BUT_WAS)
                         .isEqualTo(OK.toString());
    }

    @Test
    @DisplayName("return actual query results")
    void provideActualResults() {
        var response = responseWithMultipleEntities();
        var actualEntities = assertWithSubjectThat(response).actual();
        assertThat(actualEntities).hasSize(2);
    }

    @Test
    @DisplayName("provide subject for the response status")
    void provideResponseStatusSubject() {
        var error = expectFailure(
                () -> assertWithSubjectThat(responseWithSingleEntity())
                        .hasStatusThat()
                        .isError()
        );
        assertThat(error).factValue(EXPECTED)
                         .isEqualTo(ERROR.toString());
        assertThat(error).factValue(BUT_WAS)
                         .isEqualTo(OK.toString());
    }

    @SuppressWarnings({"CheckReturnValue", "ResultOfMethodCallIgnored"})
    // Method called to raise an error.
    @Test
    @DisplayName("check contains a single entity state")
    void checkSingleEntityState() {
        var error = expectFailure(
                () -> assertWithSubjectThat(responseWithMultipleEntities())
                        .containsSingleEntityStateThat()
        );
        assertThat(error).factValue(EXPECTED)
                         .contains("1");

        var actualCount = String.valueOf(responseWithMultipleEntities().size());
        assertThat(error).factValue(BUT_WAS)
                         .contains(actualCount);
    }

    @Test
    @DisplayName("provide a `ProtoSubject` for yielded entity state")
    void provideEntityStateProtoSubject() {
        expectSomeFailure(
                () -> assertWithSubjectThat(responseWithSingleEntity())
                        .containsSingleEntityStateThat()
                        .isEqualTo(state2())
        );
    }

    @SuppressWarnings("CheckReturnValue") // Method called to raise an error.
    @Test
    @DisplayName("check contains a single entity version")
    void checkSingleEntityVersion() {
        var error = expectFailure(
                () -> assertWithSubjectThat(responseWithMultipleEntities())
                        .containsSingleEntityVersionThat()
        );
        assertThat(error).factValue(EXPECTED)
                         .contains("1");

        var actualCount = String.valueOf(responseWithMultipleEntities().size());
        assertThat(error).factValue(BUT_WAS)
                         .contains(actualCount);
    }

    @Test
    @DisplayName("provide an `EntityVersionSubject` for yielded entity version")
    void provideEntityVersionSubject() {
        expectSomeFailure(
                () -> assertWithSubjectThat(responseWithSingleEntity())
                        .containsSingleEntityVersionThat()
                        .isEqualTo(version2())
        );
    }

    @SuppressWarnings("CheckReturnValue") // Method called to raise an error.
    @Test
    @DisplayName("provide a subject for yielded entity versions when they are multiple")
    void provideIterableEntityVersionSubject() {
        var expectedSize = 3;
        var error = expectFailure(
                () -> assertWithSubjectThat(responseWithMultipleEntities())
                        .containsEntityVersionListThat()
                        .hasSize(expectedSize)
        );
        assertThat(error).factValue(EXPECTED)
                         .contains(String.valueOf(expectedSize));

        var actualCount = String.valueOf(responseWithMultipleEntities().size());
        assertThat(error).factValue(BUT_WAS)
                         .contains(actualCount);
    }

    private static QueryResultSubject assertWithSubjectThat(QueryResponse queryResponse) {
        return assertQueryResult(queryResponse);
    }

    @SuppressWarnings({"ErrorNotRethrown", "MaskedAssertion"}) // Ignore the error.
    private static void expectSomeFailure(Runnable call) {
        try {
            call.run();
            fail(EXPECTED_ASSERTION_ERROR_TO_BE_THROWN);
        } catch (AssertionError ignored) {
            // Do nothing.
        }
    }

    @SuppressWarnings({"ReturnOfNull" /* Not reachable. */,
            "ErrorNotRethrown" /* Error is expected. */})
    private static AssertionError expectFailure(Runnable call) {
        try {
            call.run();
            fail(EXPECTED_ASSERTION_ERROR_TO_BE_THROWN);
        } catch (AssertionError e) {
            return e;
        }
        return null;
    }
}
