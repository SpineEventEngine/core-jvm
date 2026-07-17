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

package io.spine.server.entity;

import com.google.errorprone.annotations.OverridingMethodsMustInvokeSuper;
import com.google.errorprone.annotations.concurrent.LazyInit;
import com.google.protobuf.Timestamp;
import io.spine.base.EntityState;
import io.spine.server.BoundedContext;
import io.spine.server.entity.storage.EntityStateHistoryStorage;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.jspecify.annotations.Nullable;

import java.util.Iterator;

import static io.spine.util.Exceptions.newIllegalStateException;

/**
 * Implementation of {@link RecordBasedRepository} that manages entities
 * derived from {@link AbstractEntity}.
 *
 * @param <I>
 *         the type of the entity identifiers
 * @param <E>
 *         the type of managed entities
 * @param <S>
 *         the type of the entity state
 */
public abstract class AbstractEntityRepository<I,
                                               E extends AbstractEntity<I, S>,
                                               S extends EntityState<I>>
                extends RecordBasedRepository<I, E, S> {

    @LazyInit
    private @MonotonicNonNull StorageConverter<I, E, S> storageConverter;

    /**
     * Whether the opt-in state history recording is enabled for this repository.
     *
     * <p>Volatile: read by dispatch workers, while the recording may be
     * {@linkplain #recordStateHistory() enabled} and
     * {@linkplain #stopRecordingStateHistory() stopped} at runtime.
     */
    private volatile boolean stateHistoryEnabled = false;

    /** The storage of recent state records; created lazily once the history is first needed. */
    private @MonotonicNonNull EntityStateHistoryStorage<I> stateHistory;

    /**
     * Creates a new instance with the {@linkplain #entityFactory() factory} of entities of
     * the class specified as the {@code <E>} generic parameter, and with the default
     * {@linkplain #storageConverter() entity storage converter}.
     */
    protected AbstractEntityRepository() {
        super();
    }

    @Override
    protected EntityFactory<E> entityFactory() {
        return entityModelClass().factory();
    }

    @Override
    protected final StorageConverter<I, E, S> storageConverter() {
        if (storageConverter == null) {
            var entityClass = entityModelClass();
            var stateType = entityClass.stateTypeUrl();
            storageConverter = DefaultConverter.forAllFields(stateType, entityFactory());
        }
        return storageConverter;
    }

    /**
     * Initializes the repository by performing the validation of the entity class and
     * creating the storage converter.
     *
     * @param context
     *         the Bounded Context of this repository
     */
    @Override
    @OverridingMethodsMustInvokeSuper
    public void registerWith(BoundedContext context) {
        super.registerWith(context);
        @SuppressWarnings("unused") // Trigger the method to initialize the converter.
        var unused = storageConverter();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Installs the state history loader on the created entity.
     */
    @Override
    @OverridingMethodsMustInvokeSuper
    public E create(I id) {
        var entity = super.create(id);
        setUpStateHistoryReading(entity, id);
        return entity;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Installs the state history loader on the reconstructed entity.
     */
    @Override
    @OverridingMethodsMustInvokeSuper
    protected E toEntity(EntityRecord record) {
        var entity = super.toEntity(record);
        setUpStateHistoryReading(entity, entity.id());
        return entity;
    }

    /**
     * Installs the state history loader on a newly created or reconstructed entity.
     *
     * <p>The loader is installed unconditionally: whether this repository records the
     * state history gates only the behavior of the installed loader — while the recording
     * is off, reading through it fails fast rather than serving an empty history.
     *
     * @param entity
     *         the entity to set up
     * @param id
     *         the identifier of the entity
     */
    private void setUpStateHistoryReading(E entity, I id) {
        entity.setStateHistoryLoader(stateHistoryLoaderFor(id));
    }

    /**
     * A callback invoked once per {@link #store(Entity) store()} call, after the entity
     * is stored.
     *
     * <p>When the state history is {@linkplain #recordStateHistory() recorded}, appends
     * the current state record on each call — that is, once per successful dispatch. The
     * append happens here and not in {@link #doStore}, because under a batched delivery the
     * cache defers {@code doStore()} to the end of the batch — the history still captures
     * every intermediate version of the batch.
     */
    @Override
    protected final void afterStore(E entity) {
        if (stateHistoryEnabled) {
            appendStateHistory(entity);
        }
    }

    /**
     * Appends the current state record of the entity to the state history.
     *
     * <p>Obtains the storage directly, bypassing the fail-fast {@link #stateHistory()}
     * accessor: the decision to record is made by the single flag check in
     * {@link #afterStore(AbstractEntity)}, so a concurrent
     * {@link #stopRecordingStateHistory()} cannot fail a dispatch which has already
     * persisted its state.
     *
     * <p>A failure to record the history fails the dispatch. Under a batched delivery,
     * the durable state write may happen later, at the batch flush, so a history record
     * may briefly precede the state it captures.
     */
    private void appendStateHistory(E entity) {
        var history = stateHistoryStorage();
        history.write(StorageConverter.toEntityRecord(entity).build());
    }

    /**
     * Creates a loader reading the recorded state history of the entity with
     * the given identifier.
     *
     * <p>The loader delegates to {@link #stateHistory()}: when the recording is not
     * enabled for this repository, reading through the loader fails fast the same way.
     */
    private StateHistoryLoader stateHistoryLoaderFor(I id) {
        return new StateHistoryLoader() {

            @Override
            public Iterator<EntityRecord> load(int depth) {
                return stateHistory().historyBackward(id, depth);
            }

            @Override
            public @Nullable EntityRecord stateAt(Timestamp at) {
                return stateHistory().stateAt(id, at);
            }
        };
    }

    /**
     * Enables recording the state history for the entities of this repository.
     *
     * <p>When enabled, each successful dispatch appends the resulting
     * {@link EntityRecord} to an {@link EntityStateHistoryStorage} — e.g., for answering
     * {@linkplain EntityStateHistoryStorage#stateAt(Object, com.google.protobuf.Timestamp)
     * the "state at a time" query}. The history is <b>off by default</b>: recording adds
     * a write operation to every dispatch.
     *
     * <p>The entities of this repository read the recorded history via
     * {@link AbstractEntity#stateAt(Timestamp)} and
     * {@link AbstractEntity#stateHistoryBackward(int)}.
     *
     * <p>Records are appended per dispatch even when the delivery batches the state
     * write-through, so the intermediate versions of a batch are retained.
     *
     * <p><b>The history is not trimmed automatically.</b> The framework does not spend
     * a maintenance query on the dispatch path, so the storage grows with every dispatch
     * until the application removes the records it no longer needs. Schedule the
     * maintenance suiting your domain — e.g., periodically invoke
     * {@link EntityStateHistoryStorage#truncate(Timestamp) truncate(olderThan)} on the
     * {@linkplain #stateHistory() state history}, or bound the history of a single
     * entity with {@link EntityStateHistoryStorage#trim(Object, int)
     * trim(entityId, keepMostRecent)}.
     *
     * <p>A failure to record the history fails the dispatch. Note that under a batched
     * delivery the durable write of the entity state itself may follow at the batch
     * flush, so a history record may briefly precede the state it captures.
     *
     * @see #stateHistory()
     * @see #stopRecordingStateHistory()
     */
    protected void recordStateHistory() {
        this.stateHistoryEnabled = true;
    }

    /**
     * Tells whether the opt-in state history recording is enabled for this repository.
     *
     * @return {@code false} by default
     * @see #recordStateHistory()
     */
    protected boolean stateHistoryEnabled() {
        return stateHistoryEnabled;
    }

    /**
     * Stops recording the state history for the entities of this repository.
     *
     * <p>The records already stored remain in the storage. While the recording is off,
     * reading the {@linkplain #stateHistory() state history} fails fast the usual way;
     * {@linkplain #recordStateHistory() re-enabling} the recording resumes over the
     * retained records, with a gap for the dispatches served while it was off.
     *
     * <p>The switch may be flipped at runtime: dispatch workers observe it on their
     * next dispatch. A dispatch already past its recording check may append one more
     * record after this call returns.
     *
     * <p>To also purge the retained records, truncate the history up to the present
     * <em>before</em> stopping: {@code stateHistory().truncate(currentTime())}.
     *
     * @see #recordStateHistory()
     */
    protected void stopRecordingStateHistory() {
        this.stateHistoryEnabled = false;
    }

    /**
     * Returns the storage of the recent state history of the entities of this repository.
     *
     * <p>The state history is an opt-in feature. Reading it while disabled is
     * a configuration error, so this method fails fast rather than acting as if
     * an empty history existed.
     *
     * @return the state history storage
     * @throws IllegalStateException
     *         if the state history is not {@linkplain #recordStateHistory() recorded}
     *         by this repository
     */
    protected final EntityStateHistoryStorage<I> stateHistory() {
        if (!stateHistoryEnabled) {
            throw newIllegalStateException(
                    "The state history is not recorded for the repository `%s`. " +
                            "Enable it by calling `recordStateHistory()`, " +
                            "e.g. from the repository constructor.", this);
        }
        return stateHistoryStorage();
    }

    /**
     * Creates the storage of the recent state history of the entities of this repository.
     *
     * <p>Mirrors {@link #createStorage()}: the default implementation uses the
     * {@linkplain #defaultStorageFactory() default storage factory} — the same one the default
     * {@code createStorage()} uses for the entity state. A repository that overrides
     * {@code createStorage()} to serve the entity state from a custom
     * {@link io.spine.server.storage.StorageFactory} or backend should override this method as
     * well, so the recorded state history is served by the same backend as the state, rather
     * than silently falling back to the default one.
     *
     * @return a new state history storage
     */
    protected EntityStateHistoryStorage<I> createStateHistoryStorage() {
        var factory = defaultStorageFactory();
        return factory.createEntityStateHistoryStorage(context().spec(), entityClass());
    }

    /**
     * Returns the storage of the recent state history of the entities of this repository,
     * creating it lazily via {@link #createStateHistoryStorage()} on the first access.
     *
     * <p>Unlike the fail-fast {@link #stateHistory()} accessor, this method does not require
     * recording to be {@linkplain #recordStateHistory() enabled}: it exposes the storage for
     * the maintenance operations — {@link EntityStateHistoryStorage#truncate(Timestamp) truncate}
     * and {@link EntityStateHistoryStorage#trim(Object, int) trim} — which a repository may run
     * even while the recording is off.
     *
     * <p>Synchronized: unlike the main {@linkplain #storage() storage}, which is first
     * accessed during the single-threaded registration of the repository, this storage
     * is first touched when a signal is dispatched, possibly by concurrent workers.
     *
     * @return the state history storage
     */
    protected final synchronized EntityStateHistoryStorage<I> stateHistoryStorage() {
        if (stateHistory == null) {
            stateHistory = createStateHistoryStorage();
        }
        return stateHistory;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Also closes the state history storage if it was created.
     */
    @Override
    @OverridingMethodsMustInvokeSuper
    public void close() {
        RuntimeException failure = null;
        try {
            super.close();
        } catch (RuntimeException e) {
            failure = e;
        }
        failure = attemptClose(failure, this::closeStateHistory);
        if (failure != null) {
            throw failure;
        }
    }

    /**
     * Closes the state history storage if it was created.
     *
     * <p>Synchronized to pair with {@link #stateHistoryStorage()}: the storage may have been
     * created by a dispatch worker, and the closing thread must observe that write.
     */
    private synchronized void closeStateHistory() {
        if (stateHistory != null && stateHistory.isOpen()) {
            stateHistory.close();
        }
    }
}
