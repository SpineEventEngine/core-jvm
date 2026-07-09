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

import com.google.protobuf.util.Timestamps.checkValid
import io.spine.base.Identifier
import io.spine.core.Event
import io.spine.server.entity.EntityEventRecord
import io.spine.server.entity.entityEventRecord
import io.spine.server.entity.entityEventRecordId

/**
 * A factory of [EntityEventRecord]s.
 */
public object EntityEventRecords {

    /**
     * Creates a new record for the event emitted by an entity.
     *
     * @param entityId The identifier of the entity which emitted the event.
     * @param event The event to transform into a record.
     * @return A new record.
     * @throws IllegalArgumentException If the event has no context or no message,
     *   if its identifier is empty or blank, or if its timestamp is not valid.
     */
    @JvmStatic
    public fun create(entityId: Any, event: Event): EntityEventRecord {
        require(event.hasContext()) { "Event context must be set." }
        require(event.hasMessage()) { "Event message must be set." }
        val idValue = Identifier.toString(event.id)
        require(idValue.isNotBlank()) { "Event ID must not be empty or blank." }
        val time = checkValid(event.context().timestamp)
        val packedId = Identifier.pack(entityId)
        val recordId = entityEventRecordId { value = idValue }
        // A local copy: inside the DSL block below, a bare `event` resolves to
        // the builder's own property, so the RHS must not use the parameter name.
        val emitted = event
        return entityEventRecord {
            id = recordId
            this.entityId = packedId
            timestamp = time
            this.event = emitted
        }
    }
}
