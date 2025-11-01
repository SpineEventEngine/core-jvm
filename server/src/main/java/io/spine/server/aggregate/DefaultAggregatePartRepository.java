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

package io.spine.server.aggregate;

import io.spine.annotation.Internal;
import io.spine.base.AggregateState;
import io.spine.server.DefaultRepository;
import io.spine.server.aggregate.model.AggregatePartClass;

import static io.spine.server.aggregate.model.AggregatePartClass.asAggregatePartClass;

/**
 * Default implementation of {@code AggregatePartRepository}.
 *
 * @param <I>
 *         the type of aggregate IDs
 * @param <A>
 *         the type of the stored aggregate part
 * @param <S>
 *         the type of aggregate state
 * @param <R>
 *         the type of an aggregate root
 * @see io.spine.server.DefaultRepository
 */
@Internal
public final class DefaultAggregatePartRepository<I,
                                                  A extends AggregatePart<I, S, ?, R>,
                                                  S extends AggregateState<I>,
                                                  R extends AggregateRoot<I>>
        extends AggregatePartRepository<I, A, S, R>
        implements DefaultRepository {

    private final AggregatePartClass<A> modelClass;

    /**
     * Creates a new repository for managing aggregate parts of the passed class.
     */
    public DefaultAggregatePartRepository(Class<A> cls) {
        super();
        this.modelClass = asAggregatePartClass(cls);
    }

    /**
     * Obtains the class of aggregates parts managed by this repository.
     */
    @Override
    public AggregatePartClass<A> entityModelClass() {
        return modelClass;
    }

    @Override
    public String toString() {
        return logName();
    }
}
