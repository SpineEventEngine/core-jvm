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

package io.spine.server.enrich;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Message;
import io.spine.core.EnrichableMessageContext;
import io.spine.logging.WithLogging;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.lang.String.format;

/**
 * Contains enrichment functions.
 *
 * @param <M>
 *         the type of enriched messages
 * @param <C>
 *         the type of contexts along which the enriched messages exist
 */
final class Schema<M extends Message, C extends EnrichableMessageContext> implements WithLogging {

    private final ImmutableMap<Class<? extends M>, SchemaFn<? extends M, C>> map;

    private final int size;

    static <M extends Message, C extends EnrichableMessageContext>
    Schema<M, C> newInstance(EnricherBuilder<? extends M, C, ?> eBuilder) {
        var factory = new Factory<M, C>(eBuilder);
        var result = factory.create();
        return result;
    }

    private Schema(Factory<M, C> factory) {
        this.map = ImmutableMap.copyOf(factory.schemaMap);
        this.size = factory.functions.size();
        logger().atDebug().log(() -> format(
                "Created enrichment schema with %d entries.", this.size));
    }

    boolean isEmpty() {
        return size == 0;
    }

    @Nullable SchemaFn<? extends M, C> enrichmentOf(Class<? extends M> cls) {
        var fn = map.get(cls);
        return fn;
    }

    /**
     * Creates new {@code Schema}.
     *
     * <p>Transforms functions obtained from {@link EnricherBuilder} into functions
     * used by {@code Schema}, and then creates the instance.
     *
     * @param <M>
     *         the type of enriched messages
     * @param <C>
     *         the type of contexts along which the enriched messages exist
     */
    private static class Factory<M extends Message, C extends EnrichableMessageContext> {

        /** Functions we got from {@link EnricherBuilder}. */
        private final ImmutableMap<EnricherBuilder.Key, EnrichmentFn<? extends M, C, ?>> functions;

        /** The types of messages that these functions enrich. */
        private final ImmutableSet<Class<? extends M>> sourceTypes;

        /** The map from a class of the enrichable message to the schema function. */
        private final Map<Class<? extends M>, SchemaFn<? extends M, C>> schemaMap =
                new HashMap<>();

        @SuppressWarnings("unchecked")
        private Factory(EnricherBuilder<? extends M, C, ?> eBuilder) {
            checkNotNull(eBuilder);
            this.functions = ImmutableMap.copyOf(eBuilder.functions());
            this.sourceTypes =
                    functions.keySet()
                            .stream()
                            .map(EnricherBuilder.Key::sourceClass)
                            .map(c -> (Class<M>) c)
                            .collect(toImmutableSet());
        }

        private Schema<M, C> create() {
            for (var sourceType : sourceTypes) {
                var fn = createFn(sourceType);
                schemaMap.put(sourceType, fn);
            }

            return new Schema<>(this);
        }

        @SuppressWarnings("unchecked")
        private SchemaFn<? extends M, C> createFn(Class<? extends M> sourceType) {
            ImmutableSet<EnrichmentFn<M, C, ?>> fns =
                    functions.entrySet()
                            .stream()
                            .filter(e -> sourceType.equals(e.getKey()
                                                            .sourceClass()))
                            .map(e -> (EnrichmentFn<M, C, ?>) e.getValue())
                            .collect(toImmutableSet());
            if (fns.size() == 1) {
                return new SingularFn<>(fns.iterator()
                                           .next());
            } else {
                return new CompositeFn<M, C>(fns);
            }
        }
    }
}
