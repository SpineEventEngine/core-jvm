/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package io.spine.server.entity.storage

import io.spine.core.Event
import io.spine.core.EventId
import io.spine.server.ContextSpec
import io.spine.server.entity.Entity
import io.spine.server.storage.RecordSpec
import io.spine.server.storage.StorageFactory
import io.spine.server.storage.StorageGroup
import io.spine.validation.checkValid

/**
 * The journal of events emitted by an entity.
 *
 * The journal is an append-only [HistoryStorage] of [Event]s kept for
 * traceability and recent-history lookups. It is not used for restoring
 * entity states: an entity loads from its latest record in the
 * corresponding [EntityRecordStorage].
 *
 * The events are stored as-is, keyed by their identifiers; the entity which
 * emitted an event, the event time, and the event version are exposed for
 * querying as the [columns][EntityEventColumns] derived from the event
 * context.
 *
 * This storage supersedes the `AggregateEventStorage`, removed along with the other
 * event-sourcing machinery.
 *
 * The journal is identified by the served entity class paired with the
 * type of the stored items, [Event]: vendors allocate the physical
 * storage by this pair (see
 * [createRecordStorage][io.spine.server.storage.StorageFactory.createRecordStorage]),
 * so a journal stays apart from the journals of other entity classes — even
 * when their identifier values coincide — and from the other storages of
 * its own entity class.
 *
 * The class is deliberately final: storage vendors customize the persistence via
 * the [RecordStorage][io.spine.server.storage.RecordStorage] delegate created by
 * their [StorageFactory].
 *
 * @param I The type of the identifiers of the entities whose events are journaled.
 * @param context The specification of the Bounded Context in the scope of which
 *                the storage is used.
 * @param factory The storage factory to use when creating a record storage delegate.
 * @param entityClass The class of the entities whose events are journaled.
 */
public class EntityEventStorage<I : Any>(
    context: ContextSpec,
    factory: StorageFactory,
    entityClass: Class<out Entity<I, *>>
) : HistoryStorage<I, EventId, Event>(
    context,
    HistoryStorageSpec(recordSpec, EntityEventColumns, StorageGroup.of(entityClass)),
    factory
) {
    /**
     * Journals the given event.
     *
     * The event is stored as-is, keyed by its identifier; the entity which emitted
     * the event is determined by the producer ID of the event context.
     *
     * Before storing, the event is validated: an incomplete instance — e.g., missing
     * its identifier, context, or message — is rejected.
     *
     * @param message The event to journal.
     * @throws io.spine.validation.ValidationException If the event is incomplete.
     */
    public override fun write(message: Event) {
        super.write(message.checkValid())
    }

    /**
     * Journals the given event under the given identifier.
     *
     * The record key of this journal is the event identifier, so the passed
     * identifier must match the [id][Event.getId] of the event; prefer the
     * one-argument [write].
     *
     * The event is validated the same way as by the one-argument [write].
     *
     * @param id The identifier of the record.
     * @param message The event to journal.
     * @throws IllegalArgumentException If the identifier does not match
     *   the identifier of the event.
     * @throws io.spine.validation.ValidationException If the event is incomplete.
     */
    @Synchronized
    public override fun write(id: EventId, message: Event) {
        require(id == message.id) {
            "The passed identifier does not match the identifier of the event."
        }
        super.write(id, message.checkValid())
    }
}

/**
 * Composes a specification on how to store the events emitted by the entities
 * of the given class.
 *
 * The state class of the entity becomes the source type of the specification;
 * paired with the record type, [Event], it is the identity by which storage
 * vendors allocate the physical storage.
 */
private val recordSpec: RecordSpec<EventId, Event> = RecordSpec(
    Event::class.java,
    EventId::class.java,
    Event::class.java,
    EntityEventColumns.definitions()
) { event -> event.id }
