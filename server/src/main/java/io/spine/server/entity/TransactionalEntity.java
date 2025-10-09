/*
 * Copyright 2022, TeamDev. All rights reserved.
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
package io.spine.server.entity;

import io.spine.annotation.Internal;
import io.spine.annotation.VisibleForTesting;
import io.spine.base.EntityState;
import io.spine.core.Event;
import io.spine.core.Version;
import io.spine.validate.ValidatingBuilder;
import org.jspecify.annotations.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

/**
 * A base for entities, perform transactions {@linkplain Event events}.
 *
 * <p>Defines a transaction-based mechanism for state, version, and lifecycle flags update.
 *
 * <p>Exposes {@linkplain #builder()} validating builder} for the state as the only way
 * to modify the state from the descendants.
 *
 * @param <I>
 *         the type of the entity identifiers
 * @param <S>
 *         the type of the entity state
 * @param <B>
 *         the type of the builders for the entity state
 */
public abstract
class TransactionalEntity<I, S extends EntityState<I>, B extends ValidatingBuilder<S>>
        extends AbstractEntity<I, S> {

    private final RecentHistory recentHistory = new RecentHistory();

    /**
     * The flag which becomes {@code true} if the state of the entity has been changed
     * since it has been {@linkplain RecordBasedRepository#findOrCreate(Object) loaded or created}.
     */
    private volatile boolean stateChanged;

    private volatile
    @Nullable Transaction<I, ? extends TransactionalEntity<I, S, B>, S, B> transaction;

    /**
     * Creates a new instance.
     */
    protected TransactionalEntity() {
        super();
    }

    /**
     * Creates a new instance.
     *
     * @param id the ID for the new instance
     */
    protected TransactionalEntity(I id) {
        super(id);
    }

    /**
     * Obtains recent history of events of this entity.
     */
    protected RecentHistory recentHistory() {
        return recentHistory;
    }

    /**
     * Adds events to the {@linkplain #recentHistory() recent history}.
     */
    protected void appendToRecentHistory(Iterable<Event> events) {
        recentHistory.addAll(events);
    }

    /**
     * Clears {@linkplain #recentHistory() recent history}.
     */
    protected void clearRecentHistory() {
        recentHistory.clear();
    }

    /**
     * A callback invoked before the transaction is committed.
     *
     * <p>The developers of descending types may wish to override this method to implement
     * some common logic on modifying the entity state.
     */
    @SuppressWarnings("NoopMethodInAbstractClass")  // The method does nothing by default.
    protected void onBeforeCommit() {
        // do nothing by default.
    }

    /**
     * Determines whether the state of this entity or its lifecycle flags have been modified
     * since this entity instance creation.
     *
     * <p>This method is used internally to determine whether this entity instance should be
     * stored or the storage update can be skipped for this instance.
     *
     * @return {@code true} if the state or flags have been modified, {@code false} otherwise.
     */
    @Internal
    public final boolean changed() {
        var lifecycleFlagsChanged = lifecycleFlagsChanged();
        Transaction<?, ?, ?, ?> tx = this.transaction;
        var stateChanged = tx != null
                           ? tx.stateChanged()
                           : this.stateChanged;
        return stateChanged || lifecycleFlagsChanged;
    }

    /**
     * Obtains the instance of the state builder.
     *
     * <p>This method must be called only from within an active transaction.
     *
     * @return the instance of the new state builder
     * @throws IllegalStateException if the method is called not within a transaction
     */
    protected B builder() {
        return tx().builder();
    }

    /**
     * Ensures that the entity has non-null active transaction.
     *
     * @throws IllegalStateException if the transaction is null or not active
     */
    @SuppressWarnings("ConstantConditions") // we check tx is non-null explicitly
    private Transaction<I, ? extends TransactionalEntity<I, S, B>, S, B> ensureTransaction() {
        if (!isTransactionInProgress()) {
            throw new IllegalStateException(missingTxMessage());
        }
        return requireNonNull(transaction);
    }

    /**
     * Provides error message text for the case of not having an active transaction when a state
     * modification call is made.
     */
    protected String missingTxMessage() {
        return "Cannot modify entity state: transaction is not available.";
    }

    /**
     * Obtains the transaction used for modifying the entity.
     *
     * @throws IllegalStateException
     *         if the entity is not in the modification phase
     */
    protected Transaction<I, ? extends TransactionalEntity<I, S, B>, S, B> tx() {
        return ensureTransaction();
    }

    /**
     * Determines if the state update cycle is currently active.
     *
     * @return {@code true} if it is active, {@code false} otherwise
     */
    @Internal
    protected final boolean isTransactionInProgress() {
        Transaction<?, ?, ?, ?> tx = this.transaction;
        var result = tx != null && tx.isActive();
        return result;
    }

    @SuppressWarnings({"ObjectEquality", "ReferenceEquality", "PMD.CompareObjectsWithEquals"}
            /* The refs must to point to the same object; see below. */)
    final void injectTransaction(Transaction<I, ? extends TransactionalEntity<I, S, B>, S, B> tx) {
        checkNotNull(tx);
        /*
            To ensure we are not hijacked, we must be sure that the transaction
            is injected to the very same object and wrapped into the transaction.
        */
        checkState(tx.entity() == this,
                   "Transaction injected to this %s" +
                           " is wrapped around a different entity: `%s`.", this, tx.entity());

        this.transaction = tx;
    }

    final void releaseTransaction() {
        this.transaction = null;
    }

    /**
     * Obtains the transaction which modifies this entity.
     *
     * <p>This is a test-only method. For production purposes please use {@link #tx()}.
     *
     * @return the instance of the transaction or {@code null} if the entity is not being modified
     * @see #tx()
     */
    @VisibleForTesting
    final @Nullable Transaction<I, ? extends TransactionalEntity<I, S, B>, S, B> transaction() {
        return transaction;
    }

    /**
     * Updates own {@code stateChanged} flag from the underlying transaction.
     */
    final void updateStateChanged() {
        this.stateChanged = tx().stateChanged();
    }

    /**
     * Sets an initial state for the entity.
     *
     * <p>The execution of this method requires a {@linkplain #isTransactionInProgress() presence
     * of active transaction}.
     */
    protected final void setInitialState(S initialState, Version version) {
        tx().initAll(initialState, version);
    }

    /**
     * Obtains the current state of the entity lifecycle flags.
     *
     * <p>If the transaction is in progress, returns the lifecycle flags value for the transaction.
     */
    @Override
    public final LifecycleFlags getLifecycleFlags() {
        if (isTransactionInProgress()) {
            return tx().lifecycleFlags();
        }
        return super.getLifecycleFlags();
    }

    /**
     * {@inheritDoc}
     *
     * <p>The execution of this method requires an {@linkplain #isTransactionInProgress()
     * active transaction}.
     */
    @Override
    protected final void setArchived(boolean archived) {
        tx().setArchived(archived);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The execution of this method requires an {@linkplain #isTransactionInProgress()
     * active transaction}.
     */
    @Override
    protected final void setDeleted(boolean deleted) {
        tx().setDeleted(deleted);
    }
}
