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

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.common.truth.extensions.proto.ProtoSubject;
import io.spine.annotation.VisibleForTesting;
import io.spine.core.ErrorCase;
import io.spine.core.Status;
import io.spine.core.Status.StatusCase;
import org.jspecify.annotations.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.truth.Truth.assertAbout;
import static io.spine.core.Status.StatusCase.ERROR_CASE;
import static io.spine.core.Status.StatusCase.OK;
import static io.spine.core.Status.StatusCase.REJECTION;

/**
 * A set of checks for the {@linkplain io.spine.client.QueryResponse query response} status.
 */
@VisibleForTesting
public final class ResponseStatusSubject extends ProtoSubject {

    private final @Nullable Status actual;

    private ResponseStatusSubject(FailureMetadata failureMetadata, @Nullable Status message) {
        super(failureMetadata, message);
        this.actual = message;
    }

    public static ResponseStatusSubject assertResponseStatus(@Nullable Status status) {
        return assertAbout(responseStatus()).that(status);
    }

    /**
     * Verifies that the response status is {@link StatusCase#OK OK}.
     */
    public void isOk() {
        hasStatusCase(OK);
    }

    /**
     * Verifies that the response status reports an {@link StatusCase#ERROR_CASE error case}.
     */
    public void isError() {
        hasStatusCase(ERROR_CASE);
    }

    /**
     * Verifies that the response status reports the given error case.
     */
    public void hasErrorCase(ErrorCase errorCase) {
        checkNotNull(errorCase);
        isError();
        check("errorCase()").that(checkNotNull(actual).getErrorCase())
                            .isEqualTo(errorCase);
    }

    /**
     * Verifies that the response status is {@link StatusCase#REJECTION REJECTION}.
     */
    public void isRejection() {
        hasStatusCase(REJECTION);
    }

    void hasStatusCase(StatusCase statusCase) {
        if (actual() == null) {
            assertExists();
        } else {
            check("statusCase()").that(statusCase())
                                 .isEqualTo(statusCase);
        }
    }

    private @Nullable Status actual() {
        return actual;
    }

    private StatusCase statusCase() {
        return checkNotNull(actual).getStatusCase();
    }

    private void assertExists() {
        isNotNull();
    }

    static Subject.Factory<ResponseStatusSubject, Status> responseStatus() {
        return ResponseStatusSubject::new;
    }
}
