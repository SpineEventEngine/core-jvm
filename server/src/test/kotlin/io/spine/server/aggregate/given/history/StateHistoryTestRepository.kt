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

package io.spine.server.aggregate.given.history

import io.spine.server.aggregate.given.aggregate.AbstractAggregateTestRepository
import io.spine.server.entity.storage.EntityEventStorage
import io.spine.server.entity.storage.EntityStateHistoryStorage
import io.spine.test.aggregate.AggProject
import io.spine.test.aggregate.ProjectId

/**
 * A repository fixture opening the `protected` state history configuration
 * of `AggregateRepository` to Kotlin tests.
 *
 * Java tests reach `protected` members by residing in the same package;
 * Kotlin has no package-private access, so this subclass widens exactly
 * what the spec needs. Manages [HistoryReadingAggregate]s, which likewise
 * widen the state history reads of `Aggregate`.
 */
internal class StateHistoryTestRepository :
    AbstractAggregateTestRepository<ProjectId, HistoryReadingAggregate, AggProject>() {

    /**
     * When set, skips the durable write-through, simulating the mid-batch
     * state of `RepositoryCache`, which defers `doStore()` to the batch end.
     */
    var deferWriteThrough: Boolean = false

    /**
     * Performs the write-through unless [deferWriteThrough] is set.
     */
    override fun doStore(entity: HistoryReadingAggregate) {
        if (!deferWriteThrough) {
            super.doStore(entity)
        }
    }

    /**
     * Enables recording the state history.
     */
    fun enableStateHistory() = recordStateHistory()

    /**
     * Stops recording the state history.
     */
    fun disableStateHistory() = stopRecordingStateHistory()

    /**
     * Tells whether the state history recording is enabled.
     */
    fun historyEnabled(): Boolean = stateHistoryEnabled()

    /**
     * Returns the state history storage of this repository.
     */
    fun history(): EntityStateHistoryStorage<ProjectId> = stateHistory()

    /**
     * Returns the event journal of this repository.
     */
    fun journal(): EntityEventStorage<ProjectId> = eventStorage()

    /**
     * Enables the double-dispatch guard for the aggregates of this repository.
     */
    fun enableGuard() = useDoubleDispatchGuard()

    /**
     * Starts routing the writes of the aggregate with the given identifier
     * through the repository cache, as a delivery batch does.
     */
    fun beginBatch(id: ProjectId) = cache().startCaching(id)

    /**
     * Flushes the deferred writes of the aggregate with the given identifier,
     * as the end of a delivery batch does.
     */
    fun endBatch(id: ProjectId) = cache().stopCaching(id)
}
