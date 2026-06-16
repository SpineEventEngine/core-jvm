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

package io.spine.client;

import io.spine.annotation.Internal;
import io.spine.query.ColumnName;
import io.spine.query.CustomColumn;
import io.spine.reflect.GenericTypeIndex;

import java.util.function.Supplier;

import static io.spine.util.Exceptions.newIllegalStateException;

/**
 * A common type for the lifecycle column definitions that are declared as a part of a client-side
 * query language.
 *
 * @param <V>
 *         the type of column values
 * @apiNote Unlike other types of {@link io.spine.query.Column Column}s, the descendants of
 *         this type are intended just to introduce the definitions of columns into the query DSL.
 *         Therefore, the API for obtaining the column values from entities and entity records
 *         is disabled by explicit throwing an {@code IllegalStateException}s in respective method.
 */
@Internal
@SuppressWarnings("AbstractClassWithoutAbstractMethods")    /* Must not be instantiated. */
public abstract class EntityLifecycleColumn<V> extends CustomColumn<Supplier<V>, V> {

    private final ColumnName name;

    private final Class<V> valueType;

    /**
     * Creates an instance of this class with the passed name of the column.
     */
    @SuppressWarnings("unchecked")   /* Ensured by the declaration of the generic parameter. */
    EntityLifecycleColumn(String name) {
        super();
        this.name = ColumnName.of(name);
        this.valueType = (Class<V>) GenericParameter.VALUE.argumentIn(getClass());
    }

    @Override
    public Class<V> type() {
        return valueType;
    }

    @Override
    public ColumnName name() {
        return name;
    }

    /**
     * Always throws an {@code IllegalStateException}.
     *
     * The descendant types of this class are designed to be a part of the query DSL,
     * but not the real means to extract column values. Therefore, the implementation of this method
     * prohibits obtaining the column values.
     *
     * @throws IllegalStateException
     *         always
     */
    @Override
    public final V valueIn(Supplier<V> source) {
        throw newIllegalStateException("`EntityLifecycleColumn.valueIn(..)` " +
                                               "must not be called directly.");
    }

    /**
     * Returns the name of the column as a {@code String}.
     */
    @Override
    public String toString() {
        return name.value();
    }

    /**
     * Enumeration of generic type parameters of this class.
     */
    @SuppressWarnings("rawtypes")
    private enum GenericParameter implements GenericTypeIndex<EntityLifecycleColumn> {

        /** The index of the generic type {@code <V>}. */
        VALUE(0);

        private final int index;

        GenericParameter(int index) {
            this.index = index;
        }

        @Override
        public int index() {
            return this.index;
        }
    }
}
