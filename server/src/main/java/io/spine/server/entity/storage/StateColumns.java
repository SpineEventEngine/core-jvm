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

import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.Immutable;
import io.spine.base.EntityState;
import io.spine.query.ColumnName;
import io.spine.query.EntityColumn;
import io.spine.query.RecordColumn;
import org.jspecify.annotations.Nullable;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Describes the columns of a particular entity defined in its Protobuf {@code Message}
 * via {@code (column)} annotation.
 *
 * @param <S>
 *         the type of the entity state
 */
@Immutable
final class StateColumns<S extends EntityState<?>> implements Iterable<EntityColumn<S, ?>> {

    private final ImmutableSet<EntityColumn<S, ?>> columns;

    /**
     * Creates a new instance from the passed columns.
     */
    StateColumns(Set<EntityColumn<S, ?>> columns) {
        this.columns = ImmutableSet.copyOf(columns);
    }

    /**
     * Returns an empty column set.
     *
     * @param <S>
     *         the type of the entity state which columns are described
     */
    static <S extends EntityState<?>> StateColumns<S> none() {
        return new StateColumns<>(ImmutableSet.of());
    }

    /**
     * Evaluates the columns against the passed entity's state and returns the value of each column
     * along with its name.
     */
    @SuppressWarnings("ConstantConditions") /* `@Nullable` value mapper for the map's collector. */
    Map<ColumnName, @Nullable Object> valuesIn(S entityState) {
        var values = columns.stream()
                .collect(Collectors.toMap(
                        RecordColumn::name,
                        column -> (Object) column.valueIn(entityState)
                ));
        return values;
    }

    @Override
    public Iterator<EntityColumn<S, ?>> iterator() {
        return columns.iterator();
    }
}
