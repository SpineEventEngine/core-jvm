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
package io.spine.server.stand;

import com.google.common.collect.ImmutableSet;
import io.spine.server.aggregate.AggregateRepository;
import io.spine.server.entity.Entity;
import io.spine.server.entity.QueryableRepository;
import io.spine.server.entity.Repository;
import io.spine.type.TypeUrl;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static java.util.Collections.synchronizedSet;
import static java.util.Optional.ofNullable;

/**
 * The in-memory concurrency-friendly implementation of
 * {@linkplain TypeRegistry Stand type registry}.
 */
final class InMemoryTypeRegistry implements TypeRegistry {

    /**
     * The mapping between {@code TypeUrl} instances and repositories providing
     * the entities of this type.
     */
    private final ConcurrentMap<TypeUrl, QueryableRepository<?, ?>> repositories =
            new ConcurrentHashMap<>();

    private final Set<TypeUrl> aggregateTypes = synchronizedSet(new HashSet<>());

    /** Prevents instantiation from the outside. */
    private InMemoryTypeRegistry() {
    }

    static TypeRegistry newInstance() {
        return new InMemoryTypeRegistry();
    }

    @Override
    @SuppressWarnings("ChainOfInstanceofChecks")
    public <I, E extends Entity<I, ?>> void register(Repository<I, E> repository) {
        var entityType = repository.entityStateType();

        if (repository instanceof QueryableRepository) {
            @SuppressWarnings("unchecked")  /* Guaranteed by the `QueryableRepository` contract. */
            var recordRepo = (QueryableRepository<I, ?>) repository;
            repositories.put(entityType, recordRepo);
        }
        if (repository instanceof AggregateRepository) {
            @SuppressWarnings("unchecked")
            AggregateRepository<I, ?, ?> aggRepository = (AggregateRepository<I, ?, ?>) repository;
            aggregateTypes.add(aggRepository.entityStateType());
        }
    }

    @Override
    public Optional<QueryableRepository<?, ?>> recordRepositoryOf(TypeUrl type) {
        var repo = repositories.get(type);
        Optional<QueryableRepository<?, ?>> result = ofNullable(repo);
        return result;
    }

    @Override
    public ImmutableSet<TypeUrl> aggregateTypes() {
        var result = ImmutableSet.copyOf(aggregateTypes);
        return result;
    }

    @Override
    public ImmutableSet<TypeUrl> allTypes() {
        return ImmutableSet.copyOf(repositories.keySet());
    }

    @Override
    public boolean isOpen() {
        return !repositories.isEmpty();
    }

    @Override
    public void close() {
        repositories.clear();
    }
}
