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

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.spine.annotation.Internal;
import io.spine.annotation.VisibleForTesting;
import io.spine.base.EntityState;
import io.spine.base.Error;
import io.spine.base.Identifier;
import io.spine.core.Event;
import io.spine.core.MessageId;
import io.spine.core.Version;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.dispatch.DispatchOutcomeHandler;
import io.spine.validate.NonValidated;
import io.spine.validate.ValidatingBuilder;

import java.util.List;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Lists.newLinkedList;
import static io.spine.base.Errors.causeOf;
import static io.spine.core.Versions.checkIsIncrement;
import static io.spine.protobuf.AnyPacker.pack;
import static java.lang.String.format;

/**
 * The abstract class for the {@linkplain TransactionalEntity entity} transactions.
 *
 * <p>The transaction is a set of changes made to an entity state or entity attributes
 * (e.g. version, lifecycle flags etc).
 *
 * <p>Serves as a buffer, accumulating the changes, intended for the enclosed {@code Entity};
 * the changes are only applied to the actual object upon {@linkplain #commit() commit}.
 *
 * <p>The transaction is injected to the entity, which state should be modified. By doing so,
 * the {@linkplain Transaction#builder() "buffering" builder} is exposed to concrete
 * {@code TransactionalEntity} subclasses. In turn, they receive an ability to change the entity
 * state by modifying {@link TransactionalEntity#builder() entity state builder}.
 *
 * <p>Same applies to the entity lifecycle flags.
 *
 * <p>Version management is performed automatically by the transaction itself.
 *
 * @param <I>
 *         the type of entity IDs
 * @param <E>
 *         the type of entity
 * @param <S>
 *         the type of entity state
 * @param <B>
 *         the type of a {@code ValidatingBuilder} for the entity state
 */
@SuppressWarnings("ClassWithTooManyMethods")
@Internal
public abstract class Transaction<I,
                                  E extends TransactionalEntity<I, S, B>,
                                  S extends EntityState<I>,
                                  B extends ValidatingBuilder<S>> {

    /**
     * The entity, which state and attributes are modified in this transaction.
     */
    private final E entity;

    /**
     * The state of the entity before the beginning of the transaction.
     */
    private final S initialState;

    /**
     * The version of the entity before the beginning of the transaction.
     */
    private final Version initialVersion;

    /**
     * The builder for the entity state at the current phase of the transaction.
     *
     * <p>All the state changes made within the transaction go to this {@code Builder},
     * and not to the {@code Entity} itself.
     *
     * @see #propagate(Phase)
     * @see #commit()
     */
    private B builder;

    /**
     * The {@link EntityRecord} containing the entity data and meta-info before the transaction
     * start.
     */
    private final EntityRecord entityBeforeTransaction;

    /**
     * The version of the entity, modified within this transaction.
     *
     * <p>All the version changes made within the transaction are stored in this variable,
     * and not in the {@code Entity} itself.
     *
     * <p>This value is propagated to the entity upon the {@linkplain #commit() commit()}.
     */
    private Version version;

    /**
     * The lifecycle flags of the entity, modified within this transaction.
     *
     * <p>All the entity lifecycle changes made within the transaction are stored in this variable,
     * and not in the {@code Entity} itself.
     *
     * <p>This value is set to the entity upon the {@linkplain #commit() commit()}.
     */
    private LifecycleFlags lifecycleFlags;

    /**
     * The flag, which becomes {@code true}, if the state of the entity
     * {@linkplain #commit() has been changed} since it has been
     * {@linkplain RecordBasedRepository#findOrCreate(Object)} loaded or created.
     */
    private boolean stateChanged;

    /**
     * Allows to understand whether this transaction is active.
     *
     * <p>Has {@code true} value since the transaction instance creation
     * until {@linkplain #commit() commit()} is performed.
     */
    @SuppressWarnings("UnusedAssignment") // is used to make the initial tx. state explicit.
    private boolean active = false;

    /**
     * An ordered collection of the phases that were propagated in scope of this transaction.
     *
     * <p>Contains all the phases, including failed.
     */
    private final List<Phase<I>> phases = newLinkedList();

    private TransactionListener<I> transactionListener;

    /**
     * Creates a new instance of {@code Transaction} and
     * {@linkplain TransactionalEntity#injectTransaction(Transaction) injects} the newly created
     * transaction into the given {@code entity}.
     *
     * <p>The entity state and attributes are set as starting values for this transaction.
     *
     * @param entity
     *         the entity to create the transaction for
     * @see TransactionListener
     */
    protected Transaction(E entity) {
        this.entity = checkNotNull(entity);
        this.initialState = entity.state();
        this.initialVersion = entity.version();
        this.builder = toBuilder(entity);
        this.version = entity.version();
        this.lifecycleFlags = entity.lifecycleFlags();
        this.active = true;

        this.transactionListener = new SilentWitness<>();
        injectTo(entity);
        this.entityBeforeTransaction = entityRecord();
    }

    /**
     * Creates the builder for being used by a transaction when modifying the passed entity.
     *
     * <p>If the entity has the default state, and the first field of the state is its ID, and
     * the field is required, initializes the builder with the value of the entity ID.
     */
    @VisibleForTesting
    static <I,
            E extends TransactionalEntity<I, S, B>,
            S extends EntityState<I>,
            B extends ValidatingBuilder<S>>
    B toBuilder(E entity) {
        var currentState = entity.state();
        @SuppressWarnings("unchecked") // ensured by argument of <E>.
        var result = (B) currentState.toBuilder();

        if (currentState.equals(entity.defaultState())) {
            var idField = IdField.of(entity.modelClass());
            idField.initBuilder(result, entity.id());
        }
        return result;
    }

    /**
     * Acts similar to {@linkplain Transaction#Transaction(TransactionalEntity)
     * an overloaded ctor}, but instead of using the original entity state and version,
     * this transaction will have the passed state and version as a starting point.
     *
     * <p>Note, that the given {@code state} and {@code version} are applied to the actual entity
     * upon commit.
     *
     * @param entity
     *         the target entity to modify within this transaction
     * @param state
     *         the entity state to set
     * @param version
     *         the entity version to set
     */
    protected Transaction(E entity, S state, Version version) {
        this(entity);
        initAll(state, version);
    }

    /**
     * Allows to understand whether this transaction is active.
     *
     * @return {@code true} if the transaction is active, {@code false} otherwise
     */
    @VisibleForTesting
    final boolean isActive() {
        return active;
    }

    /**
     * Returns the value of the lifecycle flags, as of the current state within this transaction.
     */
    protected LifecycleFlags lifecycleFlags() {
        return lifecycleFlags;
    }

    /**
     * Obtains the {@link MessageId} of the entity under the transaction.
     */
    MessageId entityId() {
        var typeUrl = entity.state().typeUrl();
        return MessageId.newBuilder()
                .setId(Identifier.pack(entity.id()))
                .setTypeUrl(typeUrl.value())
                .setVersion(entity.version())
                .build();
    }

    protected final E entity() {
        return entity;
    }

    /**
     * Returns the version of the entity, modified within this transaction.
     */
    protected final Version version() {
        return version;
    }

    final List<Phase<I>> phases() {
        return ImmutableList.copyOf(phases);
    }

    /**
     * Propagates a phase and performs a rollback in case of an error.
     *
     * <p>The transaction {@linkplain #listener() listener} is called for both failed and
     * successful phases.
     *
     * <p>If the signal propagation cases an error, a rejection, or an unhandled exception,
     * the transaction is rolled back.
     *
     * @param phase
     *         the phase to propagate
     * @return the phase propagation result
     */
    @CanIgnoreReturnValue
    protected final DispatchOutcome propagate(Phase<I> phase) {
        var listener = listener();
        listener.onBeforePhase(phase);
        var outcome = propagateFailsafe(phase);
        phases.add(phase);
        listener.onAfterPhase(phase);
        return outcome;
    }

    /**
     * Propagates the given phase and catches failures if any.
     *
     * <p>The catch block in this method and in {@link #commit()} prevents from force majeure
     * situations such as storage failures, etc. All the exceptions produced in the framework users'
     * code are handled before this failsafe and is already packed in the {@code DispatchOutcome}
     * produced by the phase.
     *
     * @see #propagate(Phase)
     */
    private DispatchOutcome propagateFailsafe(Phase<I> phase) {
        try {
            var outcome = phase.propagate();
            return DispatchOutcomeHandler.from(outcome)
                    .onError(this::rollback)
                    .onRejection(this::rollback)
                    .handle();
        } catch (Throwable t) {
            var rootCause = causeOf(t);
            rollback(rootCause);
            return DispatchOutcome.newBuilder()
                    .setPropagatedSignal(phase.signal().messageId())
                    .setError(rootCause)
                    .build();
        }
    }

    /**
     * Advances the state and the version of the entity being built by the transaction after
     * the message was successfully dispatched.
     *
     * <p>This is needed for the cases of dispatching more than one message during a transaction.
     * After the state is propagated to the entity, its message handler which is invoked during
     * the next step would “see” the {@linkplain Entity#state() state of the entity}.
     *
     * @param increment
     *         the strategy for incrementing the version
     */
    @SuppressWarnings("unchecked")
    final void incrementStateAndVersion(VersionIncrement increment) {
        var nextVersion = increment.nextVersion();
        checkIsIncrement(version(), nextVersion);
        setVersion(nextVersion);
        var newState = builder().build();
        builder = (B) newState.toBuilder();
        entity().updateState(newState, nextVersion);
    }

    /**
     * Commits this transaction if it is still active.
     *
     * <p>If the transaction is not active, does nothing.
     *
     * @see #commit()
     */
    public final void commitIfActive() throws InvalidEntityStateException, IllegalStateException {
        if (active) {
            commit();
        }
    }

    /**
     * Applies all the outstanding modifications to the enclosed entity.
     *
     * @throws InvalidEntityStateException
     *         in case the new entity state is not valid
     * @throws IllegalStateException
     *         in case of a generic error
     */
    @VisibleForTesting
    public final void commit() throws InvalidEntityStateException, IllegalStateException {
        executeOnBeforeCommit();
        var newState = builder().buildPartial();
        doCommit(newState);
    }

    /**
     * Commits this transaction and sets the new state to the entity.
     *
     * <p>In case there are no entity state changes, still checks the entity column values and meta
     * attributes for updates, as these values may change independently of entity state.
     *
     * <p>In case something goes wrong during the commit, the transaction is rolled back and the
     * entity keeps its current state.
     */
    private void doCommit(@NonValidated S newState) {
        try {
            var pendingVersion = version();
            beforeCommit(newState, pendingVersion);
            updateState(newState);
            updateVersion();
            updateStateChanged();
            commitAttributeChanges();
            var newRecord = entityRecord();
            afterCommit(newRecord);
        } catch (RuntimeException e) {
            rollback(causeOf(e));
        } finally {
            releaseTx();
        }
    }

    /**
     * Propagates the state update to the entity.
     */
    private void updateState(@NonValidated S newState) {
        if (!initialState.equals(newState)) {
            entity.updateState(newState);
        }
    }

    /**
     * Propagates the version update to the entity.
     */
    private void updateVersion() {
        var pending = version();
        if (!pending.equals(entity.version())) {
            entity.updateVersion(pending);
        }
    }

    /**
     * Marks entity state as changed if there are any changes.
     *
     * <p>This triggers the storage mechanism.
     */
    private void updateStateChanged() {
        if (!entity.state().equals(initialState)) {
            markStateChanged();
        }
    }

    /**
     * Turns the transaction into inactive state.
     */
    @VisibleForTesting
    final void deactivate() {
        this.active = false;
    }

    private void executeOnBeforeCommit() {
        entity.onBeforeCommit();
    }

    private void beforeCommit(S newState, Version newVersion) {
        var newFlags = lifecycleFlags();
        @NonValidated EntityRecord record = EntityRecord.newBuilder()
                .setEntityId(Identifier.pack(entity.id()))
                .setState(pack(newState))
                .setLifecycleFlags(newFlags)
                .setVersion(newVersion)
                .buildPartial();
        transactionListener.onBeforeCommit(record);
    }

    private void afterCommit(EntityRecord newEntity) {
        var change = EntityRecordChange.newBuilder()
                .setPreviousValue(entityBeforeTransaction)
                .setNewValue(newEntity)
                .build();
        transactionListener.onAfterCommit(change);
    }

    /**
     * Cancels the changes made within this transaction and removes the injected transaction object
     * from the enclosed entity.
     *
     * @param cause
     *         the reason of the rollback
     */
    @VisibleForTesting
    final void rollback(Error cause) {
        doRollback(record -> listener().onTransactionFailed(cause, record));
    }

    /**
     * Cancels the changes made within this transaction and removes the injected transaction object
     * from the enclosed entity.
     *
     * @param cause
     *         the reason of the rollback
     */
    private void rollback(Event cause) {
        doRollback(record -> listener().onTransactionFailed(cause, record));
    }

    private void doRollback(Consumer<EntityRecord> recordConsumer) {
        @NonValidated EntityRecord record = EntityRecord.newBuilder()
                .setEntityId(Identifier.pack(entity.id()))
                .setState(pack(currentBuilderState()))
                .setVersion(version)
                .setLifecycleFlags(lifecycleFlags())
                .buildPartial();
        recordConsumer.accept(record);
        rollbackStateAndVersion();
        deactivate();
        entity.releaseTransaction();
    }

    /**
     * Does the entity state and version rollback.
     */
    private void rollbackStateAndVersion() {
        if (!initialState.equals(entity.state())) {
            entity.setState(initialState);
        }
        if (!initialVersion.equals(entity.version())) {
            entity.setVersion(initialVersion);
        }
    }

    /**
     * Creates an {@link EntityRecord} for the entity under transaction.
     *
     * @return new {@link EntityRecord}
     */
    private EntityRecord entityRecord() {
        var entity = entity();
        var entityId = Identifier.pack(entity.id());
        var version = entity.version();
        var state = pack(entity.state());
        var lifecycleFlags = entity.lifecycleFlags();
        return EntityRecord.newBuilder()
                .setEntityId(entityId)
                .setVersion(version)
                .setState(state)
                .setLifecycleFlags(lifecycleFlags)
                .build();
    }

    private @NonValidated S currentBuilderState() {
        return builder.buildPartial();
    }

    private void releaseTx() {
        deactivate();
        entity.releaseTransaction();
    }

    /**
     * Applies lifecycle flag modifications to the entity under transaction.
     */
    protected final void commitAttributeChanges() {
        entity.setLifecycleFlags(lifecycleFlags());
        entity.updateStateChanged();
    }

    final void initAll(S state, Version version) {
        var builder = builder();
        builder.clear();
        builder.mergeFrom(state);
        initVersion(version);
    }

    /**
     * Obtains the builder for the current transaction.
     */
    final B builder() {
        return builder;
    }

    /**
     * Allows to determine, whether this transaction is active or not.
     */
    final boolean stateChanged() {
        return stateChanged;
    }

    @VisibleForTesting
    final void markStateChanged() {
        this.stateChanged = true;
    }

    /**
     * Injects the current transaction instance into an entity.
     */
    private void injectTo(E entity) {
        // Assigning `this` to a variable to explicitly specify
        // the generic bounds for Java compiler.
        var tx = this;
        entity.injectTransaction(tx);
    }

    final void setVersion(Version version) {
        checkNotNull(version);
        this.version = version;
    }

    /**
     * Initializes the entity with the passed version.
     *
     * <p>This method assumes that the entity version is zero.
     * If this is not so, {@code IllegalStateException} will be thrown.
     *
     * <p>One of the usages for this method is for creating an entity instance
     * from a storage.
     *
     * @param version
     *         the version to set.
     */
    private void initVersion(Version version) {
        checkNotNull(version);

        var versionNumber = this.version.getNumber();
        if (versionNumber > 0) {
            var errMsg = format(
                    "initVersion() called on an entity with non-zero version number (%d).",
                    versionNumber
            );
            throw new IllegalStateException(errMsg);
        }
        setVersion(version);
    }

    /**
     * Obtains an instance of the {@code TransactionListener} for this transaction.
     *
     * <p>By default, the returned listener {@linkplain SilentWitness does nothing}.
     */
    private TransactionListener<I> listener() {
        return transactionListener;
    }

    /**
     * Injects a {@linkplain TransactionListener listener} into this transaction.
     *
     * <p>Each next invocation overrides the previous one.
     *
     * @param listener
     *         the listener to use in this transaction
     */
    public final void setListener(TransactionListener<I> listener) {
        checkNotNull(listener);
        this.transactionListener = listener;
    }

    /**
     * Set {@code archived} lifecycle flag to the passed value.
     */
    protected final void setArchived(boolean archived) {
        lifecycleFlags = lifecycleFlags.toBuilder()
                                       .setArchived(archived)
                                       .build();
    }

    /**
     * Set {@code deleted} lifecycle flag to the passed value.
     */
    protected final void setDeleted(boolean deleted) {
        lifecycleFlags = lifecycleFlags.toBuilder()
                                       .setDeleted(deleted)
                                       .build();
    }
}
