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

import com.google.errorprone.annotations.OverridingMethodsMustInvokeSuper
import com.google.protobuf.Timestamp
import io.spine.base.EntityState
import io.spine.core.Version
import io.spine.server.BoundedContext
import io.spine.server.entity.storage.EntityStateHistoryStorage
import io.spine.util.Exceptions.newIllegalStateException

/**
 * Implementation of [RecordBasedRepository] that manages entities
 * derived from [AbstractEntity].
 *
 * @param I The type of the entity identifiers.
 * @param E The type of managed entities.
 * @param S The type of the entity state.
 */
@Suppress("TooManyFunctions") // This base for entity repositories has many functions.
public abstract class AbstractEntityRepository<I : Any,
                                               E : AbstractEntity<I, S>,
                                               S : EntityState<I>> :
    RecordBasedRepository<I, E, S> {

    private var storageConverter: StorageConverter<I, E, S>? = null

    /**
     * Whether the opt-in state history recording is enabled for this repository.
     *
     * Volatile: read by dispatch workers, while the recording may be
     * [enabled][recordStateHistory] and [stopped][stopRecordingStateHistory] at runtime.
     */
    @Volatile
    private var recordingEnabled = false

    /** The storage of recent state records; created lazily once the history is first needed. */
    private var stateHistory: EntityStateHistoryStorage<I>? = null

    /**
     * Creates a new instance with the [factory][entityFactory] of entities of
     * the class specified as the `<E>` generic parameter, and with the default
     * [entity storage converter][storageConverter].
     */
    protected constructor() : super()

    override fun entityFactory(): EntityFactory<E> = entityModelClass().factory()

    final override fun storageConverter(): StorageConverter<I, E, S> =
        storageConverter ?: run {
            val entityClass = entityModelClass()
            val stateType = entityClass.stateTypeUrl()
            DefaultConverter.forAllFields(stateType, entityFactory())
        }.also { storageConverter = it }

    /**
     * Initializes the repository by performing the validation of the entity class and
     * creating the storage converter.
     *
     * @param context The Bounded Context of this repository.
     */
    @OverridingMethodsMustInvokeSuper
    override fun registerWith(context: BoundedContext) {
        super.registerWith(context)
        // Trigger the method to initialize the converter.
        storageConverter()
    }

    /**
     * Creates an entity with the given ID, installing the state history loader on it.
     */
    @OverridingMethodsMustInvokeSuper
    override fun create(id: I): E {
        val entity = super.create(id)
        setUpStateHistoryReading(entity, id)
        return entity
    }

    /**
     * Reconstructs an entity from the given record, installing the state history
     * loader on it.
     */
    @OverridingMethodsMustInvokeSuper
    override fun toEntity(record: EntityRecord): E {
        val entity = super.toEntity(record)
        setUpStateHistoryReading(entity, entity.id)
        return entity
    }

    /**
     * Installs the state history loader on a newly created or reconstructed entity.
     *
     * The loader is installed unconditionally: whether this repository records the
     * state history gates only the behavior of the installed loader — while the recording
     * is off, reading through it fails fast rather than serving an empty history.
     *
     * @param entity The entity to set up.
     * @param id The identifier of the entity.
     */
    private fun setUpStateHistoryReading(entity: E, id: I) {
        entity.setStateHistoryLoader(stateHistoryLoaderFor(id))
    }

    /**
     * A callback invoked once per [store] call, after the entity is stored.
     *
     * When the state history is [recorded][recordStateHistory], appends
     * the current state record on each call — that is, once per successful dispatch. The
     * append happens here and not in [doStore], because under a batched delivery the
     * cache defers `doStore()` to the end of the batch — the history still captures
     * every intermediate version of the batch.
     */
    final override fun afterStore(entity: E) {
        if (recordingEnabled) {
            appendStateHistory(entity)
        }
    }

    /**
     * Appends the current state record of the entity to the state history.
     *
     * Obtains the storage directly, bypassing the fail-fast [stateHistory] accessor:
     * the decision to record is made by the single flag check in
     * [afterStore], so a concurrent
     * [stopRecordingStateHistory] cannot fail a dispatch that has already
     * persisted its state.
     *
     * A failure to record the history fails the dispatch. Under a batched delivery,
     * the durable state write may happen later, at the batch flush, so a history record
     * may briefly precede the state it captures.
     *
     * Once the durable write succeeds, the record also enters the recent state
     * history of the entity instance, so the reads served by this instance —
     * e.g., during the later dispatches of a delivery batch — come from memory.
     */
    private fun appendStateHistory(entity: E) {
        val history = stateHistoryStorage()
        val record = StorageConverter.toEntityRecord(entity).build()
        history.write(record)
        entity.appendToStateHistory(record)
    }

    /**
     * Creates a loader reading the recorded state history of the entity with
     * the given identifier.
     *
     * The loader delegates to [stateHistory]: when the recording is not
     * enabled for this repository, reading through the loader fails fast the same way.
     */
    private fun stateHistoryLoaderFor(id: I): StateHistoryLoader =
        object : StateHistoryLoader {

            override fun load(depth: Int, startingFrom: Version?): Iterator<EntityRecord> =
                stateHistory().historyBackward(entityId = id, batchSize = depth, startingFrom)

            override fun stateAt(at: Timestamp): EntityRecord? =
                stateHistory().stateAt(id, at)
        }

    /**
     * Enables recording the state history for the entities of this repository.
     *
     * When enabled, each successful dispatch appends the resulting
     * [EntityRecord] to an [EntityStateHistoryStorage] — e.g., for answering
     * [the "state at a time" query][EntityStateHistoryStorage.stateAt]. The history
     * is **off by default**: recording adds a write operation to every dispatch.
     *
     * The entities of this repository read the recorded history via
     * [AbstractEntity.stateAt] and [AbstractEntity.stateHistoryBackward].
     *
     * Records are appended per dispatch even when the delivery batches the state
     * write-through, so the intermediate versions of a batch are retained.
     *
     * **The history is not trimmed automatically.** The framework does not spend
     * a maintenance query on the dispatch path, so the storage grows with every dispatch
     * until the application removes the records it no longer needs. Schedule the
     * maintenance suiting your domain — e.g., periodically invoke
     * [truncate(olderThan)][EntityStateHistoryStorage.truncate] on the
     * [state history][stateHistory], or bound the history of a single
     * entity with [trim(entityId, keepMostRecent)][EntityStateHistoryStorage.trim].
     *
     * A failure to record the history fails the dispatch. Note that under a batched
     * delivery the durable write of the entity state itself may follow at the batch
     * flush, so a history record may briefly precede the state it captures.
     *
     * @see stateHistory
     * @see stopRecordingStateHistory
     */
    protected open fun recordStateHistory() {
        recordingEnabled = true
    }

    /**
     * Tells whether the opt-in state history recording is enabled for this repository.
     *
     * @return `false` by default.
     * @see recordStateHistory
     */
    protected open fun stateHistoryEnabled(): Boolean = recordingEnabled

    /**
     * Stops recording the state history for the entities of this repository.
     *
     * The records already stored remain in the storage. While the recording is off,
     * reading the [state history][stateHistory] fails fast the usual way;
     * [re-enabling][recordStateHistory] the recording resumes over the
     * retained records, with a gap for the dispatches served while it was off.
     *
     * The switch may be flipped at runtime: dispatch workers observe it on their
     * next dispatch. A dispatch already past its recording check may append one more
     * record after this call returns. An entity instance that cached the records
     * appended while the recording was on — e.g., in the middle of a delivery
     * batch — keeps serving them from memory for the rest of its lifetime.
     *
     * To also purge the retained records, truncate the history up to the present
     * *before* stopping: `stateHistory().truncate(currentTime())`.
     *
     * @see recordStateHistory
     */
    protected open fun stopRecordingStateHistory() {
        recordingEnabled = false
    }

    /**
     * Returns the storage of the recent state history of the entities of this repository.
     *
     * The state history is an opt-in feature. Reading it while disabled is
     * a configuration error, so this method fails fast rather than acting as if
     * an empty history existed.
     *
     * @return The state history storage.
     * @throws IllegalStateException If the state history is not
     *   [recorded][recordStateHistory] by this repository.
     */
    protected fun stateHistory(): EntityStateHistoryStorage<I> {
        if (!recordingEnabled) {
            throw newIllegalStateException(
                "The state history is not recorded for the repository `%s`. " +
                        "Enable it by calling `recordStateHistory()`, " +
                        "e.g. from the repository constructor.",
                this
            )
        }
        return stateHistoryStorage()
    }

    /**
     * Creates the storage of the recent state history of the entities of this repository.
     *
     * Mirrors [createStorage]: the default implementation uses the
     * [default storage factory][Repository.defaultStorageFactory] — the same one the default
     * `createStorage()` uses for the entity state. A repository that overrides
     * `createStorage()` to serve the entity state from a custom
     * [io.spine.server.storage.StorageFactory] or backend should override this method as
     * well, so the recorded state history is served by the same backend as the state, rather
     * than silently falling back to the default one.
     *
     * @return A new state history storage.
     */
    protected open fun createStateHistoryStorage(): EntityStateHistoryStorage<I> {
        val factory = defaultStorageFactory()
        return factory.createEntityStateHistoryStorage(context().spec(), entityClass())
    }

    /**
     * Returns the storage of the recent state history of the entities of this repository,
     * creating it lazily via [createStateHistoryStorage] on the first access.
     *
     * Unlike the fail-fast [stateHistory] accessor, this method does not require
     * recording to be [enabled][recordStateHistory]: it exposes the storage for
     * the maintenance operations — [truncate][EntityStateHistoryStorage.truncate]
     * and [trim][EntityStateHistoryStorage.trim] — which a repository may run
     * even while the recording is off.
     *
     * Synchronized: unlike the main [storage], which is first
     * accessed during the single-threaded registration of the repository, this storage
     * is first touched when a signal is dispatched, possibly by concurrent workers.
     *
     * @return The state history storage.
     */
    @Synchronized
    protected fun stateHistoryStorage(): EntityStateHistoryStorage<I> =
        stateHistory ?: createStateHistoryStorage().also { stateHistory = it }

    /**
     * Closes this repository, also closing the state history storage if it was created.
     */
    @OverridingMethodsMustInvokeSuper
    @Suppress("TooGenericExceptionCaught") // Accumulates any failure of a close step.
    override fun close() {
        var failure: RuntimeException? = null
        try {
            super.close()
        } catch (e: RuntimeException) {
            failure = e
        }
        failure = attemptClose(failure) { closeStateHistory() }
        if (failure != null) {
            throw failure
        }
    }

    /**
     * Closes the state history storage if it was created.
     *
     * Synchronized to pair with [stateHistoryStorage]: the storage may have been
     * created by a dispatch worker, and the closing thread must observe that write.
     */
    @Synchronized
    private fun closeStateHistory() {
        stateHistory?.let {
            if (it.isOpen) {
                it.close()
            }
        }
    }
}
