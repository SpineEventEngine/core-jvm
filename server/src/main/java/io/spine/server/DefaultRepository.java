/*
 * Copyright 2025, TeamDev. All rights reserved.
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

package io.spine.server;

import io.spine.annotation.Internal;
import io.spine.server.aggregate.Aggregate;
import io.spine.server.aggregate.AggregatePart;
import io.spine.server.aggregate.DefaultAggregatePartRepository;
import io.spine.server.aggregate.DefaultAggregateRepository;
import io.spine.server.entity.Entity;
import io.spine.server.entity.Repository;
import io.spine.server.entity.model.EntityClass;
import io.spine.server.procman.DefaultProcessManagerRepository;
import io.spine.server.procman.ProcessManager;
import io.spine.server.projection.DefaultProjectionRepository;
import io.spine.server.projection.Projection;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.util.Exceptions.newIllegalArgumentException;
import static java.lang.String.format;

/**
 * The marker interface for the repositories which do not require creating a custom subclass
 * of {@link Repository}.
 *
 * <p>If no customization is required, {@link DefaultRepository#of} is the easiest way to create
 * an instance of repository for a given entity type.
 */
public interface DefaultRepository {

    /**
     * Creates default repository for the passed entity class.
     *
     * <p>Default repositories are useful when no customization (for example, custom routing)
     * is required for managing entities of the passed class.
     *
     * @param cls
     *         the class of entities
     * @param <I>
     *         the type of entity identifiers
     * @param <E>
     *         the type of entity
     * @return new repository instance
     */
    @SuppressWarnings({"unchecked", "rawtypes"}) // Casts are ensured by class assignability checks.
    static <I, E extends Entity<I, ?>> Repository<I, E> of(Class<E> cls) {
        /*
         * We deliberately "save" on OOP here and detect the class by the chain of if-s below
         * (instead of implementing this using the methods in the `EntityClass` hierarchy).
         * This is done to provide more convenient syntax for our framework users.
         */
        if (AggregatePart.class.isAssignableFrom(cls)) {
            return (Repository<I, E>) new DefaultAggregatePartRepository(cls);
        }
        if (Aggregate.class.isAssignableFrom(cls)) {
            return (Repository<I, E>) new DefaultAggregateRepository(cls);
        }
        if (ProcessManager.class.isAssignableFrom(cls)) {
            return (Repository<I, E>) new DefaultProcessManagerRepository(cls);
        }
        if (Projection.class.isAssignableFrom(cls)) {
            return (Repository<I, E>) new DefaultProjectionRepository(cls);
        }
        throw newIllegalArgumentException(
                "No default repository implementation available for the class `%s`.", cls
        );
    }

    /** Obtains the class of the managed entity. */
    @Internal
    EntityClass<?> entityModelClass();

    /** Obtains the logging name of this repository. */
    @Internal
    default String logName() {
        checkNotNull(entityModelClass());
        return format("%s.of(%s.class)",
                      DefaultRepository.class.getSimpleName(),
                      entityModelClass().rawClass().getSimpleName());
    }
}
