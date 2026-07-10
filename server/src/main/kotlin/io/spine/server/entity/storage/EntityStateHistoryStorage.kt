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

import com.google.protobuf.Timestamp
import com.google.protobuf.util.Timestamps
import io.spine.base.Identifier
import io.spine.query.RecordQuery
import io.spine.server.ContextSpec
import io.spine.server.entity.EntityRecord
import io.spine.server.entity.EntityStateId
import io.spine.server.entity.entityStateId
import io.spine.server.storage.MessageStorage
import io.spine.server.storage.RecordSpec
import io.spine.server.storage.StorageFactory

/**
 * The history of recent states of entities, retained to a bounded depth.
 *
 * The history stores the [EntityRecord] an entity had after each dispatch,
 * keyed by the entity identifier and the version number. It serves debugging
 * and analysis — e.g., answering the ["state at a time" query][stateAt] —
 * and is never used for loading: an entity loads from its latest record in
 * the corresponding [EntityRecordStorage].
 *
 * Recording is opt-in per repository and is off by default; see
 * [AggregateRepository.recordStateHistory][io.spine.server.aggregate.AggregateRepository.recordStateHistory].
 * The recording repository [trims][trim] the history to its configured depth
 * after each write, so how far back the history reaches depends on how often
 * the entity is updated.
 *
 * The records are stored as-is; the entity, the time the state became
 * current, and the version number are exposed for querying as the
 * [columns][EntityStateHistoryColumn] derived from the record.
 *
 * Nothing in this storage is specific to a kind of entity: `Aggregate`s
 * record their state history first, with `ProcessManager`s planned next.
 *
 * The class is deliberately final: storage vendors customize the persistence
 * via the [RecordStorage][io.spine.server.storage.RecordStorage] delegate
 * created by their [StorageFactory].
 *
 * @param context Specification of the Bounded Context in scope of which the storage is used.
 * @param factory The storage factory to use when creating a record storage delegate.
 */
public class EntityStateHistoryStorage(
    context: ContextSpec,
    factory: StorageFactory
) : MessageStorage<EntityStateId, EntityRecord>(
    context,
    factory.createRecordStorage(context, spec)
) {

    /**
     * Reads up to [batchSize] most recent state records of the entity with
     * the given identifier, ordered from newer to older.
     *
     * The records are sorted by their versions in the descending order.
     *
     * @param entityId The identifier of the entity.
     * @param batchSize The maximum number of the records to read.
     * @return An iterator over the read records.
     * @throws IllegalArgumentException If [batchSize] is not positive, or if the type
     *   of [entityId] is not supported by the framework.
     */
    public fun historyBackward(entityId: Any, batchSize: Int): Iterator<EntityRecord> {
        require(batchSize > 0) {
            "The batch size must be positive, got `$batchSize`."
        }
        val packedId = Identifier.pack(entityId)
        val query = queryBuilder()
            .where(EntityStateHistoryColumn.entityId).isEqualTo(packedId)
            .sortDescendingBy(EntityStateHistoryColumn.version)
            .limit(batchSize)
            .build()
        return readAll(query)
    }

    /**
     * Returns the state record the entity had at the given time,
     * if the history retains it.
     *
     * The result is the retained record with the highest version among those
     * whose state became current not later than [at]. When several retained
     * records share the very instant [at], the one with the highest version wins.
     *
     * The answer is honest about retention: `null` means the question cannot
     * be answered from the retained window — the time either precedes the
     * oldest retained record, or predates the entity itself.
     *
     * @param entityId The identifier of the entity.
     * @param at The point in time to look at.
     * @return The record effective at the given time, or `null` if the history
     *   does not retain it.
     * @throws IllegalArgumentException If the type of [entityId] is not supported
     *   by the framework.
     */
    public fun stateAt(entityId: Any, at: Timestamp): EntityRecord? {
        val packedId = Identifier.pack(entityId)
        val newestFirst = queryBuilder()
            .where(EntityStateHistoryColumn.entityId).isEqualTo(packedId)
            .sortDescendingBy(EntityStateHistoryColumn.version)
            .build()
        val records = readAll(newestFirst)
        return records.asSequence()
            .firstOrNull { Timestamps.compare(it.version.timestamp, at) <= 0 }
    }

    /**
     * Stores the given state record in the history.
     *
     * The record is stored as-is, keyed by the entity identifier and the
     * version number. Writing a record with the same entity and version as an
     * already stored record replaces that record instead of duplicating it.
     *
     * @param message The state record to store.
     * @throws IllegalArgumentException If the record has no entity identifier,
     *   no version, or its version has no timestamp.
     */
    public override fun write(message: EntityRecord) {
        require(message.hasEntityId()) {
            "The state record must have the entity identifier."
        }
        require(message.hasVersion()) {
            "The state record must have a version."
        }
        require(message.version.hasTimestamp()) {
            "The version of the state record must have a timestamp."
        }
        super.write(message)
    }

    /**
     * Trims the history of the entity with the given identifier, keeping up
     * to [keepMostRecent] most recent records.
     *
     * Unlike [truncate], the operation reads only the records of the given
     * entity, so the recording repositories use it after each write.
     * Passing zero purges the history of the entity.
     *
     * @param entityId The identifier of the entity.
     * @param keepMostRecent The number of the most recent records to keep.
     * @throws IllegalArgumentException If [keepMostRecent] is negative, or if the type
     *   of [entityId] is not supported by the framework.
     */
    public fun trim(entityId: Any, keepMostRecent: Int) {
        require(keepMostRecent >= 0) {
            "The number of the records to keep must not be negative, got `$keepMostRecent`."
        }
        val packedId = Identifier.pack(entityId)
        val newestFirst = queryBuilder()
            .where(EntityStateHistoryColumn.entityId).isEqualTo(packedId)
            .sortDescendingBy(EntityStateHistoryColumn.version)
            .build()
        val records = readAll(newestFirst)
        val toDelete = records.asSequence()
            .drop(keepMostRecent)
            .map { it.stateId() }
            .toList()
        if (toDelete.isNotEmpty()) {
            deleteAll(toDelete)
        }
    }

    /**
     * Truncates the history, keeping up to [keepMostRecent] most recent
     * records for each entity.
     *
     * The most recent records are determined per entity, by the version
     * number. Passing zero purges the whole history.
     *
     * The operation reads the whole history, so it is intended for periodic
     * maintenance rather than for per-dispatch use; see [trim] for the latter.
     *
     * @param keepMostRecent The number of the most recent records to keep for each entity.
     * @throws IllegalArgumentException If [keepMostRecent] is negative.
     */
    public fun truncate(keepMostRecent: Int) {
        truncate(keepMostRecent) { true }
    }

    /**
     * Truncates the history, deleting the records whose states became current
     * before [olderThan], but keeping at least [keepMostRecent] most recent
     * records for each entity.
     *
     * A record is deleted only if it is older than the given time *and* it is
     * not among the [keepMostRecent] most recent records of its entity.
     * To purge everything older than the given time, pass zero as [keepMostRecent].
     *
     * The operation reads the whole history, so it is intended for periodic
     * maintenance rather than for per-dispatch use; see [trim] for the latter.
     *
     * @param keepMostRecent The number of the most recent records to keep for each entity.
     * @param olderThan Only the records created strictly before this time are deleted.
     * @throws IllegalArgumentException If [keepMostRecent] is negative.
     */
    public fun truncate(keepMostRecent: Int, olderThan: Timestamp) {
        truncate(keepMostRecent) { record ->
            Timestamps.compare(record.version.timestamp, olderThan) < 0
        }
    }

    private fun truncate(keepMostRecent: Int, deletionAllowed: (EntityRecord) -> Boolean) {
        require(keepMostRecent >= 0) {
            "The number of the records to keep must not be negative, got `$keepMostRecent`."
        }
        val newestFirst = queryBuilder()
            .sortDescendingBy(EntityStateHistoryColumn.version)
            .build()
        val records = readAll(newestFirst)
        val seen = mutableMapOf<Any, Int>()
        val toDelete = mutableListOf<EntityStateId>()
        records.forEach { record ->
            val entityId = record.entityId
            val count = (seen[entityId] ?: 0) + 1
            seen[entityId] = count
            if (count > keepMostRecent && deletionAllowed(record)) {
                toDelete.add(record.stateId())
            }
        }
        deleteAll(toDelete)
    }

    /**
     * Reads all the history records matching the given query.
     *
     * Overrides to expose the method as a part of the public API of this storage.
     */
    public override fun readAll(
        query: RecordQuery<EntityStateId, EntityRecord>
    ): Iterator<EntityRecord> = super.readAll(query)

    /**
     * Deletes the history record with the given identifier.
     *
     * The history is maintained by the framework write path; this method
     * exists for the maintenance operations, such as the [truncate] trimming.
     *
     * Overrides to expose the method as a part of the public API of this storage.
     *
     * @return `true` if the record was deleted, `false` if it was not found.
     */
    public override fun delete(id: EntityStateId): Boolean = super.delete(id)

    /**
     * Deletes the history records with the given identifiers.
     *
     * The history is maintained by the framework write path; this method
     * exists for the maintenance operations, such as the [truncate] trimming.
     *
     * Overrides to expose the method as a part of the public API of this storage.
     */
    public override fun deleteAll(ids: Iterable<EntityStateId>) {
        super.deleteAll(ids)
    }
}

/**
 * A specification on how to store the state records.
 */
private val spec: RecordSpec<EntityStateId, EntityRecord> = RecordSpec(
    EntityStateId::class.java,
    EntityRecord::class.java,
    // The parameter is nullable only because the SAM inherits Guava's `Function`;
    // the framework never passes `null` records.
    { record -> requireNotNull(record).stateId() },
    EntityStateHistoryColumn.definitions()
)

/**
 * Composes the history record key of this state record.
 */
private fun EntityRecord.stateId(): EntityStateId = entityStateId {
    entityId = this@stateId.entityId
    version = this@stateId.version.number
}
