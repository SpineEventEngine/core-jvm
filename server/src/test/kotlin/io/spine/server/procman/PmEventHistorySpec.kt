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

package io.spine.server.procman

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.spine.core.Command
import io.spine.grpc.StreamObservers.noOpObserver
import io.spine.server.BoundedContext
import io.spine.server.BoundedContextBuilder
import io.spine.server.procman.given.journal.JournalTestEnv.addTask
import io.spine.server.procman.given.journal.JournalTestEnv.createProject
import io.spine.server.procman.given.journal.JournalTestEnv.newProjectId
import io.spine.server.procman.given.journal.JournalTestEnv.startProject
import io.spine.server.procman.given.journal.JournalTestPmRepo
import io.spine.server.procman.given.journal.JournalTestProcman
import io.spine.test.procman.ProjectId
import io.spine.test.procman.event.PmProjectCreated
import io.spine.test.procman.event.PmProjectStarted
import io.spine.test.procman.event.PmTaskAdded
import io.spine.testing.server.model.ModelTests
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`ProcessManager` event history should")
internal class PmEventHistorySpec {

    private lateinit var context: BoundedContext
    private lateinit var id: ProjectId

    @BeforeEach
    fun setUp() {
        ModelTests.dropAllModels()
        context = BoundedContextBuilder.assumingTests().build()
        id = newProjectId()
    }

    @AfterEach
    fun tearDown() {
        context.close()
        ModelTests.dropAllModels()
    }

    private fun register(journaling: Boolean): JournalTestPmRepo {
        val repo = JournalTestPmRepo(journaling)
        context.internalAccess().register(repo)
        return repo
    }

    private fun post(command: Command) = context.commandBus().post(command, noOpObserver())

    @Test
    fun `read up to the requested depth, newest first`() {
        val repo = register(journaling = true)
        post(createProject(id))
        post(addTask(id))
        post(startProject(id))
        val instance = repo.find(id).orElseThrow()

        val recent = instance.readEventsBackward(2)
            .asSequence()
            .toList()

        recent shouldHaveSize 2
        recent[0].enclosedMessage().shouldBeInstanceOf<PmProjectStarted>()
        recent[1].enclosedMessage().shouldBeInstanceOf<PmTaskAdded>()
    }

    @Test
    fun `tell if the recent events contain a match`() {
        val repo = register(journaling = true)
        post(createProject(id))
        post(addTask(id))
        post(startProject(id))
        val instance = repo.find(id).orElseThrow()

        instance.containsEvent(3) { it.enclosedMessage() is PmProjectCreated } shouldBe true
        instance.containsEvent(1) { it.enclosedMessage() is PmProjectCreated } shouldBe false
    }

    @Test
    fun `fail fast when the managing repository does not journal the events`() {
        val repo = register(journaling = false)
        post(createProject(id))
        val instance = repo.find(id).orElseThrow()

        val failure = shouldThrow<IllegalStateException> {
            instance.readEventsBackward(5).hasNext()
        }

        failure.message.shouldNotBeNull() shouldContain "does not journal"
    }

    @Test
    fun `serve empty history on an instance created outside a repository`() {
        val instance = JournalTestProcman()

        instance.readEventsBackward(5).hasNext() shouldBe false
    }
}
