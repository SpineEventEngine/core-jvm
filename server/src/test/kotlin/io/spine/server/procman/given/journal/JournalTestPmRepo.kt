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

package io.spine.server.procman.given.journal

import io.spine.server.entity.storage.EntityEventStorage
import io.spine.server.procman.ProcessManagerRepository
import io.spine.test.procman.ElephantProcess
import io.spine.test.procman.ProjectId

/**
 * A test repository of [JournalTestProcman], configuring the event journaling
 * as the specs require.
 */
internal class JournalTestPmRepo(
    journaling: Boolean = true
) : ProcessManagerRepository<ProjectId, JournalTestProcman, ElephantProcess>() {

    init {
        if (journaling) {
            recordEventHistory()
        }
    }

    /**
     * Exposes the event journal to the tests.
     */
    fun journal(): EntityEventStorage<ProjectId> = eventStorage()

    /**
     * Starts serving the given entity from the cache, as a batched delivery would.
     */
    fun beginBatch(id: ProjectId) = cache().startCaching(id)

    /**
     * Stops serving the given entity from the cache, flushing the deferred store.
     */
    fun endBatch(id: ProjectId) = cache().stopCaching(id)
}
