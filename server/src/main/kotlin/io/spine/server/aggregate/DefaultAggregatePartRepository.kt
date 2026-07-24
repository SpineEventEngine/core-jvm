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

@file:Suppress("DEPRECATION") // This repository serves the deprecated `AggregatePart` API.

package io.spine.server.aggregate

import io.spine.base.AggregateState
import io.spine.server.DefaultRepository
import io.spine.server.aggregate.model.AggregatePartClass
import io.spine.server.aggregate.model.AggregatePartClass.asAggregatePartClass
import io.spine.server.defaultRepositoryLogName

/**
 * Default implementation of `AggregatePartRepository`.
 *
 * @param I The type of aggregate IDs.
 * @param A The type of the stored aggregate part.
 * @param S The type of aggregate state.
 * @param R The type of aggregate root.
 * @param cls The class of aggregate parts managed by this repository.
 * @see io.spine.server.DefaultRepository
 */
@Deprecated(
    "This API does not provide isolation for an invariant. " +
        "To coordinate the work of several `Aggregate`s, please use a `ProcessManager` instead."
)
internal class DefaultAggregatePartRepository<I : Any,
                                              A : AggregatePart<I, S, *, R>,
                                              S : AggregateState<I>,
                                              R : AggregateRoot<I>>(
    cls: Class<A>
) : AggregatePartRepository<I, A, S, R>(), DefaultRepository {

    private val modelClass: AggregatePartClass<A> = asAggregatePartClass(cls)

    /**
     * Obtains the class of aggregate parts managed by this repository.
     */
    override fun entityModelClass(): AggregatePartClass<A> = modelClass

    override fun toString(): String = defaultRepositoryLogName(entityModelClass())
}
