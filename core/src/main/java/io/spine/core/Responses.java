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

package io.spine.core;

import com.google.protobuf.Empty;
import io.spine.base.Error;

import static com.google.common.base.Preconditions.checkArgument;
import static io.spine.util.Preconditions2.checkNotDefaultArg;

/**
 * Utilities for working with {@link Response Response} objects.
 */
public final class Responses {

    private static final Status STATUS_OK =
            Status.newBuilder()
                  .setOk(Empty.getDefaultInstance())
                  .build();

    /** The response returned on successful acceptance of a message for processing. */
    private static final Response RESPONSE_OK =
            Response.newBuilder()
                    .setStatus(STATUS_OK)
                    .build();

    /** Prevent instantiation of this utility class. */
    private Responses() {
    }

    /** Returns the instance of OK {@link Response}. */
    public static Response ok() {
        return RESPONSE_OK;
    }

    /**
     * Returns the {@code OK} {@link Status} instance.
     */
    public static Status statusOk() {
        return STATUS_OK;
    }

    /**
     * Creates an error status of the passed kind.
     *
     * @throws IllegalArgumentException
     *         if the error case is {@link ErrorCase#ERROR_CASE_UNSPECIFIED} or unrecognized
     */
    public static Status errorWith(ErrorCase errorCase) {
        checkArgument(errorCase != ErrorCase.ERROR_CASE_UNSPECIFIED
                              && errorCase != ErrorCase.UNRECOGNIZED,
                      "The error case must be specified.");
        return Status.newBuilder()
                     .setErrorCase(errorCase)
                     .build();
    }

    /**
     * Creates an error status with the passed cause.
     *
     * @deprecated {@code Status.error}, which this method sets, is going to be removed.
     *         Please use {@link #errorWith(ErrorCase)}.
     */
    @Deprecated
    public static Status errorWith(Error cause) {
        return Status.newBuilder()
                     .setError(cause)
                     .build();
    }

    /**
     * Creates a rejection status with the passed rejection.
     *
     * @deprecated {@code Status.rejection}, which this method sets, is going to be removed.
     */
    @Deprecated
    public static Status rejectedBecauseOf(Event rejection) {
        checkNotDefaultArg(rejection);
        var status = Status.newBuilder()
                .setRejection(rejection)
                .build();
        return status;
    }
}
