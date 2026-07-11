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
import com.google.protobuf.util.Timestamps
import io.spine.base.Identifier
import io.spine.core.Version
import io.spine.query.RecordQuery
import io.spine.server.ContextSpec
import io.spine.server.storage.MessageStorage
import io.spine.server.storage.StorageFactory

/**
 * An abstract base for the storages of a per-entity history.
 *
 * A history storage keeps items of the type [M] — e.g., the events emitted
 * by an entity, or the records of its past states — appended as the entity
 * handles its signals. Each stored item exposes the three columns of
 * the [HistorySpec], allowing to manage the history and query it
 * efficiently: the packed identifier of the entity, the time the item was
 * created, and the number of the entity version the item belongs to.
 *
 * On top of them, the storage provides the [window reads][historyBackward]
 * ordered from newer to older, the per-entity [trim] for the write path,
 * and the count/date-based [truncate] maintenance.
 *
 * The class is internal to the framework: storage vendors customize the
 * persistence via the [RecordStorage][io.spine.server.storage.RecordStorage]
 * delegate created by their [StorageFactory].
 *
 * @param I The type of the record identifiers.
 * @param M The type of the stored history items.
 * @param context Specification of the Bounded Context in scope of which the storage is used.
 * @param factory The storage factory to use when creating a record storage delegate.
 * @param spec The specification of the history.
 * @see EntityEventStorage
 * @see EntityStateHistoryStorage
 */
public abstract class HistoryStorage<I : Any, M : Message> internal constructor(
    context: ContextSpec,
    factory: StorageFactory,
    spec: HistorySpec<I, M>
) : MessageStorage<I, M>(context, factory.createRecordStorage(context, spec.recordSpec)) {

    /**
     * The columns to manage and query the history by.
     */
    private val columns: HistoryColumns<M> = spec.columns

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
        requirePositiveBatchSize(batchSize)
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
     * Trims the history of the entity with the given identifier, keeping up
     * to [keepMostRecent] most recent items.
     *
     * Unlike [truncate], the operation reads only the items of the given
     * entity, so it suits the per-dispatch use on the write path.
     * Passing zero purges the history of the entity.
     *
     * A storage whose record identifiers carry the ranking of the items may
     * override this implementation with an identifier-only read.
     *
     * @param entityId The identifier of the entity.
     * @param keepMostRecent The number of the most recent items to keep.
     * @throws IllegalArgumentException If [keepMostRecent] is negative, or if the type
     *   of [entityId] is not supported by the framework.
     */
    public open fun trim(entityId: Any, keepMostRecent: Int) {
        requireNotNegative(keepMostRecent)
        val packedId = Identifier.pack(entityId)
        val newestFirst = queryBuilder()
            .where(columns.entity_id).isEqualTo(packedId)
            .sortDescendingBy(columns.version)
            .sortDescendingBy(columns.created)
            .build()
        val items = readAll(newestFirst)
        val toDelete = items.asSequence()
            .drop(keepMostRecent)
            .map { idOf(it) }
            .toList()
        if (toDelete.isNotEmpty()) {
            deleteAll(toDelete)
        }
    }

    /**
     * Truncates the history, keeping up to [keepMostRecent] most recent
     * items for each entity.
     *
     * The most recent items are determined per entity, in the order of
     * [historyBackward]. Passing zero purges the whole history.
     *
     * The operation reads the whole history, so it is intended for periodic
     * maintenance rather than for per-dispatch use; see [trim] for the latter.
     *
     * @param keepMostRecent The number of the most recent items to keep for each entity.
     * @throws IllegalArgumentException If [keepMostRecent] is negative.
     */
    public fun truncate(keepMostRecent: Int) {
        truncate(keepMostRecent) { true }
    }

    /**
     * Truncates the history, deleting the items created before [olderThan],
     * but keeping at least [keepMostRecent] most recent items for each entity.
     *
     * An item is deleted only if it was created before the given time *and*
     * it is not among the [keepMostRecent] most recent items of its entity.
     * To purge everything older than the given time, pass zero as [keepMostRecent].
     *
     * The operation reads the whole history, so it is intended for periodic
     * maintenance rather than for per-dispatch use; see [trim] for the latter.
     *
     * @param keepMostRecent The number of the most recent items to keep for each entity.
     * @param olderThan Only the items created strictly before this time are deleted.
     * @throws IllegalArgumentException If [keepMostRecent] is negative.
     */
    public fun truncate(keepMostRecent: Int, olderThan: Timestamp) {
        truncate(keepMostRecent) { item ->
            // The column value comes from a complete stored item; never `null`.
            val created = checkNotNull(columns.created.valueIn(item))
            Timestamps.compare(created, olderThan) < 0
        }
    }

    private fun truncate(keepMostRecent: Int, deletionAllowed: (M) -> Boolean) {
        requireNotNegative(keepMostRecent)
        val newestFirst = queryBuilder()
            .sortDescendingBy(columns.version)
            .sortDescendingBy(columns.created)
            .build()
        val items = readAll(newestFirst)
        val seen = mutableMapOf<Any, Int>()
        val toDelete = mutableListOf<I>()
        items.forEach { item ->
            // The column value comes from a complete stored item; never `null`.
            val entityId = checkNotNull(columns.entity_id.valueIn(item))
            val count = (seen[entityId] ?: 0) + 1
            seen[entityId] = count
            if (count > keepMostRecent && deletionAllowed(item)) {
                toDelete.add(idOf(item))
            }
        }
        deleteAll(toDelete)
    }

    /**
     * Ensures the size of a window to keep is not negative.
     */
    protected fun requireNotNegative(keepMostRecent: Int) {
        require(keepMostRecent >= 0) {
            "The number of the items to keep must not be negative, got `$keepMostRecent`."
        }
    }

    /**
     * Obtains the record identifier of the given history item.
     */
    private fun idOf(item: M): I = recordSpec().idValueIn(item)

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
