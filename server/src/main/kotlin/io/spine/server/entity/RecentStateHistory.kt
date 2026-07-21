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
import io.spine.base.EntityState
import io.spine.core.Version
import io.spine.protobuf.AnyPacker

/**
 * The recent history of states of an [AbstractEntity].
 *
 * The states are read from the durable state history via the loader
 * [installed][AbstractEntity.setStateHistoryLoader] by the repository
 * of the entity, and cached for the lifetime of the entity instance —
 * see [RecentHistory]. A record is unpacked into the entity state type
 * once — eagerly when appended, or lazily when first traversed from storage.
 *
 * The repository installs the loader unconditionally; whether it records
 * the state history gates only the loader behavior — while the recording
 * is off, reading through the loader fails fast. An entity created outside
 * a repository has no loader at all, so the reads serve only
 * the [appended][append] states, if any, and [stateAt] answers `null`.
 *
 * @param S The type of the entity state.
 */
public class RecentStateHistory<S : EntityState<*>> internal constructor() :
    RecentHistory<EntityRecord, S, StateHistoryLoader>() {

    /**
     * Returns the state the entity had at the given time, if the recorded
     * history retains it.
     *
     * `null` means the question cannot be answered from the retained
     * window — the time either precedes the oldest retained record, or
     * predates the entity itself — or that the entity was created outside
     * a repository and has no recorded history at all.
     *
     * @param at The point in time to look at.
     */
    public fun stateAt(at: Timestamp): S? =
        loader()?.stateAt(at)
            ?.unpackState()

    override fun toItem(record: EntityRecord): S = record.unpackState()

    override fun versionOf(record: EntityRecord): Version = record.version
}

/**
 * Extracts the entity state of the given type from this record.
 */
private fun <S : EntityState<*>> EntityRecord.unpackState(): S {
    // The cast is safe: the record holds the state of the entity
    // whose history is being read.
    @Suppress("UNCHECKED_CAST")
    return AnyPacker.unpack(state) as S
}
