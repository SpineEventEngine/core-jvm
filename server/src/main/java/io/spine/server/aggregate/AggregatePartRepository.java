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

package io.spine.server.aggregate;

import com.google.errorprone.annotations.OverridingMethodsMustInvokeSuper;
import io.spine.annotation.Experimental;
import io.spine.annotation.Internal;
import io.spine.base.AggregateState;
import io.spine.server.BoundedContext;
import io.spine.server.aggregate.model.AggregatePartClass;
import io.spine.server.entity.EntityFactory;

import java.lang.reflect.Constructor;

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
 * @deprecated This API does not provide isolation for an invariant.
 *         To coordinate the work of several {@link Aggregate}s, please use
 *         a {@link io.spine.server.procman.ProcessManager ProcessManager} instead.
 */
@Experimental
@Deprecated
public abstract class AggregatePartRepository<I,
                                              A extends AggregatePart<I, S, ?, R>,
                                              S extends AggregateState<I>,
                                              R extends AggregateRoot<I>>
                      extends AggregateRepository<I, A, S> {

    /** The factory of aggregate parts. */
    private final EntityFactory<A> partFactory = new PartByIdFactory();

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

    /**
     * {@inheritDoc}
     *
     * <p>Unlike the default factory, which passes the identifier to the constructor of
     * the entity, the returned factory creates the {@linkplain AggregateRoot root} for
     * the passed identifier first, and then the part served by that root — the way
     * {@code AggregatePart}s are constructed.
     */
    @Override
    protected EntityFactory<A> entityFactory() {
        return partFactory;
    }

    @Internal
    @Override
    @SuppressWarnings("deprecation") // Calls into the deprecated `AggregatePartClass`.
    protected final AggregatePartClass<A> toModelClass(Class<A> cls) {
        return AggregatePartClass.asAggregatePartClass(cls);
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

    /**
     * Creates aggregate parts by their identifiers.
     *
     * <p>Creates the {@linkplain AggregateRoot root} for the passed identifier, and then
     * the part served by that root. The {@linkplain #constructor() constructor} is that
     * of the part class, taking the root.
     *
     * <p>Captures the enclosing repository and is therefore not practically serializable;
     * the framework never serializes the storage converters holding entity factories.
     */
    private final class PartByIdFactory implements EntityFactory<A> {

        private static final long serialVersionUID = 0L;

        @Override
        public A create(Object constructionArgument) {
            @SuppressWarnings("unchecked")
            var id = (I) constructionArgument;
            var root = createAggregateRoot(id);
            return createAggregatePart(root);
        }

        @Override
        public Constructor<A> constructor() {
            return aggregatePartClass().factory().constructor();
        }
    }
}
