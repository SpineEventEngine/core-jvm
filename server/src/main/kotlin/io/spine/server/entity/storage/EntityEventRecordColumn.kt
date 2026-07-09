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
import io.spine.query.Columns
import io.spine.query.RecordColumn
import io.spine.query.RecordColumns
import io.spine.server.entity.EntityEventRecord

/**
 * Columns stored along with an [EntityEventRecord].
 *
 * The column names follow the storage-level `snake_case` convention used
 * by the other record kinds.
 */
@RecordColumns(ofType = EntityEventRecord::class)
public object EntityEventRecordColumn {

    /**
     * Stores the identifier of the entity which emitted the event.
     */
    @JvmField
    public val entityId: RecordColumn<EntityEventRecord, Any> =
        RecordColumn.create("entity_id", Any::class.java, EntityEventRecord::getEntityId)

    /**
     * Stores the time when the event record was created.
     */
    @JvmField
    public val created: RecordColumn<EntityEventRecord, Timestamp> =
        RecordColumn.create("created", Timestamp::class.java, EntityEventRecord::getTimestamp)

    /**
     * Stores the version of the stored event.
     */
    @JvmField
    public val version: RecordColumn<EntityEventRecord, Int> =
        RecordColumn.create("version", Int::class.javaObjectType) { record ->
            record.event.context.version.number
        }

    /**
     * Returns all the column definitions.
     */
    @JvmStatic
    public fun definitions(): Columns<EntityEventRecord> = Columns.of(entityId, created, version)
}
