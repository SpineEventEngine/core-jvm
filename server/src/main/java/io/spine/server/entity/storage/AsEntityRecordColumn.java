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

package io.spine.server.entity.storage;

import com.google.errorprone.annotations.Immutable;
import io.spine.query.Column;
import io.spine.query.RecordColumn;
import io.spine.server.entity.EntityRecord;

import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.util.Exceptions.newIllegalStateException;

/**
 * Utility which creates views on the {@linkplain Column columns} posting them as instances
 * of {@link RecordColumn}s.
 *
 * <p>Such a "type cast" is required in order to adapt the all the column types available
 * to the framework users into the {@code RecordColumn}s, which are used in
 * {@linkplain io.spine.query.RecordQuery the common data structures} for
 * {@linkplain io.spine.server.storage.RecordStorage storage and retrieval} of the records' data.
 */
final class AsEntityRecordColumn {

    /**
     * Prevents the instantiation of this utility.
     */
    private AsEntityRecordColumn() {
    }

    /**
     * Creates a view on the given column as on a record column of an {@link EntityRecord}
     * with the given type of values.
     *
     * <p>A value of the resulting column is determined by the passed getter.
     *
     * @param original
     *         the column to create a view for
     * @param typeOfValues
     *         the type of the column values
     * @param getter
     *         the getter returning the value of the resulting column view
     * @param <V>
     *         the type of the column values
     * @return a view on the column
     */
    @SuppressWarnings("Immutable")
    static <V> RecordColumn<EntityRecord, V>
    apply(Column<?, ?> original, Class<V> typeOfValues, Function<EntityRecord, V> getter) {
        checkNotNull(original);
        checkNotNull(typeOfValues);
        var columnName = columnName(original);
        return RecordColumn.create(columnName, typeOfValues, getter::apply);
    }

    /**
     * Creates a view on the given column as on the record column of an {@linkplain EntityRecord}
     * with the {@code Object} values.
     *
     * <p>The resulting view loses an ability to {@linkplain Column#valueIn(Object) obtain
     * the values from the record instance}.
     *
     * @param original
     *         the column to create a view for
     * @return a view on the column
     */
    static <V> RecordColumn<EntityRecord, V> apply(Column<?, V> original) {
        var type = original.type();
        return apply(original, type);
    }

    /**
     * Creates a view on the given column as on a record column of an {@link EntityRecord}
     * with the given type of values.
     *
     * <p>The resulting view loses an ability to {@linkplain Column#valueIn(Object) obtain
     * the values from the record instance}.
     *
     * @param original
     *         the column to create a view for
     * @param typeOfValues
     *         the type of the column values
     * @param <V>
     *         the type of the column values
     * @return a view on the column
     */
    private static <V> RecordColumn<EntityRecord, V>
    apply(Column<?, ?> original, Class<V> typeOfValues) {
        return apply(original, typeOfValues, new NoGetter<>());
    }

    private static String columnName(Column<?, ?> original) {
        return original.name()
                       .value();
    }

    /**
     * Returns the getter which always throws an {@link IllegalStateException} upon invocation.
     *
     * @param <V>
     *         the type of the column values
     */
    @Immutable
    private static final class NoGetter<V> implements Column.Getter<EntityRecord, V> {

        @Override
        public V apply(EntityRecord record) {
            throw newIllegalStateException("`EntityRecordColumn`s do not have getters.");
        }
    }
}
