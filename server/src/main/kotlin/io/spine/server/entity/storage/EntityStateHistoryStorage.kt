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
import io.spine.server.ContextSpec
import io.spine.server.entity.Entity
import io.spine.server.entity.EntityRecord
import io.spine.server.entity.EntityStateKey
import io.spine.server.entity.entityStateKey
import io.spine.server.storage.StorageFactory

/**
 * The history of recent states of entities, retained to a bounded depth.
 *
 * This [HistoryStorage] stores the [EntityRecord] an entity had after each
 * dispatch, keyed by the entity identifier and the version number. Entities
 * read their recorded states through a
 * [StateHistoryLoader][io.spine.server.entity.StateHistoryLoader]
 * installed by the repository — e.g., `Aggregate.stateAt(Timestamp)` — and
 * the storage also serves debugging and analysis, answering
 * the ["state at a time" query][stateAt]. The history is never used for
 * loading: an entity loads from its latest record in the corresponding
 * [EntityRecordStorage].
 *
 * Recording is opt-in per repository and is off by default; see
 * [recordStateHistory][io.spine.server.aggregate.AggregateRepository.recordStateHistory]
 * in `AggregateRepository`. The history is not trimmed automatically:
 * retention is the duty of the application, served by the [trim] and
 * [truncate] maintenance operations.
 *
 * The records are stored as-is; the entity, the time the state became
 * current, and the version number are exposed for querying as the
 * [columns][EntityStateHistoryColumns] derived from the record.
 *
 * Nothing in this storage is specific to a kind of entity; currently,
 * `Aggregate`s are the only kind recording their state history.
 *
 * The storage is identified by the served entity class paired with the
 * type of the stored items, [EntityRecord]: vendors allocate the
 * physical storage by this pair (see
 * [createHistoryStorage][io.spine.server.storage.StorageFactory.createHistoryStorage]),
 * so a state history stays apart from the histories of other entity
 * classes — even when their identifier values coincide — and from the
 * latest-state records of its own entity class, which never arrive at
 * the history seam.
 *
 * The class is deliberately final: storage vendors customize the persistence
 * via the [RecordStorage][io.spine.server.storage.RecordStorage] delegate
 * created by their [StorageFactory].
 *
 * @param context Specification of the Bounded Context in scope of which the storage is used.
 * @param factory The storage factory to use when creating a record storage delegate.
 * @param entityClass The class of the entities whose states are recorded.
 */
public class EntityStateHistoryStorage(
    context: ContextSpec,
    factory: StorageFactory,
    entityClass: Class<out Entity<*, *>>
) : HistoryStorage<EntityStateKey, EntityRecord>(context, factory, specFor(entityClass)) {

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
    public fun stateAt(entityId: Any, at: Timestamp): EntityRecord? =
        historyBackward(entityId, Int.MAX_VALUE)
            .asSequence()
            .firstOrNull { Timestamps.compare(it.version.timestamp, at) <= 0 }

    /**
     * Stores the given state record in the history.
     *
     * The record is stored as-is, keyed by the entity identifier and the
     * version number. Writing a record with the same entity and version as an
     * already stored record replaces that record instead of duplicating it.
     *
     * @param message The state record to store.
     * @throws IllegalArgumentException If the record has no entity identifier,
     *   no state, no version, or its version has no timestamp.
     */
    public override fun write(message: EntityRecord) {
        validate(message)
        super.write(message)
    }

    /**
     * Stores the given state record under the given identifier.
     *
     * The record key of this storage is derived from the record content,
     * so the passed identifier must match the entity and the version of
     * the record; prefer the one-argument [write].
     *
     * @param id The identifier of the record.
     * @param message The state record to store.
     * @throws IllegalArgumentException If the record is incomplete (see the
     *   one-argument [write]), or if the identifier does not match the record.
     */
    @Synchronized
    public override fun write(id: EntityStateKey, message: EntityRecord) {
        validate(message)
        require(id == message.stateKey()) {
            "The passed identifier does not match the entity and the version of the record."
        }
        super.write(id, message)
    }

    /**
     * Trims the history of the entity with the given identifier, keeping up
     * to [keepMostRecent] most recent records.
     *
     * Overrides the generic implementation with an identifier-only read:
     * the [record keys][EntityStateKey] of this storage carry the version,
     * so ranking the records needs no record payloads. Trimming thus
     * reads small identifiers instead of full records with packed states.
     */
    override fun trim(entityId: Any, keepMostRecent: Int) {
        requireNotNegative(keepMostRecent)
        val packedId = Identifier.pack(entityId)
        val selection = queryBuilder()
            .where(EntityStateHistoryColumns.entity_id).isEqualTo(packedId)
            .build()
        val ids = index(selection)
        val toDelete = ids.asSequence()
            .sortedByDescending { it.version }
            .drop(keepMostRecent)
            .toList()
        if (toDelete.isNotEmpty()) {
            deleteAll(toDelete)
        }
    }

    /**
     * Ensures the record carries the fields the history relies upon.
     */
    private fun validate(record: EntityRecord) {
        require(record.hasEntityId()) {
            "The state record must have the entity identifier."
        }
        require(record.hasState()) {
            "The state record must have the entity state."
        }
        require(record.hasVersion()) {
            "The state record must have a version."
        }
        require(record.version.hasTimestamp()) {
            "The version of the state record must have a timestamp."
        }
    }
}

/**
 * Composes a specification on how to store the state records of the entities
 * of the given class.
 *
 * The entity class, paired with the item type, is the identity by which
 * storage vendors allocate the physical storage.
 */
private fun specFor(
    entityClass: Class<out Entity<*, *>>
): HistorySpec<EntityStateKey, EntityRecord> = HistorySpec(
    entityClass = entityClass,
    idType = EntityStateKey::class.java,
    itemType = EntityRecord::class.java,
    columns = EntityStateHistoryColumns
) { record -> record.stateKey() }

/**
 * Composes the history record key of this state record.
 */
private fun EntityRecord.stateKey(): EntityStateKey = entityStateKey {
    entityId = this@stateKey.entityId
    version = this@stateKey.version.number
}
