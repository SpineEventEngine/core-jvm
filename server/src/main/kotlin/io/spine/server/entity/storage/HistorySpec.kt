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
import io.spine.server.entity.Entity
import io.spine.server.entity.model.EntityClass
import io.spine.server.storage.RecordSpec

/**
 * The specification of a per-entity history: which entity class the history
 * serves, how its items are stored, and which of their columns carry
 * the history semantics.
 *
 * Combines the identification of the stored items — their types and the
 * identifier extraction — with the [HistoryColumns] every history exposes.
 * [HistoryStorage] manages and queries the history through these columns.
 *
 * The pair of the [entityClass] and the [itemType] identifies the physical
 * storage: storage vendors allocate a distinct table, a kind, and the like
 * per pair in their
 * [createHistoryStorage][io.spine.server.storage.StorageFactory.createHistoryStorage],
 * naming it at their discretion. The pair keeps a history apart from
 * the histories of other entity classes, from the history of the same
 * class storing another kind of items, and — the seam itself being
 * dedicated to histories — from the non-history storages, such as the
 * storage of the latest entity states.
 *
 * Instances are composed by the histories themselves — see
 * [EntityEventStorage] and [EntityStateHistoryStorage] — so
 * the constructor is `internal`.
 *
 * @param I The type of the record identifiers.
 * @param M The type of the stored history items.
 * @param idType The class of the record identifiers.
 * @property itemType The class of the stored items.
 * @property entityClass The class of the entities the history serves.
 * @property columns The columns of the history.
 * @param extractId Obtains the record identifier of an item.
 */
public class HistorySpec<I : Any, M : Message> internal constructor(
    idType: Class<I>,
    public val itemType: Class<M>,
    public val entityClass: Class<out Entity<*, *>>,
    public val columns: HistoryColumns<M>,
    extractId: (M) -> I
) {

    /**
     * The specification of the record storage persisting the history items.
     *
     * The source type of this specification is the state class of the served
     * [entityClass], derived per the one-to-one convention between the entity
     * classes and their states.
     *
     * Storage vendors use this value to create the
     * [RecordStorage][io.spine.server.storage.RecordStorage] delegate in their
     * [createHistoryStorage][io.spine.server.storage.StorageFactory.createHistoryStorage].
     */
    public val recordSpec: RecordSpec<I, M> = RecordSpec(
        EntityClass.stateClassOf(entityClass),
        idType,
        itemType,
        columns.definitions()
    ) { item -> extractId(item) }
}
