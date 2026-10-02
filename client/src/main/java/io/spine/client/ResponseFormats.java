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

package io.spine.client;

import org.jspecify.annotations.Nullable;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A utility for working with {@link ResponseFormat}s.
 */
final class ResponseFormats {

    /**
     * Prevents this utility class from instantiation.
     */
    private ResponseFormats() {
    }

    /**
     * Creates a new {@code ResponseFormat}.
     *
     * <p>A caller of this method may choose which parts of the format are set. {@code null} value
     * passed signalizes that the part should not be set.
     *
     * @param ordering
     *         the ordering to return the results in,
     *         or {@code null} if the ordering is not specified
     * @param limit
     *         the maximum number of records to return in the scope of response,
     *         or {@code null} if no particular limit should be applied
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")      // Conditionally configuring the builder.
    static ResponseFormat responseFormat(@Nullable OrderBy ordering, @Nullable Integer limit) {
        var result = ResponseFormat.newBuilder();
        if (ordering != null) {
            result.addOrderBy(ordering);
        }
        if (limit != null) {
            checkArgument(limit > 0, "Limit must be positive, if set.");
            result.setLimit(limit);
        }
        return result.build();
    }
}
