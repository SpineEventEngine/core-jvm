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

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.spine.server.BoundedContext
import io.spine.server.BoundedContextBuilder
import io.spine.server.aggregate.given.Given.ACommand.addTask
import io.spine.server.aggregate.given.Given.ACommand.createProject
import io.spine.server.aggregate.given.aggregate.FaultyAggregate
import io.spine.server.aggregate.given.dispatch.AggregateMessageDispatcher.dispatchCommand
import io.spine.server.type.EventClass
import io.spine.test.aggregate.ProjectId
import io.spine.test.aggregate.event.AggProjectCreated
import io.spine.testing.logging.mute.MuteLogging
import io.spine.testing.server.model.ModelTests
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`Aggregate` should")
internal class AggregateUncommittedEventsSpec {

    private lateinit var context: BoundedContext

    @BeforeEach
    fun setUp() {
        ModelTests.dropAllModels()
        context = BoundedContextBuilder.assumingTests(true).build()
    }

    @AfterEach
    fun tearDown() {
        context.close()
    }

    @Test
    @MuteLogging
    fun `not record an event that fails to apply as uncommitted`() {
        val aggregate: Aggregate<*, *, *> =
            FaultyAggregate(ID, /* brokenHandler = */ false, /* brokenApplier = */ true)

        val outcome = dispatchCommand(aggregate, createProject(ID))

        outcome.hasError() shouldBe true
        aggregate.hasUncommittedEvents() shouldBe false
    }

    @Test
    @MuteLogging
    fun `keep already recorded events intact when a later event fails to apply`() {
        val aggregate: Aggregate<*, *, *> =
            FaultyAggregate(ID, /* brokenHandler = */ false, /* brokenApplier = */ false)

        // The first command is applied successfully and recorded as uncommitted.
        val applied = dispatchCommand(aggregate, createProject(ID))
        applied.hasError() shouldBe false

        // The applier of the second command fails, so its event must not be recorded.
        val failed = dispatchCommand(aggregate, addTask(ID))
        failed.hasError() shouldBe true

        val recorded = aggregate.uncommittedEvents.list()
            .map { EventClass.from(it) }
        recorded shouldContainExactly listOf(EventClass.from(AggProjectCreated::class.java))
    }

    private companion object {
        val ID: ProjectId = ProjectId.newBuilder()
            .setUuid("prj-uncommitted-01")
            .build()
    }
}
