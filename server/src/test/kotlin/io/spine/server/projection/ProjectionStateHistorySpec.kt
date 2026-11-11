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

package io.spine.server.projection

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.spine.base.Identifier.newUuid
import io.spine.server.BoundedContext
import io.spine.server.BoundedContextBuilder
import io.spine.server.entity.EntityRecord
import io.spine.server.entity.storage.EntityStateHistoryStorage
import io.spine.server.projection.given.TestProjection
import io.spine.test.projection.ProjectId
import io.spine.testing.server.model.ModelTests
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Proves the state history feature — pulled up to `AbstractEntityRepository` and
 * `AbstractEntity` — works for a non-aggregate repository, here a
 * [ProjectionRepository], with no projection-specific code.
 *
 * The behavioral contract itself (fail-fast, per-dispatch append, "state at a time",
 * retention) is exercised in depth by the aggregate suite over the same inherited
 * machinery; this suite verifies the generalization holds for projections, including
 * the bulk store path.
 */
@DisplayName("State history of a `ProjectionRepository` should")
internal class ProjectionStateHistorySpec {

    private lateinit var context: BoundedContext
    private lateinit var repository: StateHistoryProjectionRepository

    @BeforeEach
    fun setUp() {
        ModelTests.dropAllModels()
        context = BoundedContextBuilder.assumingTests().build()
        repository = StateHistoryProjectionRepository()
        context.internalAccess()
            .register(repository)
    }

    @AfterEach
    fun tearDown() {
        repository.close()
        context.close()
        ModelTests.dropAllModels()
    }

    @Test
    fun `keep the recording disabled by default`() {
        repository.historyEnabled() shouldBe false
    }

    @Test
    fun `fail fast when the history is read while disabled`() {
        val exception = shouldThrow<IllegalStateException> {
            repository.history()
        }
        exception.message shouldContain "recordStateHistory"
    }

    @Test
    fun `record a state history entry when a projection is stored`() {
        repository.enableStateHistory()
        val id = projectId()

        repository.store(repository.create(id))

        historyRecords(id) shouldHaveSize 1
    }

    @Test
    fun `record a state history entry for every projection stored in bulk`() {
        repository.enableStateHistory()
        val first = projectId()
        val second = projectId()

        repository.store(listOf(repository.create(first), repository.create(second)))

        historyRecords(first) shouldHaveSize 1
        historyRecords(second) shouldHaveSize 1
    }

    @Test
    fun `close the state history storage when the repository is closed`() {
        repository.enableStateHistory()
        val history = repository.history()

        repository.close()

        history.isOpen shouldBe false
    }

    private fun historyRecords(id: ProjectId): List<EntityRecord> =
        repository.history()
            .historyBackward(id, Int.MAX_VALUE)
            .asSequence()
            .toList()

    private fun projectId(): ProjectId =
        ProjectId.newBuilder()
            .setId(newUuid())
            .build()
}

/**
 * A repository fixture opening the `protected` state history configuration of
 * `AbstractEntityRepository` — inherited by every entity repository — to Kotlin tests,
 * which cannot reach a `protected` member from outside the declaring package.
 */
internal class StateHistoryProjectionRepository : TestProjection.Repository() {

    fun enableStateHistory() = recordStateHistory()

    fun historyEnabled(): Boolean = stateHistoryEnabled()

    fun history(): EntityStateHistoryStorage<ProjectId> = stateHistory()
}
