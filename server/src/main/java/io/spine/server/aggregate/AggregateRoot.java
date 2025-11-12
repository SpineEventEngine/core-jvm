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

import io.spine.annotation.Experimental;
import io.spine.base.AggregateState;
import io.spine.server.BoundedContext;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.util.Exceptions.newIllegalStateException;

/**
 * A root object for a larger aggregate.
 *
 * @param <I> the type for IDs of this class of aggregates
 */
@Experimental
public class AggregateRoot<I> {

    /** The context to which the aggregate belongs. */
    private final BoundedContext context;

    /** The aggregate ID. */
    private final I id;

    /**
     * Creates a new instance.
     *
     * @param context the bounded context to which the aggregate belongs
     * @param id             the ID of the aggregate
     */
    protected AggregateRoot(BoundedContext context, I id) {
        this.context = checkNotNull(context);
        this.id = checkNotNull(id);
    }

    /**
     * Obtains the aggregate ID.
     */
    public I id() {
        return this.id;
    }

    /**
     * Obtains a part state by its class.
     *
     * @param partStateClass
     *         the class of the state of the part
     * @param <S>
     *         the type of the part state
     * @return the state of the part or a default state if the state was not found
     * @throws IllegalStateException
     *         if a repository was not found, or the ID type of the part state does not match
     *         the ID type of this {@code AggregateRoot}
     */
    protected <S extends AggregateState<I>, A extends AggregatePart<I, S, ?, ?>>
    S partState(Class<S> partStateClass) {
        AggregatePartRepository<I, A, S, ?> repo = repositoryOf(partStateClass);
        AggregatePart<I, S, ?, ?> aggregatePart = repo.loadOrCreate(id());
        var partState = aggregatePart.state();
        return partState;
    }

    /**
     * Obtains a repository for the passed state class.
     *
     * @throws IllegalStateException
     *         if a repository was not found, or the repository ID type does not match the ID type
     *         of this {@code AggregateRoot}
     */
    @SuppressWarnings("unchecked") // We ensure ID type when adding to the map.
    private <S extends AggregateState<I>, A extends AggregatePart<I, S, ?, ?>>
    AggregatePartRepository<I, A, S, ?> repositoryOf(Class<S> stateClass) {
        var thisType = (Class<? extends AggregateRoot<?>>) getClass();
        Optional<? extends AggregatePartRepository<?, ?, ?, ?>> partRepository =
                context.internalAccess()
                       .aggregateRootDirectory()
                       .findPart(thisType, stateClass);
        AggregatePartRepository<?, ?, ?, ?> repository = partRepository.orElseThrow(
                () -> newIllegalStateException("Could not find repository for aggregate part `%s`.",
                                               stateClass.getName())
        );
        var result = (AggregatePartRepository<I, A, S, ?>) repository;
        return result;
    }
}
