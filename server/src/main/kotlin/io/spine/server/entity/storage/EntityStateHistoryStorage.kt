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
import io.spine.server.ContextSpec
import io.spine.server.entity.EntityRecord
import io.spine.server.entity.EntityStateId
import io.spine.server.entity.entityStateId
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
 * in `AggregateRepository`. The recording repository [trims][trim] the history
 * to its configured depth after each write, so how far back the history
 * reaches depends on how often the entity is updated.
 *
 * The records are stored as-is; the entity, the time the state became
 * current, and the version number are exposed for querying as the
 * [columns][EntityStateHistoryColumn] derived from the record.
 *
 * Nothing in this storage is specific to a kind of entity; currently,
 * `Aggregate`s are the only kind recording their state history.
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
) : HistoryStorage<EntityStateId, EntityRecord>(context, factory, spec) {

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
     *   no version, or its version has no timestamp.
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
    public override fun write(id: EntityStateId, message: EntityRecord) {
        validate(message)
        require(id == message.stateId()) {
            "The passed identifier does not match the entity and the version of the record."
        }
        super.write(id, message)
    }

    /**
     * Ensures the record carries the fields the history relies upon.
     */
    private fun validate(record: EntityRecord) {
        require(record.hasEntityId()) {
            "The state record must have the entity identifier."
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
 * A specification on how to store the state records.
 */
private val spec: HistorySpec<EntityStateId, EntityRecord> = HistorySpec(
    EntityStateId::class.java,
    EntityRecord::class.java,
    { record -> record.stateId() },
    EntityStateHistoryColumn.entityId,
    EntityStateHistoryColumn.created,
    EntityStateHistoryColumn.version
)

/**
 * Composes the history record key of this state record.
 */
private fun EntityRecord.stateId(): EntityStateId = entityStateId {
    entityId = this@stateId.entityId
    version = this@stateId.version.number
}
