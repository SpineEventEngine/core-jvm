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
import io.spine.annotation.Internal;
import io.spine.base.AggregateState;
import io.spine.reflect.GenericTypeIndex;
import io.spine.server.aggregate.model.AggregatePartClass;
import io.spine.validation.ValidatingBuilder;

import static io.spine.server.aggregate.model.AggregatePartClass.asAggregatePartClass;

/**
 * A part of a larger aggregate.
 *
 * <p>Some business logic objects may be big enough.
 * If not all parts of such a business object need to be preserved at the same
 * time as business logic invariants, such an object can be split into several parts.
 *
 * <p>Each such part would:
 * <ul>
 *   <li>be a class derived from {@code AggregatePart}
 *   <li>have the same aggregate ID as other parts belonging to the same business object
 *   <li>have own state defined as a Protobuf message
 *   <li>managed by a separate repository class derived from {@link AggregateRepository}
 * </ul>
 *
 * <p>To access parts of the aggregate, {@link AggregateRoot} should be used.
 *
 * <p>If your business logic cannot be split into parts, it cannot be modified separately.
 * Consider extending {@link Aggregate} instead of several {@code AggregatePart}s.
 *
 * @param <I>
 *         the type for IDs of this class of aggregates
 * @param <S>
 *         the type of the state held by the aggregate part
 * @param <B>
 *         the type of the aggregate part state builder
 * @param <R>
 *         the type of the aggregate root
 * @see Aggregate
 */
@Experimental
public abstract class AggregatePart<I,
                                    S extends AggregateState<I>,
                                    B extends ValidatingBuilder<S>,
                                    R extends AggregateRoot<I>>
                      extends Aggregate<I, S, B> {

    private final R root;

    /**
     * Creates a new instance of the aggregate part.
     *
     * @param root a root of the aggregate to which this part belongs
     */
    protected AggregatePart(R root) {
        super(root.id());
        this.root = root;
    }

    /**
     * Obtains model class for this aggregate part.
     */
    @Override
    protected AggregatePartClass<?> thisClass() {
        return (AggregatePartClass<?>) super.thisClass();
    }

    @Internal
    @Override
    public final AggregatePartClass<?> modelClass() {
        return asAggregatePartClass(getClass());
    }

    /**
     * Obtains a state of another {@code AggregatePart} by its class.
     *
     * @param partStateClass the class of the state of the part
     * @param <P>            the type of the part state
     * @return the state of the part or a default state if the state was not found
     * @throws IllegalStateException if a repository was not found,
     *                               or the ID type of the part state does not match
     *                               the ID type of the {@code root}
     */
    protected <P extends AggregateState<I>> P partState(Class<P> partStateClass) {
        var partState = root.partState(partStateClass);
        return partState;
    }

    /**
     * Enumeration of generic type parameters of this class.
     */
    public enum GenericParameter implements GenericTypeIndex<AggregatePart<?, ?, ?, ?>> {

        /** The index of the generic type {@code <I>}. */
        ID(0),

        /** The index of the generic type {@code <S>}. */
        STATE(1),

        /** The index of the generic type {@code <B>}. */
        STATE_BUILDER(2),

        /** The index of the generic type {@code <R>}. */
        AGGREGATE_ROOT(3);

        private final int index;

        GenericParameter(int index) {
            this.index = index;
        }

        @Override
        public int index() {
            return this.index;
        }
    }
}
