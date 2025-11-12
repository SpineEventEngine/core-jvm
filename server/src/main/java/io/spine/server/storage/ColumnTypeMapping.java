/*
 * Copyright 2022, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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

package io.spine.server.storage;

import io.spine.annotation.Internal;

import java.util.function.Function;

/**
 * A persistence strategy of an entity {@linkplain io.spine.query.Column column}.
 *
 * <p>Maps a column value to a stored value of type {@code R}.
 *
 * @param <T>
 *         the column type
 * @param <R>
 *         the "persist as" type
 */
public interface ColumnTypeMapping<T, R> extends Function<T, R> {

    /**
     * A convenience alias for {@code #apply(T)}.
     *
     * <p>Can be used when the object is known of being of type {@code T} but can't be cast to it
     * explicitly (e.g. in case of wildcard arguments).
     */
    @Internal
    @SuppressWarnings("unchecked") // See doc.
    default R applyTo(Object object) {
        var value = (T) object;
        return apply(value);
    }

    static <T> ColumnTypeMapping<T, T> identity() {
        return t -> t;
    }
}
