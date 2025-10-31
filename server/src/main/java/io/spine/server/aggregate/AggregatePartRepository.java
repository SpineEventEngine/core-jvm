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

import com.google.errorprone.annotations.OverridingMethodsMustInvokeSuper;
import io.spine.annotation.Experimental;
import io.spine.annotation.Internal;
import io.spine.base.AggregateState;
import io.spine.server.BoundedContext;
import io.spine.server.aggregate.model.AggregatePartClass;

import static io.spine.server.aggregate.model.AggregatePartClass.asAggregatePartClass;

/**
 * Common abstract base for repositories that manage {@code AggregatePart}s.
 *
 * @param <I>
 *         the type of part identifiers
 * @param <A>
 *         the type of aggregate parts
 * @param <S>
 *         the type of the state of aggregate parts
 * @param <R>
 *         the type of the aggregate root associated with the type of parts
 */
@Experimental
public abstract class AggregatePartRepository<I,
                                              A extends AggregatePart<I, S, ?, R>,
                                              S extends AggregateState<I>,
                                              R extends AggregateRoot<I>>
                      extends AggregateRepository<I, A, S> {

    /**
     * Creates a new instance.
     */
    protected AggregatePartRepository() {
        super();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Registers itself with the {@link AggregateRootDirectory} of the context.
     */
    @Override
    @OverridingMethodsMustInvokeSuper
    public void registerWith(BoundedContext context) {
        super.registerWith(context);
        context.internalAccess()
               .aggregateRootDirectory()
               .register(this);
    }

    @Override
    public A create(I id) {
        var root = createAggregateRoot(id);
        var result = createAggregatePart(root);
        return result;
    }

    @Internal
    @Override
    protected final AggregatePartClass<A> toModelClass(Class<A> cls) {
        return asAggregatePartClass(cls);
    }

    AggregatePartClass<A> aggregatePartClass() {
        return (AggregatePartClass<A>) entityModelClass();
    }

    private AggregateRoot<I> createAggregateRoot(I id) {
        var result = aggregatePartClass().createRoot(context(), id);
        return result;
    }

    private A createAggregatePart(AggregateRoot<I> root) {
        return aggregatePartClass().create(root);
    }
}
