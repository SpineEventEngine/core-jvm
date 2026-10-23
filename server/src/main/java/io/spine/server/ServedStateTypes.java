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

import com.google.common.collect.ImmutableList;
import io.spine.core.BoundedContextName;
import io.spine.type.TypeUrl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;

/**
 * The record of which open domain Bounded Context serves which entity state type.
 *
 * <p>When the check is on, the record lets only one open context serve a state type.
 * When the check is off, the record still keeps every context that serves the type,
 * so that {@link #contextsServing(TypeUrl)} can tell about all of them.
 *
 * <p>Contexts are told apart by identity rather than by
 * {@linkplain BoundedContext#equals(Object) name}: two contexts with the same name still have
 * a repository each.
 */
final class ServedStateTypes {

    private final Map<TypeUrl, List<BoundedContext>> contexts = new HashMap<>();

    /**
     * Records that the given context serves entities with the given state type.
     *
     * <p>Does nothing if the context is already recorded for the type.
     *
     * @param context
     *         the domain context to which a repository is being added
     * @param stateType
     *         the state type of the entities of the repository
     * @param check
     *         if {@code true}, the context is not recorded when another open context
     *         already serves the type, and the method throws instead
     * @throws DuplicateEntityStateException
     *         if {@code check} is {@code true}, and another open context already serves the type
     */
    synchronized void serve(BoundedContext context, TypeUrl stateType, boolean check) {
        checkNotNull(context);
        checkNotNull(stateType);
        var serving = contexts.computeIfAbsent(stateType, type -> new ArrayList<>());
        if (serving.stream().anyMatch(sameAs(context))) {
            return;
        }
        if (check && !serving.isEmpty()) {
            var names = ImmutableList.<BoundedContextName>builder()
                    .addAll(names(serving))
                    .add(context.name())
                    .build();
            throw new DuplicateEntityStateException(stateType, names);
        }
        serving.add(context);
    }

    /**
     * Removes the given context from the record.
     *
     * <p>Does nothing if the context is not recorded.
     */
    synchronized void forget(BoundedContext context) {
        checkNotNull(context);
        contexts.values()
                .forEach(serving -> serving.removeIf(sameAs(context)));
        contexts.values()
                .removeIf(List::isEmpty);
    }

    /**
     * Obtains the names of the open contexts that serve entities with the given state type,
     * in the order they were recorded.
     */
    synchronized ImmutableList<BoundedContextName> contextsServing(TypeUrl stateType) {
        checkNotNull(stateType);
        var serving = contexts.getOrDefault(stateType, List.of());
        return names(serving);
    }

    @SuppressWarnings("ReferenceEquality") // Contexts are told apart by identity.
    private static Predicate<BoundedContext> sameAs(BoundedContext context) {
        return recorded -> recorded == context;
    }

    private static ImmutableList<BoundedContextName> names(List<BoundedContext> contexts) {
        return contexts.stream()
                       .map(BoundedContext::name)
                       .collect(toImmutableList());
    }
}
