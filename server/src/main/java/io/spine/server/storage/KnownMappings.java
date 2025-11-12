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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import java.util.Optional;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Speeds up the discovery of the {@link ColumnMapping} by the type of column.
 *
 * <p>Searches for the column mappings among user-provided custom mappings.
 * If nothing is found for the given column type, the standard mappings are searched.
 *
 * <p>In real-life scenarios, the search for the column mapping corresponding
 * to the column type is performed many times for the same inputs. As long as the mappings
 * are configured during the application setup, it makes sense to cache
 * the mapping values per column type.
 *
 * @param <R>
 *         type of the column to persist the column value as
 */
final class KnownMappings<R> {

    private final FindMapping<R> findStandardMapping;
    private final FindMapping<R> findCustomMapping;

    /**
     * Cached column mappings per type.
     *
     * <p>Without caching, this operation may be executed for too many times
     * for the same input.
     */
    private final LoadingCache<Class<?>, Optional<ColumnTypeMapping<?, ? extends R>>>
            mappingsByType = CacheBuilder.newBuilder()
            .maximumSize(300)
            .build(new CacheLoader<>() {
                @Override
                public Optional<ColumnTypeMapping<?, ? extends R>> load(Class<?> type) {
                    var result = findCustomMapping.apply(type);
                    if (result.isEmpty()) {
                        result = findStandardMapping.apply(type);
                    }
                    return result;
                }
            });

    /**
     * Creates a new instance.
     *
     * @param findStandardMapping
     *         function searching for the mapping in standard column mappings
     * @param findCustomMapping
     *         function searching for the mapping among custom mappings
     */
    KnownMappings(FindMapping<R> findStandardMapping, FindMapping<R> findCustomMapping) {
        this.findStandardMapping = checkNotNull(findStandardMapping);
        this.findCustomMapping = checkNotNull(findCustomMapping);
    }

    /**
     * Obtains the type mapping for the given value and returns it as {@code Optional}.
     *
     * <p>Returns {@code Optional.empty()} if no mapping has been found.
     *
     * @param type
     *         type to look the mapping for
     * @apiNote The returning type is {@code Optional}, as soon as the callee would have
     *         some logic on handling the {@code Optional.empty()} results
     *         in a pseudo-functional way. Returning {@code null} instead would break
     *         the functional nature of the calling code.
     */
    Optional<ColumnTypeMapping<?, ? extends R>> get(Class<?> type) {
        return mappingsByType.getUnchecked(type);
    }

    /**
     * Obtains the mapping for the given column type.
     *
     * @param <R>
     *         type of the column to persist the column value as
     */
    @FunctionalInterface
    interface FindMapping<R>
            extends Function<Class<?>, Optional<ColumnTypeMapping<?, ? extends R>>> {

    }
}
