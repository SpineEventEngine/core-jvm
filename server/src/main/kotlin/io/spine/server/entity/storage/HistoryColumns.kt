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
import com.google.protobuf.Message
import com.google.protobuf.Timestamp
import io.spine.query.Columns
import io.spine.query.RecordColumn

/**
 * The columns every per-entity history exposes.
 *
 * A [HistoryStorage] manages a history and queries it efficiently through
 * these columns.
 *
 * @param M The type of the stored history items.
 * @see EntityEventColumns
 * @see EntityStateHistoryColumns
 */
public interface HistoryColumns<M : Message> {

    /**
     * The column with the packed identifier of the entity.
     */
    @Suppress("VariableNaming", "PropertyName")
        // Named after the column, per the `RecordColumns` contract.
    public val entity_id: RecordColumn<M, Any>

    /**
     * The column with the time the item was created.
     */
    public val created: RecordColumn<M, Timestamp>

    /**
     * The column with the number of the entity version the item belongs to.
     */
    public val version: RecordColumn<M, Int>

    /**
     * Returns all the column definitions.
     */
    public fun definitions(): Columns<M> = Columns.of(entity_id, created, version)
}
