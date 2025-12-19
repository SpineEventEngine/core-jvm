/*
 * Copyright 2023, TeamDev. All rights reserved.
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

@file:JvmName("TransactionalEntityExtensions")

package io.spine.server.entity

import io.spine.base.EntityState
import io.spine.core.Version
import io.spine.validation.ValidatingBuilder

/**
 * Extends [TransactionalEntity] with the `update` block for accessing
 * properties of the entity state [builder][TransactionalEntity.builder].
 *
 * For example, a method of an [Aggregate][io.spine.server.aggregate.Aggregate] that
 * applies an event may look like this:
 *
 * ```kotlin
 * @Apply
 * fun event(e: TaskCreated) {
 *     val builder = update {
 *         title = e.title
 *         description = e.description
 *     }
 *     // Use `builder` properties.
 * }
 * ```
 *
 * **API Note:** This function is not `inline` because [TransactionalEntity.builder] is
 * `protected` while inline functions can use only `public` API.
 *
 * @param I the type of the entity identifiers.
 * @param E the type of the transactional entity.
 * @param S the type of the entity state.
 * @param B the type of the entity state builder.
 *
 * @see alter for a version of this method that does not return a value.
 */
public fun <I, E : TransactionalEntity<I, S, B>, S : EntityState<I>, B : ValidatingBuilder<S>>
        E.update(block: B.() -> Unit): B {
    val builder = builder()
    block(builder)
    return builder
}

/**
 * Extends [TransactionalEntity] with the `alter` block for changing
 * properties of the entity state [builder][TransactionalEntity.builder].
 *
 * For example, a method of an [Aggregate][io.spine.server.aggregate.Aggregate] that
 * applies an event may look like this:
 *
 * ```kotlin
 * @Apply
 * fun event(e: TaskCreated) = alter {
 *     title = e.title
 *     description = e.description
 * }
 * ```
 * **API Note:** This function is not `inline` because [TransactionalEntity.builder] is `protected`
 * while inline functions can use only `public` API.
 *
 * @param I the type of the entity identifiers.
 * @param E the type of the transactional entity.
 * @param S the type of the entity state.
 * @param B the type of the entity state builder.
 *
 * @see update for a version of this method that returns the value of the builder.
 */
public fun <I, E : TransactionalEntity<I, S, B>, S : EntityState<I>, B : ValidatingBuilder<S>>
        E.alter(block: B.() -> Unit) {
    val builder = builder()
    block(builder)
}

/**
 * Obtains the entity identifier.
 *
 * This is a shortcut for `id()`.
 *
 * @param I the type of the entity identifiers.
 * @param E the type of the transactional entity.
 * @param S the type of the entity state.
 * @param B the type of the entity state builder.
 */
public val <I, E : TransactionalEntity<I, S, B>, S : EntityState<I>, B : ValidatingBuilder<S>>
        E.id: I
    get() = id()

/**
 * Obtains the entity version.
 *
 * This is a shortcut for `version()`.
 *
 * @param I the type of the entity identifiers.
 * @param E the type of the transactional entity.
 * @param S the type of the entity state.
 * @param B the type of the entity state builder.
 */
public val <I, E : TransactionalEntity<I, S, B>, S : EntityState<I>, B : ValidatingBuilder<S>>
        E.version: Version
    get() = version()

/**
 * Obtains the entity state.
 *
 * This is a shortcut for `state()`.
 *
 * @param I the type of the entity identifiers.
 * @param E the type of the transactional entity.
 * @param S the type of the entity state.
 * @param B the type of the entity state builder.
 */
public val <I, E : TransactionalEntity<I, S, B>, S : EntityState<I>, B : ValidatingBuilder<S>>
        E.state: S
    get() = state()
