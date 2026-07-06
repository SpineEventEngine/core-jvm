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

@file:JvmName("TransactionalEntityExtensions")

package io.spine.server.entity

import io.spine.base.EntityState
import io.spine.core.Version
import io.spine.validation.ConstraintViolation
import io.spine.validation.ValidatingBuilder
import io.spine.validation.ValidationException

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
public fun <I : Any, E : TransactionalEntity<I, S, B>, S : EntityState<I>, B : ValidatingBuilder<S>>
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
public fun <I : Any, E : TransactionalEntity<I, S, B>, S : EntityState<I>, B : ValidatingBuilder<S>>
        E.alter(block: B.() -> Unit) {
    val builder = builder()
    block(builder)
}

/**
 * Attempts to change the state of this entity, modifying the live state
 * [builder][TransactionalEntity.builder] only if the resulting state is valid.
 *
 * The [block] runs on a scratch copy of the live builder, and the candidate state
 * it produces is validated before being applied:
 *
 *  1. If the candidate state is valid, the changes are merged into the live builder,
 *     and an empty list is returned.
 *  2. If the candidate state violates constraints declared in the entity state type,
 *     the live builder is left untouched, and the violations are returned.
 *
 * This makes `tryAlter` the validate-before-apply counterpart of [alter]: a receptor
 * can reject a command or skip a reaction *before* an invalid change reaches the live
 * builder, instead of failing the whole dispatch when the transaction is committed.
 *
 * For example, a reaction that reserves the ordered items only when the stock
 * allows it:
 *
 * ```kotlin
 * @React
 * fun on(event: BulkOrderPlaced): EitherOf2<StockReserved, NoReaction> {
 *     val violations = tryAlter {
 *         inStock -= event.quantity
 *     }
 *     return if (violations.isEmpty()) {
 *         EitherOf2.withA(stockReserved { /* … */ })
 *     } else {
 *         EitherOf2.withB(noReaction())
 *     }
 * }
 * ```
 *
 * A constraint enforced at setter time, such as `(set_once)`, makes the generated
 * builder throw [ValidationException] from inside [block] — against the scratch copy,
 * not the live builder. `tryAlter` catches this exception and returns its violations,
 * so both setter-time and build-time constraints surface uniformly as the returned
 * list. Any other exception thrown by [block] propagates unchanged, leaving the live
 * builder untouched.
 *
 * Consecutive calls compose: each call validates the cumulative candidate state,
 * including the changes applied by the preceding successful calls.
 *
 * To probe the current content of a builder in place — including content
 * that is already invalid — without the protective scratch copy,
 * use [ValidatingBuilder.validate].
 *
 * **Warning:** [block] must mutate only its receiver — the scratch builder.
 * Calling [alter] or [update] from inside [block] bypasses the scratch copy
 * and modifies the live builder directly, defeating the purpose of this function.
 * Also, avoid building other messages, such as events, inside [block]: any
 * [ValidationException] raised in [block] is reported as a withheld state change,
 * even if a foreign message failing its own validation caused it.
 *
 * **API Note:** This function is not `inline` because [TransactionalEntity.builder] is
 * `protected` while inline functions can use only `public` API.
 *
 * @param I the type of the entity identifiers.
 * @param E the type of the transactional entity.
 * @param S the type of the entity state.
 * @param B the type of the entity state builder.
 *
 * @param block the mutation to attempt, running on a scratch copy of the live builder.
 * @return an empty list if the changes were applied to the live builder,
 *   or the constraint violations of the candidate state if the changes were withheld.
 * @see alter for the version of this method that applies changes unconditionally.
 */
public fun <I : Any, E : TransactionalEntity<I, S, B>, S : EntityState<I>, B : ValidatingBuilder<S>>
        E.tryAlter(block: B.() -> Unit): List<ConstraintViolation> {
    val live = builder()
    @Suppress("UNCHECKED_CAST") // `clone()` of a builder returns the same builder type.
    val scratch = live.clone() as B
    try {
        block(scratch)
    } catch (e: ValidationException) {
        return e.constraintViolations
    }
    val candidate = scratch.buildPartial()
    val violations = checkEntityState(candidate)
    if (violations.isEmpty()) {
        // Merge in place: the open transaction keeps the reference to the live builder.
        live.clear()
        live.mergeFrom(candidate)
    }
    return violations
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
public val <I : Any, E : TransactionalEntity<I, S, B>, S : EntityState<I>, B : ValidatingBuilder<S>>
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
public val <I : Any, E : TransactionalEntity<I, S, B>, S : EntityState<I>, B : ValidatingBuilder<S>>
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
public val <I : Any, E : TransactionalEntity<I, S, B>, S : EntityState<I>, B : ValidatingBuilder<S>>
        E.state: S
    get() = state()
