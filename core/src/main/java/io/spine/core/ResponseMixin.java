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
     * Verifies if this response has the {@link Status.StatusCase#ERROR_CASE ERROR_CASE} status.
     */
    default boolean isError() {
        return getStatus().getStatusCase() == Status.StatusCase.ERROR_CASE;
    }

    /**
     * Obtains the kind of the error reported by this response.
     *
     * @return the error case, or {@link ErrorCase#ERROR_CASE_UNSPECIFIED} if the response
     *         is not an error
     */
    default ErrorCase errorCase() {
        return getStatus().getErrorCase();
    }
}
