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
import io.spine.server.Closeable;
import io.spine.server.entity.Entity;
import io.spine.server.entity.QueryableRepository;
import io.spine.server.entity.RecordBasedRepository;
import io.spine.server.entity.Repository;
import io.spine.type.TypeUrl;

import java.util.Optional;

/**
 * The registry of all {@linkplain TypeUrl types} managed by an instance of {@linkplain Stand}.
 *
 * <p>In addition to types, manages the information about the {@linkplain Repository repositories}
 * for the objects of known types.
 */
interface TypeRegistry extends Closeable {

    /**
     * Registers a {@linkplain Repository repository} of objects and
     * {@linkplain Repository#entityStateType its entity state type} in this registry.
     *
     * <p>For {@linkplain RecordBasedRepository record-based repositories},
     * the reference to the {@code repository} is also kept to allow accessing its records
     * from {@code Stand}.
     *
     * <p>In case {@link io.spine.server.aggregate.AggregateRepository AggregateRepository}
     * instance is passed, only its {@code type} is registered.
     */
    <I, E extends Entity<I, ?>> void register(Repository<I, E> repository);

    /**
     * Obtains the instance of {@linkplain RecordBasedRepository repository} for the passed
     * {@linkplain TypeUrl type}, if it {@linkplain #register(Repository) has been registered}
     * previously.
     *
     * @param type
     *         the type of {@code Entity} to obtain a repository for
     * @return {@code RecordBasedRepository} managing the objects of the given {@code type},
     *         or {@code Optional.empty()} if no such repository has been registered
     */
    Optional<QueryableRepository<?, ?>> recordRepositoryOf(TypeUrl type);

    /**
     * Reads all {@link io.spine.server.aggregate.Aggregate Aggregate} entity state types
     * registered in this instance of registry.
     *
     * @return the set of types as {@link TypeUrl} instances
     */
    ImmutableSet<TypeUrl> aggregateTypes();

    /**
     * Reads all entity types, which repositories are registered in this instance of registry.
     *
     * <p>The result includes all values from {@link #aggregateTypes()} as well.
     *
     * @return the set of types as {@link TypeUrl} instances
     */
    ImmutableSet<TypeUrl> allTypes();
}
