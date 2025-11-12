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

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableSet;
import io.spine.base.EntityState;
import io.spine.option.EntityOption.Visibility;
import io.spine.server.entity.EntityVisibility;
import io.spine.server.entity.Repository;
import io.spine.type.TypeName;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static io.spine.util.Exceptions.newIllegalStateException;

/**
 * A registry of repositories that controls access to them depending on the visibility of
 * corresponding entity states.
 */
final class VisibilityGuard {

    private final
    Map<Class<? extends EntityState<?>>, RepositoryAccess> repositories = new HashMap<>();

    /** Prevent instantiation from outside. */
    private VisibilityGuard() {
    }

    /**
     * Creates a new instance of the guard.
     */
    static VisibilityGuard newInstance() {
        return new VisibilityGuard();
    }

    /**
     * Registers the passed repository with the guard.
     */
    void register(Repository<?, ?> repository) {
        checkNotNull(repository);
        var entityClass = repository.entityModelClass();
        var stateClass = entityClass.stateClass();
        checkNotAlreadyRegistered(stateClass);
        repositories.put(stateClass, new RepositoryAccess(repository));
    }

    private void checkNotAlreadyRegistered(Class<? extends EntityState<?>> stateClass) {
        var alreadyRegistered = repositories.get(stateClass);
        if (alreadyRegistered != null) {
            throw newIllegalStateException(
                    "A repository for the state class %s already registered: `%s`.",
                    stateClass.getName(),
                    alreadyRegistered
            );
        }
    }

    /**
     * Verifies if there is a registered repository for the passed entity state class.
     */
    boolean hasRepository(Class<? extends EntityState<?>> stateClass) {
        checkNotNull(stateClass);
        var result = repositories.containsKey(stateClass);
        return result;
    }

    /**
     * Obtains the repository for the passed entity state class.
     *
     * @param stateClass
     *         the class of the state of entities managed by the repository
     * @return the repository wrapped into {@code Optional} or {@code Optional#empty()} if the
     *         entity state is {@linkplain Visibility#NONE not visible}
     * @throws IllegalArgumentException
     *         if the repository for the passed state class was not
     *         {@linkplain #register(Repository) registered} with the guard
     *         prior to this call, or if all repositories were
     *         {@linkplain #shutDownRepositories() shut down}
     */
    Optional<Repository<?, ?>> repositoryFor(Class<? extends EntityState<?>> stateClass) {
        checkNotNull(stateClass);
        var repositoryAccess = findOrThrow(stateClass);
        return repositoryAccess.get();
    }

    private RepositoryAccess findOrThrow(Class<? extends EntityState<?>> stateClass) {
        var repository = repositories.get(stateClass);
        if (repository == null) {
            throw newIllegalStateException(
                    "A repository for the state class `%s` is not registered.",
                    stateClass.getName()
            );
        }
        return repository;
    }

    /**
     * Obtains a repository by the type of the entity state.
     *
     * @throws IllegalStateException
     *         if there is no repository, entities of which have the passed state
     */
    Repository<?, ?> get(Class<? extends EntityState<?>> stateClass) {
        var access = findOrThrow(stateClass);
        return access.repository;
    }

    private Stream<RepositoryAccess> repoAccess() {
        return repositories.values()
                           .stream();
    }

    /** Obtains all guarded repositories. */
    private ImmutableSet<Repository<?, ?>> repositories() {
        ImmutableSet<Repository<?, ?>> result =
                repoAccess().map(access -> access.repository)
                            .collect(toImmutableSet());
        return result;
    }

    /**
     * Obtains a set of entity type names by their visibility.
     */
    public Set<TypeName> entityStateTypes(Visibility visibility) {
        checkNotNull(visibility);
        Set<TypeName> result =
                repoAccess().filter(access -> access.visibility.is(visibility))
                            .map(RepositoryAccess::stateTypeName)
                            .collect(toImmutableSet());
        return result;
    }

    Set<TypeName> allEntityTypes() {
        Set<TypeName> result =
                repoAccess().map(RepositoryAccess::stateTypeName)
                            .collect(toImmutableSet());
        return result;
    }

    /**
     * Closes all registered repositories and clears the registration list.
     */
    void shutDownRepositories() {
        repositories().forEach(Repository::close);
        repositories.clear();
    }

    boolean isClosed() {
        return repositories.isEmpty();
    }

    /**
     * Allows to get a reference to a repository if states of its entities are visible.
     */
    private static class RepositoryAccess {

        private final Repository<?, ?> repository;
        private final EntityVisibility visibility;

        private RepositoryAccess(Repository<?, ?> repository) {
            this.repository = repository;
            var entityClass = repository.entityModelClass();
            this.visibility = entityClass.visibility();
        }

        private Optional<Repository<?, ?>> get() {
            return visibility.isNotNone()
                   ? Optional.of(repository)
                   : Optional.empty();
        }

        private TypeName stateTypeName() {
            return repository.entityStateType().typeName();
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                              .add("repository", repository)
                              .add("visibility", visibility)
                              .toString();
        }
    }
}
