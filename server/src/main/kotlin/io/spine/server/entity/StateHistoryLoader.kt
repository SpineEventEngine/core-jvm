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

package io.spine.server.entity

import com.google.protobuf.Timestamp
import io.spine.annotation.Internal

/**
 * Loads the recorded state history of an entity from the durable storage.
 *
 * An instance is installed via [TransactionalEntity.setStateHistoryLoader]
 * by the repository owning the entity — see
 * `AggregateRepository.recordStateHistory(int)`. An entity created outside
 * a repository has no loader, and its state history reads come back empty.
 *
 * @see io.spine.server.entity.storage.EntityStateHistoryStorage
 */
@Internal
public interface StateHistoryLoader {

    /**
     * Loads up to [depth] most recent state records of the entity,
     * ordered from newer to older.
     *
     * @param depth The maximum number of the records to load.
     */
    public fun load(depth: Int): Iterator<EntityRecord>

    /**
     * Loads the state record the entity had at the given time, or `null`
     * if the recorded history does not retain it.
     *
     * @param at The point in time to look at.
     */
    public fun stateAt(at: Timestamp): EntityRecord?
}
