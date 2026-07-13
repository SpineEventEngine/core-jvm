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

package io.spine.server.entity.storage

import com.google.protobuf.Message
import com.google.protobuf.Timestamp
import io.spine.base.Identifier
import io.spine.core.Version
import io.spine.query.RecordQuery
import io.spine.server.ContextSpec
import io.spine.server.storage.MessageStorage
import io.spine.server.storage.RecordSpec
import io.spine.server.storage.StorageFactory
import io.spine.server.storage.StorageGroup

/**
 * An abstract base for the storages of a per-entity history.
 *
 * A history storage keeps items of the type [M] — e.g., the events emitted
 * by an entity, or the records of its past states — appended as the entity
 * handles its signals. Each stored item exposes the three
 * [columns][HistoryColumns], allowing to manage the history and query it
 * efficiently: the packed identifier of the entity, the time the item was
 * created, and the number of the entity version the item belongs to.
 *
 * On top of them, the storage provides the [window reads][historyBackward]
 * ordered from newer to older, and the time-based [truncate] maintenance operation.
 *
 * While the reads and the maintenance operations are open to applications,
 * new kinds of histories cannot be defined outside the framework:
 * the constructor is `internal`. Storage vendors customize the persistence
 * via the [RecordStorage][io.spine.server.storage.RecordStorage] delegate
 * their [StorageFactory] creates in
 * [createRecordStorage][io.spine.server.storage.StorageFactory.createRecordStorage].
 *
 * @param I The type of the record identifiers.
 * @param M The type of the stored history items.
 * @param context Specification of the Bounded Context in scope of which the storage is used.
 * @param recordSpec The specification of the records persisting the history items.
 * @property columns The columns to manage and query the history by.
 * @param storageGroup The group the record storage of this history belongs to,
 *   telling it apart from the latest-state storage of the same entity.
 * @param factory The storage factory to use when creating a record storage delegate.
 * @see EntityEventStorage
 * @see EntityStateHistoryStorage
 */
public abstract class HistoryStorage<I : Any, M : Message> internal constructor(
    context: ContextSpec,
    recordSpec: RecordSpec<I, M>,
    private val columns: HistoryColumns<M>,
    storageGroup: StorageGroup,
    factory: StorageFactory
) : MessageStorage<I, M>(context, factory.createRecordStorage(context, recordSpec, storageGroup)) {

    /**
     * Reads up to [batchSize] most recent history items of the entity with
     * the given identifier, ordered from newer to older.
     *
     * The items are sorted by the entity version and then by the time they
     * were created, both in the descending order.
     *
     * @param entityId The identifier of the entity.
     * @param batchSize The maximum number of the items to read.
     * @param startingFrom If set, only the items with versions lower than this one are read.
     * @return An iterator over the read items.
     * @throws IllegalArgumentException If [batchSize] is not positive, or if the type
     *   of [entityId] is not supported by the framework.
     */
    @JvmOverloads
    public fun historyBackward(
        entityId: Any,
        batchSize: Int,
        startingFrom: Version? = null
    ): Iterator<M> {
        require(batchSize > 0) {
            "The batch size must be positive, got $batchSize."
        }
        val packedId = Identifier.pack(entityId)
        val builder = queryBuilder()
            .where(columns.entity_id).isEqualTo(packedId)
        if (startingFrom != null) {
            builder.where(columns.version)
                .isLessThan(startingFrom.number)
        }
        val query = builder
            .sortDescendingBy(columns.version)
            .sortDescendingBy(columns.created)
            .limit(batchSize)
            .build()
        return readAll(query)
    }

    /**
     * Truncates the history, deleting the items created before [olderThan].
     *
     * An item is deleted if and only if it was created strictly before the
     * given time. To purge the whole history, pass the current time — or any
     * moment after the newest item.
     *
     * The items are removed by a query on the [created][HistoryColumns.created]
     * column rather than by reading the history into memory, so the operation
     * scales to large histories.
     *
     * @param olderThan Only the items created strictly before this time are deleted.
     */
    public fun truncate(olderThan: Timestamp) {
        val query = queryBuilder()
            .where(columns.created).isLessThan(olderThan)
            .build()
        deleteMatching(query)
    }

    /**
     * Reads all the history items matching the given query.
     *
     * Overrides to expose the method as a part of the public API of this storage.
     */
    public override fun readAll(query: RecordQuery<I, M>): Iterator<M> = super.readAll(query)

    /**
     * Deletes the history item with the given identifier.
     *
     * The history is maintained by the framework write path; this method
     * exists for the maintenance operations, such as the [truncate] trimming.
     *
     * Overrides to expose the method as a part of the public API of this storage.
     *
     * @return `true` if the item was deleted, `false` if it was not found.
     */
    public override fun delete(id: I): Boolean = super.delete(id)

    /**
     * Deletes the history items with the given identifiers.
     *
     * The history is maintained by the framework write path; this method
     * exists for the maintenance operations, such as the [truncate] trimming.
     *
     * Overrides to expose the method as a part of the public API of this storage.
     */
    public override fun deleteAll(ids: Iterable<I>) {
        super.deleteAll(ids)
    }
}
