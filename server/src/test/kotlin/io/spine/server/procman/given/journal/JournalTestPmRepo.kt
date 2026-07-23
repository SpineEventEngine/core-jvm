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

package io.spine.server.procman.given.journal

import io.spine.server.entity.storage.EntityEventStorage
import io.spine.server.procman.ProcessManagerRepository
import io.spine.test.procman.ElephantProcess
import io.spine.test.procman.ProjectId

/**
 * A test repository of [JournalTestProcman], configuring the event journaling
 * and the double-dispatch guard as the specs require.
 */
internal class JournalTestPmRepo(
    journaling: Boolean = true,
    guard: Boolean = false
) : ProcessManagerRepository<ProjectId, JournalTestProcman, ElephantProcess>() {

    init {
        if (journaling) {
            recordEventHistory()
        }
        if (guard) {
            useDoubleDispatchGuard()
        }
    }

    /**
     * Exposes the event journal to the tests.
     */
    fun journal(): EntityEventStorage<ProjectId> = eventStorage()

    /**
     * Exposes the event-history depth setter to the tests.
     */
    fun depth(value: Int) {
        setEventHistoryDepth(value)
    }

    /**
     * Starts serving the given entity from the cache, as a batched delivery would.
     */
    fun beginBatch(id: ProjectId) = cache().startCaching(id)

    /**
     * Stops serving the given entity from the cache, flushing the deferred store.
     */
    fun endBatch(id: ProjectId) = cache().stopCaching(id)
}
