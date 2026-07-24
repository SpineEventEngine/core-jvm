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

package io.spine.server.aggregate

import io.spine.annotation.Experimental
import io.spine.base.AggregateState
import io.spine.reflect.GenericTypeIndex
import io.spine.server.aggregate.model.AggregatePartClass
import io.spine.validation.ValidatingBuilder

/**
 * A part of a larger aggregate.
 *
 * Some business logic objects may be big enough.
 * If not all parts of such a business object need to be preserved at the same
 * time as business logic invariants, such an object can be split into several parts.
 *
 * Each such part would:
 *  - be a class derived from `AggregatePart`;
 *  - have the same aggregate ID as other parts belonging to the same business object;
 *  - have its own state defined as a Protobuf message;
 *  - be managed by a separate repository class derived from [AggregateRepository].
 *
 * To access parts of the aggregate, [AggregateRoot] should be used.
 *
 * If your business logic cannot be split into parts, it cannot be modified separately.
 * Consider extending [Aggregate] instead of several `AggregatePart`s.
 *
 * @param I The type for IDs of this class of aggregates.
 * @param S The type of the state held by the aggregate part.
 * @param B The type of the aggregate part state builder.
 * @param R The type of the aggregate root.
 *
 * @see Aggregate
 */
@Experimental
@Deprecated(
    "This API does not provide isolation for an invariant." +
            " To coordinate the work of several `Aggregate`s, please use a `ProcessManager`" +
            " instead."
)
public abstract class AggregatePart<I : Any,
                                    S : AggregateState<I>,
                                    B : ValidatingBuilder<S>,
                                    R : AggregateRoot<I>> :
    Aggregate<I, S, B> {

    private val root: R

    /**
     * Creates a new instance of the aggregate part.
     *
     * @param root A root of the aggregate to which this part belongs.
     */
    protected constructor(root: R) : super(root.id()) {
        this.root = root
    }

    /**
     * Obtains model class for this aggregate part.
     */
    override fun thisClass(): AggregatePartClass<*> = super.thisClass() as AggregatePartClass<*>

    @Suppress("DEPRECATION") // Calls into the deprecated `AggregatePartClass`.
    final override fun modelClass(): AggregatePartClass<*> =
        AggregatePartClass.asAggregatePartClass(javaClass)

    /**
     * Obtains a state of another `AggregatePart` by its class.
     *
     * @param P The type of the part state.
     * @param partStateClass The class of the state of the part.
     * @return The state of the part or a default state if the state was not found.
     * @throws IllegalStateException If a repository was not found, or the ID type of
     *   the part state does not match the ID type of the `root`.
     */
    protected fun <P : AggregateState<I>> partState(partStateClass: Class<P>): P =
        root.partState(partStateClass)

    /**
     * Enumeration of generic type parameters of this class.
     */
    public enum class GenericParameter(
        private val index: Int
    ) : GenericTypeIndex<AggregatePart<*, *, *, *>> {

        /** The index of the generic type `<I>`. */
        ID(0),

        /** The index of the generic type `<S>`. */
        STATE(1),

        /** The index of the generic type `<B>`. */
        STATE_BUILDER(2),

        /** The index of the generic type `<R>`. */
        AGGREGATE_ROOT(3);

        override fun index(): Int = index
    }
}
