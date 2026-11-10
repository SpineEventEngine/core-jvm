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
import io.spine.base.EntityState;
import io.spine.client.QueryResponse;
import io.spine.testing.SubjectTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.ExpectFailure.assertThat;
import static com.google.common.truth.Truth.assertThat;
import static io.spine.core.Status.StatusCase.ERROR_CASE;
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
                        .hasStatus(ERROR_CASE)
        );
        assertThat(error).factValue(EXPECTED)
                         .isEqualTo(ERROR_CASE.toString());
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
                         .isEqualTo(ERROR_CASE.toString());
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
