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

import com.google.common.collect.ImmutableList
import com.google.errorprone.annotations.CanIgnoreReturnValue
import io.spine.annotation.Internal
import io.spine.annotation.VisibleForTesting
import io.spine.base.EntityState
import io.spine.base.Error
import io.spine.base.Errors.causeOf
import io.spine.base.Identifier
import io.spine.core.Event
import io.spine.core.MessageId
import io.spine.core.Version
import io.spine.core.Versions.checkIsIncrement
import io.spine.protobuf.AnyPacker.pack
import io.spine.server.dispatch.DispatchOutcome
import io.spine.server.dispatch.DispatchOutcomeHandler
import io.spine.validation.NonValidated
import io.spine.validation.ValidatingBuilder

/**
 * The abstract class for the [entity][TransactionalEntity] transactions.
 *
 * The transaction is a set of changes made to an entity state or entity attributes
 * (e.g. version, lifecycle flags etc).
 *
 * Serves as a buffer, accumulating the changes, intended for the enclosed `Entity`;
 * the changes are only applied to the actual object upon [commit].
 *
 * The transaction is injected into the entity, whose state should be modified. By doing so,
 * the ["buffering" builder][builder] is exposed to concrete `TransactionalEntity` subclasses.
 * In turn, they receive an ability to change the entity state by modifying
 * [entity state builder][TransactionalEntity.builder].
 *
 * The same applies to the entity lifecycle flags.
 *
 * Version management is performed automatically by the transaction itself.
 *
 * @param I The type of entity IDs.
 * @param E The type of entity.
 * @param S The type of entity state.
 * @param B The type of a `ValidatingBuilder` for the entity state.
 * @see TransactionListener
 */
@Internal
@Suppress("TooManyFunctions") // The class coordinates the whole transaction lifecycle.
public abstract class Transaction<I : Any,
                                  E : TransactionalEntity<I, S, B>,
                                  S : EntityState<I>,
                                  B : ValidatingBuilder<S>> {

    /**
     * The entity, whose state and attributes are modified in this transaction.
     */
    @get:JvmName("entity")
    internal val entity: E

    /**
     * The state of the entity before the beginning of the transaction.
     */
    private val initialState: S

    /**
     * The version of the entity before the beginning of the transaction.
     */
    private val initialVersion: Version

    /**
     * The builder for the entity state at the current phase of the transaction.
     *
     * All the state changes made within the transaction go to this `Builder`,
     * and not to the `Entity` itself.
     *
     * @see propagate
     * @see commit
     */
    @get:JvmName("builder")
    internal var builder: B
        private set

    /**
     * The [EntityRecord] containing the entity data and meta-info before the transaction start.
     */
    private val entityBeforeTransaction: EntityRecord

    /**
     * The version of the entity, modified within this transaction.
     *
     * All the version changes made within the transaction are stored in this variable,
     * and not in the `Entity` itself.
     *
     * This value is propagated to the entity upon the [commit].
     */
    @get:JvmName("version")
    @set:JvmName("setVersion")
    internal var version: Version

    /**
     * The lifecycle flags of the entity, modified within this transaction.
     *
     * All the entity lifecycle changes made within the transaction are stored in this variable,
     * and not in the `Entity` itself.
     *
     * This value is set to the entity upon the [commit].
     */
    @get:JvmName("lifecycleFlags")
    internal var lifecycleFlags: LifecycleFlags
        private set

    /**
     * The lifecycle flags of the entity as of the start of this transaction.
     *
     * Serves as the base of the [lifecycleFlagsChanged] comparison: while the
     * transaction is active, the entity itself reads its flags through the
     * transaction, so the committed value must be captured up front.
     */
    private val initialLifecycleFlags: LifecycleFlags

    /**
     * The flag, which becomes `true` if the state of the entity [has been changed][commit] since
     * it has been [loaded or created][Repository.findOrCreate].
     */
    internal var stateChanged: Boolean = false
        private set

    /**
     * Tells whether this transaction is active.
     *
     * Has `true` value since the transaction instance creation until [commit] is performed.
     */
    internal var isActive: Boolean = false
        private set

    /**
     * An ordered collection of the phases that were propagated in scope of this transaction.
     *
     * Contains all the phases, including failed.
     */
    private val phaseList: MutableList<Phase<I>> = mutableListOf()

    private var transactionListener: TransactionListener<I> = SilentWitness()

    /**
     * Creates a new instance of `Transaction` and
     * [injects][TransactionalEntity.injectTransaction] the newly created transaction into
     * the given `entity`.
     *
     * The entity state and attributes are set as starting values for this transaction.
     *
     * @param entity The entity to create the transaction for.
     * @see TransactionListener
     */
    protected constructor(entity: E) {
        this.entity = entity
        initialState = entity.state()
        initialVersion = entity.version()
        builder = toBuilder(entity)
        version = entity.version()
        lifecycleFlags = entity.lifecycleFlags()
        initialLifecycleFlags = lifecycleFlags
        isActive = true
        injectTo()
        entityBeforeTransaction = entityRecord()
    }

    /**
     * Acts similar to [an overloaded ctor][Transaction], but instead of using the original entity
     * state and version, this transaction will have the passed state and version as a starting
     * point.
     *
     * Note, that the given `state` and `version` are applied to the actual entity upon commit.
     *
     * @param entity The target entity to modify within this transaction.
     * @param state The entity state to set.
     * @param version The entity version to set.
     */
    protected constructor(entity: E, state: S, version: Version) : this(entity) {
        initAll(state, version)
    }

    /**
     * Obtains the [MessageId] of the entity under the transaction.
     */
    @JvmName("entityId")
    internal fun entityId(): MessageId {
        val typeUrl = entity.state().typeUrl()
        return MessageId.newBuilder()
            .setId(Identifier.pack(entity.id()))
            .setTypeUrl(typeUrl.value())
            .setVersion(entity.version())
            .build()
    }

    /**
     * Returns the collection of the phases that were propagated in scope of this transaction.
     */
    internal fun phases(): List<Phase<I>> = ImmutableList.copyOf(phaseList)

    /**
     * Propagates a phase and performs a rollback in case of an error.
     *
     * The transaction [listener][listener] is called for both failed and successful phases.
     *
     * If the signal propagation cases an error, a rejection, or an unhandled exception,
     * the transaction is rolled back.
     *
     * @param phase The phase to propagate.
     * @return The phase propagation result.
     */
    @CanIgnoreReturnValue
    protected fun propagate(phase: Phase<I>): DispatchOutcome {
        val listener = listener()
        listener.onBeforePhase(phase)
        val outcome = propagateFailsafe(phase)
        phaseList.add(phase)
        listener.onAfterPhase(phase)
        return outcome
    }

    /**
     * Propagates the given phase and catches failures if any.
     *
     * The catch block in this method and in [commit] prevents force majeure situations such
     * as storage failures, etc. All the exceptions produced in the framework users' code are
     * handled before this failsafe and are already packed in the `DispatchOutcome` produced by
     * the phase.
     *
     * @see propagate
     */
    @Suppress("TooGenericExceptionCaught") // Failsafe against force majeure failures.
    private fun propagateFailsafe(phase: Phase<I>): DispatchOutcome {
        return try {
            val outcome = phase.propagate()
            DispatchOutcomeHandler.from(outcome)
                .onError { rollback(it) }
                .onRejection { rollback(it) }
                .handle()
        } catch (t: Throwable) {
            val rootCause = causeOf(t)
            rollback(rootCause)
            DispatchOutcome.newBuilder()
                .setPropagatedSignal(phase.signal().messageId())
                .setError(rootCause)
                .build()
        }
    }

    /**
     * Advances the state and the version of the entity being built by the transaction after
     * the message was successfully dispatched.
     *
     * This is needed for the cases of dispatching more than one message during a transaction.
     * After the state is propagated to the entity, its message handler that is invoked during
     * the next step would “see” the [state of the entity][Entity.state].
     *
     * @param increment The strategy for incrementing the version.
     */
    @JvmName("incrementStateAndVersion")
    internal fun incrementStateAndVersion(increment: VersionIncrement) {
        val nextVersion = increment.nextVersion()
        checkIsIncrement(version, nextVersion)
        version = nextVersion
        val newState = builder.build()
        @Suppress("UNCHECKED_CAST") // Ensured by the argument of `<E>`.
        builder = newState.toBuilder() as B
        entity.updateState(newState, nextVersion)
    }

    /**
     * Tells whether the receptor dispatched in the current phase changed the entity state.
     *
     * Compares the live [builder] against a freshly recomputed baseline for the entity's
     * current state (which, for a fresh entity, carries the framework-injected ID). When the two
     * are equal, the receptor left the state untouched — for example an `@React` reaction that
     * withheld its reaction and returned no events.
     *
     * The phase uses this to avoid validating and versioning a pure no-op dispatch, which would
     * otherwise force a [build][ValidatingBuilder.build] of an as-yet-unmodified state — failing
     * whenever that default state is itself invalid (e.g. a not-yet-initialized aggregate whose
     * state declares required constraints).
     */
    @JvmName("stateChangedInPhase")
    internal fun stateChangedInPhase(): Boolean =
        builder.buildPartial() != toBuilder(entity).buildPartial()

    /**
     * Tells whether the lifecycle flags of the entity have changed in scope
     * of this transaction.
     *
     * The phase consults this alongside [stateChangedInPhase]: a receptor
     * that emits no events and leaves the state as-is may still archive or
     * delete the entity. Such a change alters the persisted record and must
     * advance the version, so that the consumers keyed by the version —
     * e.g. the entity state history — never observe two distinct records
     * under one version.
     */
    @JvmName("lifecycleFlagsChanged")
    internal fun lifecycleFlagsChanged(): Boolean =
        lifecycleFlags != initialLifecycleFlags

    /**
     * Commits this transaction if it is still active.
     *
     * If the transaction is not active, does nothing.
     *
     * @see commit
     */
    public fun commitIfActive() {
        if (isActive) {
            commit()
        }
    }

    /**
     * Applies all the outstanding modifications to the enclosed entity.
     *
     * @throws InvalidEntityStateException In case the new entity state is not valid.
     * @throws IllegalStateException In case of a generic error.
     */
    @VisibleForTesting
    public fun commit() {
        executeOnBeforeCommit()
        val newState = builder.buildPartial()
        doCommit(newState)
    }

    /**
     * Commits this transaction and sets the new state to the entity.
     *
     * In case there are no entity state changes, still checks the entity column values and
     * meta-attributes for updates, as these values may change independently of the entity state.
     *
     * In case something goes wrong during the commit, the transaction is rolled back and the
     * entity keeps its current state.
     */
    @Suppress("TooGenericExceptionCaught") // Rolls back on any commit-time failure.
    private fun doCommit(newState: @NonValidated S) {
        try {
            val pendingVersion = version
            beforeCommit(newState, pendingVersion)
            updateState(newState)
            updateVersion()
            updateStateChanged()
            commitAttributeChanges()
            val newRecord = entityRecord()
            afterCommit(newRecord)
        } catch (e: RuntimeException) {
            rollback(causeOf(e))
        } finally {
            releaseTx()
        }
    }

    /**
     * Propagates the state update to the entity.
     */
    private fun updateState(newState: @NonValidated S) {
        if (initialState != newState) {
            entity.updateState(newState)
        }
    }

    /**
     * Propagates the version update to the entity.
     */
    private fun updateVersion() {
        val pending = version
        if (pending != entity.version()) {
            entity.updateVersion(pending)
        }
    }

    /**
     * Marks entity state as changed if there are any changes.
     *
     * This triggers the storage mechanism.
     */
    private fun updateStateChanged() {
        if (entity.state() != initialState) {
            markStateChanged()
        }
    }

    /**
     * Turns the transaction into inactive state.
     */
    @VisibleForTesting
    protected fun deactivate() {
        isActive = false
    }

    private fun executeOnBeforeCommit() {
        entity.triggerOnBeforeCommit()
    }

    private fun beforeCommit(newState: S, newVersion: Version) {
        val newFlags = lifecycleFlags
        val record: @NonValidated EntityRecord = EntityRecord.newBuilder()
            .setEntityId(Identifier.pack(entity.id()))
            .setState(pack(newState))
            .setLifecycleFlags(newFlags)
            .setVersion(newVersion)
            .buildPartial()
        transactionListener.onBeforeCommit(record)
    }

    private fun afterCommit(newEntity: EntityRecord) {
        val change = EntityRecordChange.newBuilder()
            .setPreviousValue(entityBeforeTransaction)
            .setNewValue(newEntity)
            .build()
        transactionListener.onAfterCommit(change)
    }

    /**
     * Cancels the changes made within this transaction and removes the injected transaction object
     * from the enclosed entity.
     *
     * @param cause The reason for the rollback.
     */
    @VisibleForTesting
    internal fun rollback(cause: Error) {
        doRollback { record -> listener().onTransactionFailed(cause, record) }
    }

    /**
     * Cancels the changes made within this transaction and removes the injected transaction object
     * from the enclosed entity.
     *
     * @param cause The reason for the rollback.
     */
    private fun rollback(cause: Event) {
        doRollback { record -> listener().onTransactionFailed(cause, record) }
    }

    private fun doRollback(recordConsumer: (EntityRecord) -> Unit) {
        val record: @NonValidated EntityRecord = EntityRecord.newBuilder()
            .setEntityId(Identifier.pack(entity.id()))
            .setState(pack(currentBuilderState()))
            .setVersion(version)
            .setLifecycleFlags(lifecycleFlags)
            .buildPartial()
        recordConsumer(record)
        rollbackStateAndVersion()
        deactivate()
        entity.releaseTransaction()
    }

    /**
     * Does the entity state and version rollback.
     */
    private fun rollbackStateAndVersion() {
        if (initialState != entity.state()) {
            entity.setState(initialState)
        }
        if (initialVersion != entity.version()) {
            entity.setVersion(initialVersion)
        }
    }

    /**
     * Creates an [EntityRecord] for the entity under transaction.
     *
     * @return a new [EntityRecord].
     */
    private fun entityRecord(): EntityRecord {
        val e = entity
        val entityId = Identifier.pack(e.id())
        val entityVersion = e.version()
        val state = pack(e.state())
        val flags = e.lifecycleFlags()
        return EntityRecord.newBuilder()
            .setEntityId(entityId)
            .setVersion(entityVersion)
            .setState(state)
            .setLifecycleFlags(flags)
            .build()
    }

    private fun currentBuilderState(): @NonValidated S = builder.buildPartial()

    private fun releaseTx() {
        deactivate()
        entity.releaseTransaction()
    }

    /**
     * Applies lifecycle flag modifications to the entity under transaction.
     */
    protected fun commitAttributeChanges() {
        entity.setLifecycleFlags(lifecycleFlags)
        entity.updateStateChanged()
    }

    @JvmName("initAll")
    internal fun initAll(state: S, version: Version) {
        builder.clear()
        builder.mergeFrom(state)
        initVersion(version)
    }

    @VisibleForTesting
    protected fun markStateChanged() {
        stateChanged = true
    }

    /**
     * Injects the current transaction instance into an entity.
     */
    private fun injectTo() {
        entity.injectTransaction(this)
    }

    /**
     * Initializes the entity with the passed version.
     *
     * This method assumes that the entity version is zero.
     * If this is not so, `IllegalStateException` will be thrown.
     *
     * One of the usages for this method is for creating an entity instance from storage.
     *
     * @param version The version to set.
     */
    private fun initVersion(version: Version) {
        val versionNumber = this.version.number
        check(versionNumber <= 0) {
            "initVersion() called on an entity with non-zero version number ($versionNumber)."
        }
        this.version = version
    }

    /**
     * Obtains an instance of the `TransactionListener` for this transaction.
     *
     * By default, the returned listener [does nothing][SilentWitness].
     */
    private fun listener(): TransactionListener<I> = transactionListener

    /**
     * Injects a [listener][TransactionListener] into this transaction.
     *
     * Each next invocation overrides the previous one.
     *
     * @param listener The listener to use in this transaction.
     */
    public fun setListener(listener: TransactionListener<I>) {
        transactionListener = listener
    }

    /**
     * Sets the `archived` lifecycle flag to the passed value.
     */
    @JvmName("setArchived")
    internal fun setArchived(archived: Boolean) {
        lifecycleFlags = lifecycleFlags.toBuilder()
            .setArchived(archived)
            .build()
    }

    /**
     * Sets the `deleted` lifecycle flag to the passed value.
     */
    @JvmName("setDeleted")
    internal fun setDeleted(deleted: Boolean) {
        lifecycleFlags = lifecycleFlags.toBuilder()
            .setDeleted(deleted)
            .build()
    }

    public companion object {

        /**
         * Creates the builder to be used by a transaction when modifying the passed entity.
         *
         * If the entity has the default state, and the first field of the state is its ID, and
         * the field is required, initializes the builder with the value of the entity ID.
         */
        @VisibleForTesting
        internal fun <I : Any,
                      E : TransactionalEntity<I, S, B>,
                      S : EntityState<I>,
                      B : ValidatingBuilder<S>>
                toBuilder(entity: E): B {
            val currentState = entity.state()
            @Suppress("UNCHECKED_CAST") // Ensured by the argument of `<E>`.
            val result = currentState.toBuilder() as B
            if (currentState == entity.defaultState()) {
                val idField = IdField.of(entity.modelClass())
                idField.initBuilder(result, entity.id())
            }
            return result
        }
    }
}
