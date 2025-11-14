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
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.Message;
import io.spine.core.EnrichableMessageContext;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.type.MessageClass.interfacesOf;

/**
 * Allows registering enrichment functions used by the {@link Enricher}.
 *
 * @param <M>
 *         the type of the enriched messages
 * @param <C>
 *         the type of the contexts of the enriched messages
 * @param <B>
 *         the type of enricher builder implementation, a descendant of this type, for covariance
 */
public abstract class EnricherBuilder<M extends Message,
                                      C extends EnrichableMessageContext,
                                      B extends EnricherBuilder<M, C, B>> {

    private static final String SUGGEST_REMOVAL = " Please call `remove(Class, Class)` first.";

    /**
     * Maps a pair of [source class, enrichment class] to a function which produces this enrichment.
     */
    private final Map<Key, EnrichmentFn<? extends M, C, ?>> functions = new HashMap<>();

    /**
     * Creates a new instance.
     */
    protected EnricherBuilder() {
    }

    /**
     * Adds an enrichment function to the builder.
     *
     * @implNote This method does the real job of adding functions to the builder.
     *         The binding of the generic parameters allows type-specific functions
     *         exposed in the public API to call this method.
     */
    @CanIgnoreReturnValue
    protected final <S extends M, T extends Message>
    B doAdd(Class<S> messageClassOrInterface,
            Class<T> enrichmentClass,
            EnrichmentFn<S, C, T> func) {
        checkNotNull(messageClassOrInterface);
        checkNotNull(enrichmentClass);
        checkArgument(!enrichmentClass.isInterface(),
                      "The `enrichmentClass` argument must be a class, not an interface." +
                      " `%s` is the interface. Please pass a class which" +
                      " implements this interface.",
                      enrichmentClass.getCanonicalName());
        checkNotNull(func);
        var key = new Key(messageClassOrInterface, enrichmentClass);
        checkDirectDuplication(key);
        checkInterfaceDuplication(key);
        checkImplDuplication(key);
        functions.put(key, func);
        return self();
    }

    @SuppressWarnings("unchecked")
    private B self() {
        return (B) this;
    }

    private void checkDirectDuplication(Key key) {
        checkArgument(
                !functions.containsKey(key),
                "The function which enriches `%s` with `%s` is already added." + SUGGEST_REMOVAL,
                key.sourceClass().getCanonicalName(),
                key.enrichmentClass().getCanonicalName()
        );
    }

    private void checkInterfaceDuplication(Key key) {
        var sourceClass = key.sourceClass();
        var enrichmentClass = key.enrichmentClass();
        var interfaces = interfacesOf(sourceClass);
        interfaces.forEach(i -> {
            var keyByInterface = new Key(i, enrichmentClass);
            checkArgument(
                    !functions.containsKey(keyByInterface),
                    "The builder already has the function which creates enrichments of the class" +
                    " `%s` via the interface `%s` which is implemented by the class `%s`." +
                    SUGGEST_REMOVAL,
                    enrichmentClass.getCanonicalName(),
                    i.getCanonicalName(),
                    sourceClass.getCanonicalName()
            );
        });
    }

    private void checkImplDuplication(Key key) {
        var sourceInterface = key.sourceClass();
        var enrichmentClass = key.enrichmentClass();
        functions.forEach((k, v) -> {
            var entrySourceCls = k.sourceClass();
            var entryEnrichmentCls = k.enrichmentClass();
            checkArgument(
                    !sourceInterface.isAssignableFrom(entrySourceCls)
                    || !enrichmentClass.equals(entryEnrichmentCls),
                    "Unable to add a function which produces enrichments of the class `%s`" +
                    " via the interface `%s`. There is already a function which does" +
                    " this via the class `%s` which implements this interface." +
                    SUGGEST_REMOVAL,
                    enrichmentClass.getCanonicalName(),
                    sourceInterface.getCanonicalName(),
                    entrySourceCls.getCanonicalName()
            );
        });
    }

    /**
     * Removes the enrichment function for the passed event class.
     *
     * <p>If the function for this class was not added, the call has no effect.
     */
    @CanIgnoreReturnValue
    public <T extends Message>
    B remove(Class<M> eventClass, Class<T> enrichmentClass) {
        functions.remove(new Key(eventClass, enrichmentClass));
        return self();
    }

    /** Creates a new {@code Enricher}. */
    public abstract Enricher<M, C> build();

    /**
     * Obtains functions added to the builder by the time of the call.
     */
    ImmutableMap<Key, EnrichmentFn<? extends M, C, ?>> functions() {
        return ImmutableMap.copyOf(functions);
    }

    /**
     * A pair of source message class and enrichment message class which is used to match
     * the pair to a function which produces the enrichment.
     *
     * @see EnricherBuilder#functions
     */
    @Immutable
    static class Key {

        private final Class<? extends Message> sourceClass;
        private final Class<? extends Message> enrichmentClass;

        Key(Class<? extends Message> sourceClass, Class<? extends Message> enrichmentClass) {
            this.sourceClass = checkNotNull(sourceClass);
            this.enrichmentClass = checkNotNull(enrichmentClass);
        }

        Class<? extends Message> sourceClass() {
            return sourceClass;
        }

        Class<? extends Message> enrichmentClass() {
            return enrichmentClass;
        }

        @Override
        public int hashCode() {
            return Objects.hash(sourceClass, enrichmentClass);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Key)) {
                return false;
            }
            final var other = (Key) obj;
            return Objects.equals(this.sourceClass.getName(),
                                  other.sourceClass.getName())
                    && Objects.equals(this.enrichmentClass.getName(),
                                      other.enrichmentClass.getName());
        }
    }
}
