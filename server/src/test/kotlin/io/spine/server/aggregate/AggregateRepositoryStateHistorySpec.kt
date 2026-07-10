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

package io.spine.server.aggregate

import com.google.protobuf.util.Durations
import com.google.protobuf.util.Timestamps.subtract
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.spine.base.Identifier
import io.spine.base.Time.currentTime
import io.spine.core.Command
import io.spine.grpc.StreamObservers.noOpObserver
import io.spine.protobuf.AnyPacker
import io.spine.server.BoundedContext
import io.spine.server.BoundedContextBuilder
import io.spine.server.aggregate.given.Given
import io.spine.server.aggregate.given.history.StateHistoryTestRepository
import io.spine.server.entity.EntityRecord
import io.spine.test.aggregate.ProjectId
import io.spine.testing.server.model.ModelTests
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`AggregateRepository` should manage the state history")
internal class AggregateRepositoryStateHistorySpec {

    private lateinit var context: BoundedContext
    private lateinit var repository: StateHistoryTestRepository
    private lateinit var projectId: ProjectId

    @BeforeEach
    fun setUp() {
        ModelTests.dropAllModels()
        context = BoundedContextBuilder.assumingTests().build()
        repository = StateHistoryTestRepository()
        context.internalAccess()
            .register(repository)
        projectId = ProjectId.generate()
    }

    @AfterEach
    fun tearDown() {
        repository.close()
        context.close()
        ModelTests.dropAllModels()
    }

    @Test
    fun `keep the state history disabled by default`() {
        repository.historyEnabled() shouldBe false

        post(Given.ACommand.createProject(projectId))

        repository.loadAggregate(projectId)
            .version()
            .number shouldBe 1
    }

    @Test
    fun `fail fast when the history is read while disabled`() {
        val exception = shouldThrow<IllegalStateException> {
            repository.history()
        }
        exception.message shouldContain "recordStateHistory"
    }

    @Test
    fun `reject a non-positive history depth`() {
        shouldThrow<IllegalArgumentException> {
            repository.enableStateHistory(depth = 0)
        }
        shouldThrow<IllegalArgumentException> {
            repository.enableStateHistory(depth = -1)
        }
    }

    @Test
    fun `append a record per successful dispatch when enabled`() {
        repository.enableStateHistory(depth = 10)

        post(Given.ACommand.createProject(projectId))
        post(Given.ACommand.addTask(projectId))

        val records = historyRecords()
        records shouldHaveSize 2
        records.map { it.version.number } shouldContainExactly listOf(2, 1)
        records.forEach {
            it.entityId shouldBe Identifier.pack(projectId)
            it.version.hasTimestamp() shouldBe true
        }
        val currentState = repository.loadAggregate(projectId)
            .state()
        records[0].state shouldBe AnyPacker.pack(currentState)
    }

    @Test
    fun `answer the state at a time from the recorded history`() {
        repository.enableStateHistory(depth = 10)

        post(Given.ACommand.createProject(projectId))
        post(Given.ACommand.addTask(projectId))

        val history = repository.history()
        val records = historyRecords()
        val oldest = records.last()
        val beforeEntity = subtract(oldest.version.timestamp, Durations.fromSeconds(1))

        history.stateAt(projectId, beforeEntity) shouldBe null
        history.stateAt(projectId, currentTime()) shouldBe records.first()
    }

    @Test
    fun `close the state history storage when the repository is closed`() {
        repository.enableStateHistory(depth = 10)
        post(Given.ACommand.createProject(projectId))
        val history = repository.history()

        repository.close()

        history.isOpen shouldBe false
    }

    @Test
    fun `bound the history by the configured depth`() {
        repository.enableStateHistory(depth = 2)

        post(Given.ACommand.createProject(projectId))
        repeat(3) {
            post(Given.ACommand.addTask(projectId))
        }

        val records = historyRecords()
        records.map { it.version.number } shouldContainExactly listOf(4, 3)
    }

    private fun historyRecords(): List<EntityRecord> =
        repository.history()
            .historyBackward(projectId, Int.MAX_VALUE)
            .asSequence()
            .toList()

    private fun post(command: Command) {
        context.commandBus()
            .post(command, noOpObserver())
    }
}
