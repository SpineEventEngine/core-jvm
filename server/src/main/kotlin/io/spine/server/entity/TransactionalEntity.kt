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

package io.spine.server.entity

import io.spine.annotation.Internal
import io.spine.annotation.VisibleForTesting
import io.spine.base.EntityState
import io.spine.core.Event
import io.spine.core.Version
import io.spine.validation.ConstraintViolation
import io.spine.validation.ValidatingBuilder
import io.spine.validation.ValidationException
import java.util.function.Consumer

/**
 * A base for entities that perform transactions with [events][Event].
 *
 * Defines a transaction-based mechanism for state, version, and lifecycle flags update.
 *
 * Exposes the [validating builder][builder] for the state as the only way
 * to modify the state from the descendants.
 *
 * @param I The type of the entity identifiers.
 * @param S The type of the entity state.
 * @param B The type of the builders for the entity state.
 */
@Suppress("TooManyFunctions") // The class is a base for entities, so it has many functions.
public abstract class TransactionalEntity<I : Any, S : EntityState<I>, B : ValidatingBuilder<S>> :
    AbstractEntity<I, S> {

    private val recentHistory = RecentHistory()

    /**
     * The flag that becomes `true` if the state of the entity has been changed
     * since it has been [loaded or created][RecordBasedRepository.findOrCreate].
     */
    @Volatile
    private var stateChanged: Boolean = false

    @Volatile
    private var transaction: Transaction<I, out TransactionalEntity<I, S, B>, S, B>? = null

    /**
     * Creates a new instance with the entity ID left unassigned.
     *
     * When this constructor is called, the ID must be set before any other
     * interactions with the instance.
     *
     * @see AbstractEntity
     */
    protected constructor() : super()

    /**
     * Creates a new instance.
     *
     * @param id The ID for the new instance.
     */
    protected constructor(id: I) : super(id)

    /**
     * Obtains recent history of events of this entity.
     */
    protected open fun recentHistory(): RecentHistory = recentHistory

    /**
     * Adds events to the [recent history][recentHistory].
     */
    protected open fun appendToRecentHistory(events: Iterable<Event>) {
        recentHistory.addAll(events)
    }

    /**
     * Clears [recent history][recentHistory].
     */
    protected open fun clearRecentHistory() {
        recentHistory.clear()
    }

    /**
     * A callback invoked before the transaction is committed.
     *
     * The developers of descending types may wish to override this method to implement
     * some common logic on modifying the entity state.
     */
    protected open fun onBeforeCommit() {
        // Do nothing by default.
    }

    /**
     * Triggers the [onBeforeCommit] callback from the transaction managing this entity.
     *
     * Declared here because [onBeforeCommit] is `protected`, while the [Transaction] that
     * invokes it resides in the same module but is not a subclass of this class.
     */
    @JvmSynthetic
    internal fun triggerOnBeforeCommit() {
        onBeforeCommit()
    }

    /**
     * Determines whether the state of this entity or its lifecycle flags have been modified
     * since this entity instance creation.
     *
     * This method is used internally to determine whether this entity instance should be
     * stored or the storage update can be skipped for this instance.
     *
     * @return `true` if the state or flags have been modified, `false` otherwise.
     */
    @Internal
    public fun changed(): Boolean {
        val lifecycleFlagsChanged = lifecycleFlagsChanged()
        val tx = transaction
        val effectiveStateChanged = tx?.stateChanged ?: this.stateChanged
        return effectiveStateChanged || lifecycleFlagsChanged
    }

    /**
     * Obtains the instance of the state builder.
     *
     * This method must be called only from within an active transaction.
     *
     * @return An instance of the new state builder.
     * @throws IllegalStateException If the method is called not within a transaction.
     */
    protected open fun builder(): B = tx().builder

    /**
     * Provides the `update` block for accessing properties of the entity
     * state [builder].
     *
     * For example, a receptor of a [ProcessManager][io.spine.server.procman.ProcessManager]
     * handling a command may look like this:
     *
     * ```kotlin
     * @Assign
     * fun handle(command: CreateTask): TaskCreated {
     *     val builder = update {
     *         title = command.title
     *         description = command.description
     *     }
     *     // Use `builder` properties, e.g., for creating the returned event.
     *     return taskCreated { title = builder.title }
     * }
     * ```
     *
     * **API Note:** This function is not `inline` because [builder] is
     * `protected` while inline functions can use only `public` API.
     *
     * @see alter for a version of this method that does not return a value.
     */
    @JvmSynthetic // Hidden from Java so the `Consumer`-based overload resolves unambiguously.
    protected fun update(block: B.() -> Unit): B {
        val builder = builder()
        block(builder)
        return builder
    }

    /**
     * A Java-friendly overload of [update].
     *
     * Accepts a `void`-compatible [block], so that Java code does not have to
     * return `Unit.INSTANCE` from the lambda.
     *
     * @param block The code to run on the builder.
     * @return The builder of the entity state.
     * @see update
     */
    protected fun update(block: Consumer<B>): B = update { block.accept(this) }

    /**
     * Provides the `alter` block for changing properties of the entity
     * state [builder].
     *
     * For example, a receptor of a [ProcessManager][io.spine.server.procman.ProcessManager]
     * handling a command may look like this:
     *
     * ```kotlin
     * @Assign
     * fun handle(command: CreateTask): TaskCreated {
     *     alter {
     *         title = command.title
     *         description = command.description
     *     }
     *     return taskCreated { title = command.title }
     * }
     * ```
     * **API Note:** This function is not `inline` because [builder] is
     * `protected` while inline functions can use only `public` API.
     *
     * @see update for a version of this method that returns the value of the builder.
     */
    @JvmSynthetic // Hidden from Java so the `Consumer`-based overload resolves unambiguously.
    protected fun alter(block: B.() -> Unit) {
        val builder = builder()
        block(builder)
    }

    /**
     * A Java-friendly overload of [alter].
     *
     * Accepts a `void`-compatible [block], so that Java code does not have to
     * return `Unit.INSTANCE` from the lambda.
     *
     * @param block The code to run on the builder.
     * @see alter
     */
    protected fun alter(block: Consumer<B>) {
        alter { block.accept(this) }
    }

    /**
     * Attempts to change the state of this entity, modifying the live state
     * [builder] only if the resulting state is valid.
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
     * **API Note:** This function is not `inline` because [builder] is
     * `protected` while inline functions can use only `public` API.
     *
     * This function is `public` and annotated [@VisibleForTesting][VisibleForTesting]:
     * tests may call it directly on an entity with an injected transaction.
     * Otherwise, the function would have been `protected`, because it can only be
     * used when a transaction is open, and the entity is inside one of its
     * receptor functions.
     *
     * @param block The mutation to attempt, running on a scratch copy of the live builder.
     * @return An empty list if the changes were applied to the live builder,
     *   or the constraint violations of the candidate state if the changes were withheld.
     * @see alter for the version of this method that applies changes unconditionally.
     */
    @JvmSynthetic // Hidden from Java so the `Consumer`-based overload resolves unambiguously.
    @VisibleForTesting
    public fun tryAlter(block: B.() -> Unit): List<ConstraintViolation> {
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
     * A Java-friendly overload of [tryAlter].
     *
     * Accepts a `void`-compatible [block], so that Java code does not have to
     * return `Unit.INSTANCE` from the lambda. Please see the primary overload
     * for the full contract, including the visibility note.
     *
     * @param block The mutation to attempt, running on a scratch copy of the live builder.
     * @return An empty list if the changes were applied to the live builder,
     *   or the constraint violations of the candidate state if the changes were withheld.
     * @see tryAlter
     */
    @VisibleForTesting
    public fun tryAlter(block: Consumer<B>): List<ConstraintViolation> =
        tryAlter { block.accept(this) }

    /**
     * Ensures that the entity has non-null active transaction.
     *
     * @throws IllegalStateException If the transaction is null or not active.
     */
    private fun ensureTransaction(): Transaction<I, out TransactionalEntity<I, S, B>, S, B> {
        check(isTransactionInProgress()) { missingTxMessage() }
        return checkNotNull(transaction)
    }

    /**
     * Provides error message text for the case of not having an active transaction when a state
     * modification call is made.
     */
    protected open fun missingTxMessage(): String =
        "Cannot modify entity state: transaction is not available."

    /**
     * Obtains the transaction used for modifying the entity.
     *
     * @throws IllegalStateException If the entity is not in the modification phase.
     */
    protected open fun tx(): Transaction<I, out TransactionalEntity<I, S, B>, S, B> =
        ensureTransaction()

    /**
     * Determines if the state update cycle is currently active.
     *
     * @return `true` if it is active, `false` otherwise.
     */
    @Internal
    protected fun isTransactionInProgress(): Boolean {
        val tx = transaction
        return tx != null && tx.isActive
    }

    /**
     * Injects the transaction wrapping this entity.
     *
     * @throws IllegalStateException If the given transaction is wrapped around another entity.
     */
    @Internal
    @JvmSynthetic // Hidden from Java: transaction control must stay within the framework's Kotlin.
    internal fun injectTransaction(tx: Transaction<I, out TransactionalEntity<I, S, B>, S, B>) {
        /*
            To ensure we are not hijacked, we must be sure that the transaction
            is injected to the very same object and wrapped into the transaction.
        */
        check(tx.entity === this) {
            "Transaction injected to this $this is wrapped around a different entity:" +
                    " `${tx.entity}`."
        }
        this.transaction = tx
    }

    /**
     * Releases the transaction that was modifying this entity.
     */
    @Internal
    @JvmSynthetic // Hidden from Java: transaction control must stay within the framework's Kotlin.
    internal fun releaseTransaction() {
        this.transaction = null
    }

    /**
     * Obtains the transaction that modifies this entity.
     *
     * This is a test-only method. For production purposes please use [tx].
     *
     * @return The instance of the transaction or `null` if the entity is not being modified.
     * @see tx
     */
    @Internal
    @VisibleForTesting
    @JvmSynthetic // Hidden from Java: transaction control must stay within the framework's Kotlin.
    internal fun transaction(): Transaction<I, out TransactionalEntity<I, S, B>, S, B>? =
        transaction

    /**
     * Updates own `stateChanged` flag from the underlying transaction.
     */
    @Internal
    @JvmSynthetic // Hidden from Java: transaction control must stay within the framework's Kotlin.
    internal fun updateStateChanged() {
        this.stateChanged = tx().stateChanged
    }

    /**
     * Sets an initial state for the entity.
     *
     * The execution of this method requires a [presence of active
     * transaction][isTransactionInProgress].
     */
    protected fun setInitialState(initialState: S, version: Version) {
        tx().initAll(initialState, version)
    }

    /**
     * Obtains the current state of the entity lifecycle flags.
     *
     * If the transaction is in progress, returns the lifecycle flags value for the transaction.
     */
    final override fun getLifecycleFlags(): LifecycleFlags =
        if (isTransactionInProgress()) {
            tx().lifecycleFlags
        } else {
            super.getLifecycleFlags()
        }

    /**
     * Sets `archived` status flag to the passed value.
     *
     * The execution of this method requires an [active transaction][isTransactionInProgress].
     */
    final override fun setArchived(archived: Boolean) {
        tx().setArchived(archived)
    }

    /**
     * Sets `deleted` status flag to the passed value.
     *
     * The execution of this method requires an [active transaction][isTransactionInProgress].
     */
    final override fun setDeleted(deleted: Boolean) {
        tx().setDeleted(deleted)
    }
}
