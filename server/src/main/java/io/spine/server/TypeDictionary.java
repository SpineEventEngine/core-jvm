/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package io.spine.server;

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
 * Allows finding to which Bounded Context a given type belongs.
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
