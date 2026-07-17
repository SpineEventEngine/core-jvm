/*
 * Copyright 2026, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
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

package io.spine.server.delivery;

import io.spine.server.projection.ProjectionRepository;
import io.spine.type.TypeUrl;

import java.util.HashMap;
import java.util.Map;

import static io.spine.util.Exceptions.newIllegalStateException;
import static java.util.Collections.synchronizedMap;

/**
 * A cache of {@link ProjectionRepository Projection repositories} that are used in
 * the {@link CatchUpProcess}es across all known Bounded Contexts.
 *
 * <p>This type is a JVM-wide singleton.
 */
final class CatchUpRepositories {

    private static final CatchUpRepositories instance = new CatchUpRepositories();

    private final Map<TypeUrl, ProjectionRepository<?, ?, ?>> repos =
            synchronizedMap(new HashMap<>());

    private CatchUpRepositories() {
    }

    /**
     * Returns the instance of this cache.
     */
    static CatchUpRepositories cache() {
        return instance;
    }

    /**
     * Registers the {@code ProjectionRepository} as such to be associated with
     * the catch-up processes that have already started, or may be started in the future.
     *
     * @param repository
     *         a repository to cache
     */
    void put(ProjectionRepository<?, ?, ?> repository) {
        repos.put(repository.entityStateType(), repository);
    }

    /**
     * Obtains the previously registered {@code ProjectionRepository} by the type URL of the
     * projection under-catch-up.
     *
     * <p>It is a responsibility of the caller to use a proper type when calling this method.
     *
     * <p>In case no repository was previously cached with the provided type URL,
     * an {@link IllegalStateException} is thrown.
     *
     * @param projectionType
     *         the type of the projection under-catch-up
     * @param <I>
     *         the type of the identifiers of projections managed by the repository.
     * @return the instance of the repository
     * @throws IllegalStateException
     *         if no repository is registered for the passed type URL
     */
    @SuppressWarnings("unchecked")
    <I> ProjectionRepository<I, ?, ?> get(TypeUrl projectionType) {
        if (!repos.containsKey(projectionType)) {
            throw newIllegalStateException("Cannot find a `ProjectionRepository` " +
                                                   "for the catch-up process with type URL `%s`.",
                                           projectionType);
        }
        return (ProjectionRepository<I, ?, ?>) repos.get(projectionType);
    }
}
