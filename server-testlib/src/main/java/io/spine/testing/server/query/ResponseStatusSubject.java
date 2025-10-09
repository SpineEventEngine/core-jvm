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

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.common.truth.extensions.proto.ProtoSubject;
import io.spine.annotation.VisibleForTesting;
import io.spine.core.Status;
import io.spine.core.Status.StatusCase;
import org.jspecify.annotations.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.truth.Truth.assertAbout;
import static io.spine.core.Status.StatusCase.ERROR;
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
     * Verifies that the response status is {@link StatusCase#ERROR ERROR}.
     */
    public void isError() {
        hasStatusCase(ERROR);
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
