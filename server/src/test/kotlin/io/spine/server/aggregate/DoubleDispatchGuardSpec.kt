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

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.spine.base.Error
import io.spine.core.Command
import io.spine.core.CommandValidationError
import io.spine.core.CommandValidationError.DUPLICATE_COMMAND_VALUE
import io.spine.core.Event
import io.spine.core.EventValidationError
import io.spine.grpc.StreamObservers.noOpObserver
import io.spine.server.BoundedContext
import io.spine.server.BoundedContextBuilder
import io.spine.server.aggregate.given.DoubleDispatchGuardTestEnv.command
import io.spine.server.aggregate.given.DoubleDispatchGuardTestEnv.createProject
import io.spine.server.aggregate.given.DoubleDispatchGuardTestEnv.event
import io.spine.server.aggregate.given.DoubleDispatchGuardTestEnv.projectPaused
import io.spine.server.aggregate.given.DoubleDispatchGuardTestEnv.startProject
import io.spine.server.aggregate.given.DoubleDispatchGuardTestEnv.taskStarted
import io.spine.server.aggregate.given.aggregate.IgTestAggregate
import io.spine.server.aggregate.given.aggregate.IgTestAggregateRepository
import io.spine.server.entity.DoubleDispatchGuard
import io.spine.server.type.CommandEnvelope
import io.spine.server.type.EventEnvelope
import io.spine.test.aggregate.ProjectId
import io.spine.testing.server.model.ModelTests
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("`DoubleDispatchGuard` should")
internal class DoubleDispatchGuardSpec {

    private lateinit var context: BoundedContext
    private lateinit var repository: IgTestAggregateRepository
    private lateinit var projectId: ProjectId

    @BeforeEach
    fun setUp() {
        ModelTests.dropAllModels()
        context = BoundedContextBuilder.assumingTests().build()
        repository = IgTestAggregateRepository()
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

    @Nested inner class
    `check commands and` {

        @Test
        fun `report a duplicate if the command was handled since the last snapshot`() {
            val createCommand = command(createProject(projectId))
            post(createCommand)

            val guard = DoubleDispatchGuard(aggregate())
            val error = check(guard, createCommand)

            error.shouldNotBeNull()
            error.type shouldBe CommandValidationError::class.java.simpleName
            error.code shouldBe DUPLICATE_COMMAND_VALUE
        }

        @Test
        fun `accept a command older than the recent-history window`() {
            repository.setEventHistoryDepth(1)
            val createCommand = command(createProject(projectId))
            post(createCommand)
            // Push the create command's event out of the depth-1 window with a newer command.
            post(command(startProject(projectId)))

            val guard = DoubleDispatchGuard(aggregate())
            val error = check(guard, createCommand)

            error.shouldBeNull()
        }

        @Test
        fun `accept a command that was not handled`() {
            val createCommand = command(createProject(projectId))
            val aggregate = IgTestAggregate(projectId)

            val guard = DoubleDispatchGuard(aggregate)
            val error = guard.check(CommandEnvelope.of(createCommand))

            error.shouldBeNull()
        }

        @Test
        fun `accept a command when another command was handled`() {
            val createCommand = command(createProject(projectId))
            val startCommand = command(startProject(projectId))
            post(createCommand)

            val guard = DoubleDispatchGuard(aggregate())
            val error = check(guard, startCommand)

            error.shouldBeNull()
        }

        private fun aggregate(): IgTestAggregate = repository.loadAggregate(projectId)

        private fun post(command: Command) {
            context.commandBus()
                .post(command, noOpObserver())
        }

        private fun check(guard: DoubleDispatchGuard, command: Command): Error? {
            guard.enable(repository.eventHistoryDepth())
            val envelope = CommandEnvelope.of(command)
            return guard.check(envelope)
        }
    }

    @Nested inner class
    `check events and` {

        @BeforeEach
        fun postCreateProject() {
            context.commandBus()
                .post(command(createProject(projectId)), noOpObserver())
        }

        @Test
        fun `report a duplicate if the event was handled since the last snapshot`() {
            val taskEvent = event(taskStarted(projectId))
            post(taskEvent)

            val guard = DoubleDispatchGuard(repository.loadAggregate(projectId))
            val error = check(guard, taskEvent)

            error.shouldNotBeNull()
            error.type shouldBe EventValidationError::class.java.simpleName
        }

        @Test
        fun `accept an event older than the recent-history window`() {
            repository.setEventHistoryDepth(1)
            val taskEvent = event(taskStarted(projectId))
            post(taskEvent)
            // Push the reaction to the task event out of the depth-1 window with a newer event.
            post(event(projectPaused(projectId)))

            val guard = DoubleDispatchGuard(repository.loadAggregate(projectId))
            val error = check(guard, taskEvent)

            error.shouldBeNull()
        }

        @Test
        fun `accept an event that was not handled`() {
            val taskEvent = event(taskStarted(projectId))
            val aggregate = IgTestAggregate(projectId)

            val guard = DoubleDispatchGuard(aggregate)
            val error = check(guard, taskEvent)

            error.shouldBeNull()
        }

        @Test
        fun `accept an event when another event was handled`() {
            val taskEvent = event(taskStarted(projectId))
            val projectEvent = event(projectPaused(projectId))
            post(taskEvent)

            val guard = DoubleDispatchGuard(repository.loadAggregate(projectId))
            val error = check(guard, projectEvent)

            error.shouldBeNull()
        }

        private fun post(event: Event) {
            context.eventBus()
                .post(event)
        }

        private fun check(guard: DoubleDispatchGuard, event: Event): Error? {
            guard.enable(repository.eventHistoryDepth())
            val envelope = EventEnvelope.of(event)
            return guard.check(envelope)
        }
    }
}
