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
