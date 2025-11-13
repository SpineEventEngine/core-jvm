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

package io.spine.server.aggregate.model;

import io.spine.server.aggregate.AggregatePart;
import io.spine.server.aggregate.AggregateRoot;
import io.spine.server.entity.model.AbstractEntityFactory;
import io.spine.server.model.ModelError;

import java.io.Serial;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;

/**
 * Creates aggregate parts passing the root class to the constructor.
 *
 * @param <A>
 *         the type of the created aggregate parts
 */
final class PartFactory<A extends AggregatePart<?, ?, ?, ?>> extends AbstractEntityFactory<A> {

    @Serial
    private static final long serialVersionUID = 0L;

    private final Class<? extends AggregateRoot<?>> rootClass;

    PartFactory(Class<A> aggregatePartClass, Class<? extends AggregateRoot<?>> rootClass) {
        super(aggregatePartClass);
        this.rootClass = checkNotNull(rootClass);
    }

    /**
     * Creates an aggregate part with the passed root.
     */
    @Override
    public A create(Object root) {
        var ctor = constructor();
        try {
            var aggregatePart = ctor.newInstance(root);
            return aggregatePart;
        } catch (InvocationTargetException | InstantiationException | IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Obtains the constructor for the passed aggregate part class.
     *
     * <p>The part class must have a constructor with ID and {@code AggregateRoot} parameters.
     *
     * <p>Returns the constructor if the first parameter is aggregate ID
     * and the second constructor parameter is subtype of the {@code AggregateRoot}
     * For example:
     * <pre>{@code
     *  // A user-defined AggregateRoot:
     *  class CustomAggregateRoot extends AggregateRoot { ... }
     *
     *  // An AggregatePart for the CustomAggregateRoot:
     *  class CustomAggregatePart extends AggregatePart<...> { ... }
     *
     *  // The expected constructor:
     *  CustomAggregatePart(AnAggregateId id, CustomAggregateRoot root) { ... }
     * }</pre>
     *
     * <p>Throws {@code IllegalStateException} in other cases.
     *
     * @return the constructor
     * @throws ModelError if the entity class does not have the required constructor
     */
    @Override
    protected Constructor<A> findConstructor() {
        Class<? extends A> cls = entityClass();
        Constructor<? extends A> ctor;
        try {
            ctor = cls.getDeclaredConstructor(rootClass);
            ctor.setAccessible(true);
        } catch (NoSuchMethodException ignored) {
            throw noSuchConstructor(cls, rootClass);
        }
        @SuppressWarnings("unchecked") // The cast is protected by generic params.
        var result = (Constructor<A>) ctor;
        return result;
    }

    private static ModelError noSuchConstructor(Class<?> partClass, Class<?> rootClass) {
        var errMsg = format(
                "`%s` class must declare a constructor with one parameter of the `%s` type.",
                partClass.getName(),
                rootClass.getName());
        var cause = new NoSuchMethodException(errMsg);
        throw new ModelError(cause);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PartFactory<?> factory)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        return rootClass.equals(factory.rootClass);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), rootClass);
    }
}
