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

package io.spine.server;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.spine.type.TypeUrl;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Allows to find to which Bounded Context a given type belongs.
 *
 * <p>Such a dictionary is used in services exposed to client communications
 * to find out to which bounded context a message received by the service belongs.
 */
final class TypeDictionary {

    private final ImmutableMap<TypeUrl, BoundedContext> map;

    private TypeDictionary(Map<TypeUrl, BoundedContext> map) {
        this.map = ImmutableMap.copyOf(map);
    }

    /**
     * Creates a new builder for a type dictionary.
     */
    static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Obtains a bounded context to which the given type belongs.
     *
     * @param type
     *         the type to search
     * @return the bounded context or empty {@code Optional}
     */
    Optional<BoundedContext> find(TypeUrl type) {
        var result = Optional.ofNullable(map.get(type));
        return result;
    }

    /**
     * Obtains the bounded contexts listed in this dictionary.
     */
    ImmutableCollection<BoundedContext> contexts() {
        return map.values();
    }

    /**
     * Tells if this dictionary empty or not.
     */
    boolean isEmpty() {
        return map.isEmpty();
    }

    /**
     * A builder for new type dictionary.
     */
    static final class Builder {

        private final Map<TypeUrl, BoundedContext> map = new HashMap<>();

        /**
         * Obtains the types from the given context and associates them with it.
         *
         * @param context
         *         the bounded context to query for the types
         * @param typeSupplier
         *         the function obtaining the types from the given instance of the context
         * @return this builder instance
         */
        @CanIgnoreReturnValue
        Builder putAll(BoundedContext context,
                       Function<BoundedContext, Set<TypeUrl>> typeSupplier) {
            checkNotNull(context);
            checkNotNull(typeSupplier);
            var types = typeSupplier.apply(context);
            types.forEach(type -> map.put(type, context));
            return this;
        }

        /**
         * Creates new type dictionary.
         */
        TypeDictionary build() {
            return new TypeDictionary(map);
        }
    }
}
