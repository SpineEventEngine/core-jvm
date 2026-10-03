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

package io.spine.server.storage;

import com.google.protobuf.FieldMask;
import com.google.protobuf.Message;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;

/**
 * A {@link Function} that returns the passed storage record as-is.
 *
 * <p>Formerly, the function transformed the record by applying a {@link FieldMask} to it.
 *
 * @param <R>
 *         the type of the record
 * @deprecated Field masks are no longer supported. This function returns the passed record
 *         unchanged. Please remove its usages.
 */
@Deprecated
public final class FieldMaskApplier<R extends Message> implements Function<R, R> {

    /**
     * Creates a new instance of the function ignoring the passed field mask.
     *
     * @param fieldMask
     *         the ignored field mask
     */
    @SuppressWarnings("unused") // The mask is ignored.
    public FieldMaskApplier(FieldMask fieldMask) {
        super();
    }

    /**
     * Returns the passed record as-is.
     */
    @Override
    public @Nullable R apply(@Nullable R input) {
        return input;
    }
}
