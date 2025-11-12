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

import org.jspecify.annotations.Nullable;

/**
 * The value mapping rules of record {@linkplain io.spine.query.Column columns}.
 *
 * <p>A {@link ColumnTypeMapping} allows to map column values of certain type to their value
 * in the storage.
 *
 * <p>Since there is a limited set of possible column types, in most non-testing scenarios it will
 * be more convenient to extend {@link AbstractColumnMapping} than to implement
 * this interface directly.
 *
 * @param <R>
 *         a supertype of all stored values
 */
public interface ColumnMapping<R> {

    /**
     * Obtains the mapping rules for the given type.
     *
     * @param <T>
     *         the column type
     * @throws IllegalArgumentException
     *         if the mapping for the specified type cannot be found
     */
    <T> ColumnTypeMapping<T, ? extends R> of(Class<T> type);

    /**
     * Obtains the mapping rules of {@code null}.
     */
    ColumnTypeMapping<? extends @Nullable Object, ? extends @Nullable R> ofNull();
}
