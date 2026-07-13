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

import com.google.protobuf.Any
import com.google.protobuf.Timestamp
import io.spine.query.RecordColumn
import io.spine.query.RecordColumns
import io.spine.server.entity.EntityRecord

/**
 * Columns stored along with an [EntityRecord] kept by [EntityStateHistoryStorage].
 *
 * The column values are derived from the entity identifier and the [version]
 * of the stored record. The column set matches that of [EntityEventColumns],
 * so the state history and the event journal of an entity are queryable by
 * the same axes. The column names follow the storage-level `snake_case`
 * convention used by the other record kinds.
 */
@RecordColumns(ofType = EntityRecord::class)
public object EntityStateHistoryColumns : HistoryColumns<EntityRecord> {

    /**
     * Stores the identifier of the entity.
     */
    override val entity_id: RecordColumn<EntityRecord, Any> =
        RecordColumn.create("entity_id", Any::class.java) { record ->
            record.entityId
        }

    /**
     * Stores the time when the recorded state became current.
     *
     * The value is the timestamp of the record [version], stamped when
     * the signal that produced this state was dispatched to the entity.
     */
    override val created: RecordColumn<EntityRecord, Timestamp> =
        RecordColumn.create("created", Timestamp::class.java) { record ->
            record.version.timestamp
        }

    /**
     * Stores the number of the entity version this state belongs to.
     */
    override val version: RecordColumn<EntityRecord, Int> =
        RecordColumn.create("version", Int::class.javaObjectType) { record ->
            record.version.number
        }
}
