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

import com.google.errorprone.annotations.Immutable;
import io.spine.annotation.GeneratedMixin;
import io.spine.base.Error;

/**
 * Mixin interface for the {@link Response} objects.
 */
@GeneratedMixin
@Immutable
interface ResponseMixin extends ResponseOrBuilder {

    /**
     * Verifies if this response has the {@link Status.StatusCase#OK OK} status.
     */
    default boolean isOk() {
        return getStatus().getStatusCase() == Status.StatusCase.OK;
    }

    /**
     * Verifies if this response reports an error.
     *
     * <p>The error is either an {@linkplain Status#getErrorCase() error case}, or
     * the deprecated {@link Status.StatusCase#ERROR ERROR}, which the server does not send
     * any more.
     */
    @SuppressWarnings("deprecation") // Recognize the deprecated status as an error too.
    default boolean isError() {
        var statusCase = getStatus().getStatusCase();
        return statusCase == Status.StatusCase.ERROR_CASE
                || statusCase == Status.StatusCase.ERROR;
    }

    /**
     * Obtains the kind of the error reported by the response.
     *
     * @return the error case, or {@link ErrorCase#ERROR_CASE_UNSPECIFIED} if the response
     *         does not report an error case
     */
    default ErrorCase errorCase() {
        return getStatus().getErrorCase();
    }

    /**
     * Obtains the error associated with the response, or the default instance if the response is
     * not an error.
     *
     * @deprecated The server reports the kind of the error instead; please use
     *         {@link #errorCase()}.
     */
    @Deprecated
    @SuppressWarnings("DeprecatedIsStillUsed") // Kept until `Status.error` is removed.
    default Error error() {
        return getStatus().getError();
    }
}
