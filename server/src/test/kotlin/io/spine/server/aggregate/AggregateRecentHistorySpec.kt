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

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.spine.base.Time.currentTime
import io.spine.core.Command
import io.spine.core.CommandValidationError.DUPLICATE_COMMAND_VALUE
import io.spine.grpc.StreamObservers.noOpObserver
import io.spine.server.BoundedContext
import io.spine.server.BoundedContextBuilder
import io.spine.server.aggregate.given.Given
import io.spine.server.aggregate.given.history.HistoryReadingAggregate
import io.spine.server.aggregate.given.history.StateHistoryTestRepository
import io.spine.server.type.CommandEnvelope
import io.spine.test.aggregate.ProjectId
import io.spine.test.aggregate.event.AggProjectCreated
import io.spine.test.aggregate.event.AggTaskAdded
import io.spine.testing.logging.mute.MuteLogging
import io.spine.testing.server.model.ModelTests
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Verifies that the events and states an aggregate produces enter its
 * recent history right away, before the durable writes.
 *
 * See `AggregateRepositoryStateHistorySpec` for the durable side of
 * the state history.
 */
@DisplayName("The recent history of an `Aggregate` should")
internal class AggregateRecentHistorySpec {

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
        HistoryReadingAggregate.statesSeenOnStart = emptyList()
        HistoryReadingAggregate.eventsSeenOnStart = emptyList()
    }

    @AfterEach
    fun tearDown() {
        repository.close()
        context.close()
        ModelTests.dropAllModels()
    }

    @Test
    fun `serve the events a dispatch produced to the same instance right away`() {
        val aggregate = repository.create(projectId)

        dispatch(aggregate, Given.ACommand.createProject(projectId))

        // The journal write has not happened — `store()` was never called —
        // yet the produced event is already readable on this instance.
        val events = aggregate.readEventsBackward(10)
            .asSequence()
            .toList()
        events shouldHaveSize 1
        events.first()
            .enclosedMessage()
            .shouldBeInstanceOf<AggProjectCreated>()
    }

    @Test
    fun `hide the events of a dispatch from its own receptor`() {
        // The `AggStartProject` receptor also reads the state history.
        repository.enableStateHistory()
        val aggregate = repository.create(projectId)
        dispatch(aggregate, Given.ACommand.createProject(projectId))

        dispatch(aggregate, Given.ACommand.startProject(projectId))

        // The `AggStartProject` receptor saw the event of the earlier
        // dispatch, but not its own `AggProjectStarted`.
        val seen = HistoryReadingAggregate.eventsSeenOnStart
        seen shouldHaveSize 1
        seen.first()
            .enclosedMessage()
            .shouldBeInstanceOf<AggProjectCreated>()
    }

    @Test
    fun `let a receptor see the events of the earlier dispatches of a batch`() {
        repository.enableStateHistory()
        repository.beginBatch(projectId)

        post(Given.ACommand.createProject(projectId))
        post(Given.ACommand.addTask(projectId))
        post(Given.ACommand.startProject(projectId))

        // Mid-batch, the journal writes are deferred, yet the receptor of
        // the third command saw the events of the first two, newest first.
        val seen = HistoryReadingAggregate.eventsSeenOnStart
        seen shouldHaveSize 2
        seen[0].enclosedMessage().shouldBeInstanceOf<AggTaskAdded>()
        seen[1].enclosedMessage().shouldBeInstanceOf<AggProjectCreated>()

        repository.endBatch(projectId)

        // The batch flush journals all the produced events.
        repository.journal()
            .historyBackward(projectId, 10)
            .asSequence()
            .toList() shouldHaveSize 3
    }

    @Test
    @MuteLogging
    fun `let the idempotency guard catch a duplicate within one batch`() {
        repository.enableGuard()
        val aggregate = repository.create(projectId)
        val command = Given.ACommand.createProject(projectId)

        val first = dispatch(aggregate, command)
        val second = dispatch(aggregate, command)

        first.hasError() shouldBe false
        // The duplicate is caught before the journal write: the produced
        // event reached only the recent history of this instance.
        second.hasError() shouldBe true
        second.error.code shouldBe DUPLICATE_COMMAND_VALUE
    }

    @Test
    fun `serve stored states from the instance cache`() {
        repository.enableStateHistory()
        val aggregate = repository.create(projectId)
        dispatch(aggregate, Given.ACommand.createProject(projectId))
        repository.store(aggregate)

        repository.history()
            .truncate(olderThan = currentTime())

        // The held instance serves the state from its cache even though
        // the durable records are gone...
        val states = aggregate.readStatesBackward(1)
            .asSequence()
            .toList()
        states shouldHaveSize 1
        states.first().id shouldBe projectId

        // ...while a freshly loaded instance sees the truncated storage.
        repository.loadAggregate(projectId)
            .readStatesBackward(1)
            .asSequence()
            .toList()
            .shouldBeEmpty()
    }

    private fun dispatch(aggregate: HistoryReadingAggregate, command: Command) =
        AggregateTestSupport.dispatchCommand(
            repository = repository,
            aggregate = aggregate,
            command = CommandEnvelope.of(command)
        )

    private fun post(command: Command) {
        context.commandBus()
            .post(command, noOpObserver())
    }
}
