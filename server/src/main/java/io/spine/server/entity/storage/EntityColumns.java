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
import io.spine.query.Column;
import io.spine.server.entity.Entity;

import java.util.Iterator;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A container for the columns whose values are calculated on top of an {@code Entity} instance.
 *
 * @param <E>
 *         the type of {@code Entity} serving as a source for the column values
 */
@Immutable
@SuppressWarnings("Immutable")  /* Effectively immutable. */
final class EntityColumns<E extends Entity<?, ?>> implements Iterable<Column<E, ?>> {

    private final ImmutableSet<Column<E, ?>> columns;

    /**
     * Creates a new instance from the passed columns.
     */
    EntityColumns(Set<Column<E, ?>> columns) {
        this.columns = ImmutableSet.copyOf(columns);
    }

    @Override
    public Iterator<Column<E, ?>> iterator() {
        return columns.iterator();
    }

    /**
     * Returns the number of columns.
     */
    int size() {
        return columns.size();
    }

    /**
     * Returns a new stream on top of the stored columns.
     */
    Stream<Column<E, ?>> stream() {
        return columns.stream();
    }

    /**
     * Returns the stored columns.
     */
    ImmutableSet<Column<E, ?>> values() {
        return columns;
    }
}
