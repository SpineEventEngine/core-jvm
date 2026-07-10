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
import io.spine.core.Event
import io.spine.core.EventId
import io.spine.core.Version
import io.spine.query.RecordQuery
import io.spine.server.ContextSpec
import io.spine.server.entity.EntityEventRecord
import io.spine.server.entity.entityEventRecord
import io.spine.server.storage.MessageStorage
import io.spine.server.storage.RecordSpec
import io.spine.server.storage.StorageFactory

/**
 * The journal of events emitted by an entity.
 *
 * The journal is an append-only storage of [EntityEventRecord]s kept for traceability
 * and recent-history lookups. It is not used for restoring entity states: an entity
 * loads from its latest record in the corresponding [EntityRecordStorage].
 *
 * Currently, the framework journals the events emitted by
 * [Aggregate][io.spine.server.aggregate.Aggregate]s; see
 * [AggregateStorage][io.spine.server.aggregate.AggregateStorage].
 *
 * This storage supersedes the `AggregateEventStorage`, removed along with the other
 * event-sourcing machinery. The records of the two storages are distinct kinds:
 * journal entries written by pre-cutover versions of the framework — the retained
 * [AggregateEventRecord][io.spine.server.aggregate.AggregateEventRecord]s — are not
 * visible to the reads performed by this storage.
 *
 * The class is deliberately final: storage vendors customize the persistence via
 * the [RecordStorage][io.spine.server.storage.RecordStorage] delegate created by
 * their [StorageFactory].
 *
 * @param context Specification of the Bounded Context in scope of which the storage is used.
 * @param factory The storage factory to use when creating a record storage delegate.
 */
public class EntityEventStorage(
    context: ContextSpec,
    factory: StorageFactory
) : MessageStorage<EventId, EntityEventRecord>(
    context,
    factory.createRecordStorage(context, spec)
) {

    /**
     * Reads up to [batchSize] most recent journal records of the entity with
     * the given identifier, ordered from newer to older.
     *
     * The records are sorted by the version of the stored event and then by
     * the time the event was emitted, both in the descending order.
     *
     * @param entityId The identifier of the entity that emitted the journaled events.
     * @param batchSize The maximum number of the records to read.
     * @param startingFrom If set, only the events with versions lower than this one are read.
     * @return An iterator over the read records.
     * @throws IllegalArgumentException If [batchSize] is not positive, or if the type
     *   of [entityId] is not supported by the framework.
     */
    @JvmOverloads
    public fun historyBackward(
        entityId: Any,
        batchSize: Int,
        startingFrom: Version? = null
    ): Iterator<EntityEventRecord> {
        require(batchSize > 0) {
            "The batch size must be positive, got `$batchSize`."
        }
        val packedId = Identifier.pack(entityId)
        val builder = queryBuilder()
            .where(EntityEventRecordColumn.entityId).isEqualTo(packedId)
        if (startingFrom != null) {
            builder.where(EntityEventRecordColumn.version)
                .isLessThan(startingFrom.number)
        }
        val query = builder
            .sortDescendingBy(EntityEventRecordColumn.version)
            .sortDescendingBy(EntityEventRecordColumn.created)
            .limit(batchSize)
            .build()
        return readAll(query)
    }

    /**
     * Journals the given event.
     *
     * The entity which emitted the event is determined by the producer ID of
     * the event context; the record is stored under the identifier of the event.
     *
     * Before storing, the enrichments are cleared from the event context and from
     * its first-level origin (see `Event.clearEnrichments()`). Clearing rebuilds
     * the event, which also validates it: an incomplete instance — e.g., missing
     * its identifier, context, message, or producer — is rejected.
     *
     * @param event The event to journal.
     * @throws io.spine.validation.ValidationException If the event is incomplete.
     * @throws IllegalArgumentException If the event timestamp is not valid.
     */
    public fun write(event: Event) {
        write(toRecord(event.clearEnrichments()))
    }

    /**
     * Transforms the given event into the journal record.
     */
    private fun toRecord(event: Event): EntityEventRecord {
        val context = event.context()
        val time = Timestamps.checkValid(context.timestamp)
        // A local copy: inside the DSL block below, a bare `event` resolves to
        // the builder's own property, so the RHS must not use the parameter name.
        val emitted = event
        return entityEventRecord {
            id = emitted.id
            entityId = context.producerId
            timestamp = time
            this.event = emitted
        }
    }

    /**
     * Truncates the journal, keeping up to [keepMostRecent] most recent records
     * for each entity.
     *
     * The most recent records are determined per entity, in the order of
     * [historyBackward]: by the version of the stored event and then by the time
     * the event was emitted. Passing zero purges the whole journal.
     *
     * The operation reads the whole journal, so it is intended for periodic
     * maintenance rather than for per-dispatch use.
     *
     * @param keepMostRecent The number of the most recent records to keep for each entity.
     * @throws IllegalArgumentException If [keepMostRecent] is negative.
     */
    public fun truncate(keepMostRecent: Int) {
        truncate(keepMostRecent) { true }
    }

    /**
     * Truncates the journal, deleting the records older than [olderThan],
     * but keeping at least [keepMostRecent] most recent records for each entity.
     *
     * A record is deleted only if its event was emitted before the given time *and*
     * the record is not among the [keepMostRecent] most recent records of its entity.
     * To purge everything older than the given time, pass zero as [keepMostRecent].
     *
     * The operation reads the whole journal, so it is intended for periodic
     * maintenance rather than for per-dispatch use.
     *
     * @param keepMostRecent The number of the most recent records to keep for each entity.
     * @param olderThan Only the records of the events emitted strictly before this time
     *   are deleted.
     * @throws IllegalArgumentException If [keepMostRecent] is negative.
     */
    public fun truncate(keepMostRecent: Int, olderThan: Timestamp) {
        truncate(keepMostRecent) { record ->
            Timestamps.compare(record.timestamp, olderThan) < 0
        }
    }

    private fun truncate(keepMostRecent: Int, deletionAllowed: (EntityEventRecord) -> Boolean) {
        require(keepMostRecent >= 0) {
            "The number of the records to keep must not be negative, got `$keepMostRecent`."
        }
        val newestFirst = queryBuilder()
            .sortDescendingBy(EntityEventRecordColumn.version)
            .sortDescendingBy(EntityEventRecordColumn.created)
            .build()
        val records = readAll(newestFirst)
        val seen = mutableMapOf<Any, Int>()
        val toDelete = mutableListOf<EventId>()
        records.forEach { record ->
            val entityId = record.entityId
            val count = (seen[entityId] ?: 0) + 1
            seen[entityId] = count
            if (count > keepMostRecent && deletionAllowed(record)) {
                toDelete.add(record.id)
            }
        }
        deleteAll(toDelete)
    }

    /**
     * Reads all the journal records matching the given query.
     *
     * Overrides to expose the method as a part of the public API of this storage.
     */
    public override fun readAll(
        query: RecordQuery<EventId, EntityEventRecord>
    ): Iterator<EntityEventRecord> = super.readAll(query)

    /**
     * Appends the given record to the journal.
     *
     * Overrides to expose the method as a part of the public API of this storage.
     */
    public override fun write(message: EntityEventRecord) {
        super.write(message)
    }

    /**
     * Deletes the journal record with the given identifier.
     *
     * The journal is append-only for the framework write path; this method exists
     * for the maintenance operations, such as the [truncate] trimming.
     *
     * Overrides to expose the method as a part of the public API of this storage.
     *
     * @return `true` if the record was deleted, `false` if it was not found.
     */
    public override fun delete(id: EventId): Boolean = super.delete(id)

    /**
     * Deletes the journal records with the given identifiers.
     *
     * The journal is append-only for the framework write path; this method exists
     * for the maintenance operations, such as the [truncate] trimming.
     *
     * Overrides to expose the method as a part of the public API of this storage.
     */
    public override fun deleteAll(ids: Iterable<EventId>) {
        super.deleteAll(ids)
    }
}

/**
 * A specification on how to store the event records of an entity.
 */
private val spec: RecordSpec<EventId, EntityEventRecord> = RecordSpec(
    EventId::class.java,
    EntityEventRecord::class.java,
    // The parameter is nullable only because the SAM inherits Guava's `Function`;
    // the framework never passes `null` records.
    { record -> requireNotNull(record).id },
    EntityEventRecordColumn.definitions()
)
