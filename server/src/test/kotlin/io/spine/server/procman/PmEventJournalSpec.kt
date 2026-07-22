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

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.spine.core.Command
import io.spine.core.Event
import io.spine.grpc.StreamObservers.noOpObserver
import io.spine.server.BoundedContext
import io.spine.server.BoundedContextBuilder
import io.spine.server.procman.given.journal.JournalTestEnv.addTask
import io.spine.server.procman.given.journal.JournalTestEnv.createProject
import io.spine.server.procman.given.journal.JournalTestEnv.newProjectId
import io.spine.server.procman.given.journal.JournalTestEnv.reviewBacklog
import io.spine.server.procman.given.journal.JournalTestEnv.throwEntityAlreadyArchived
import io.spine.server.procman.given.journal.JournalTestPmRepo
import io.spine.test.procman.ProjectId
import io.spine.test.procman.event.PmProjectCreated
import io.spine.test.procman.event.PmTaskAdded
import io.spine.testing.server.model.ModelTests
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("`ProcessManagerRepository` event journal should")
internal class PmEventJournalSpec {

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
        val repo = JournalTestPmRepo(journaling = journaling)
        context.internalAccess().register(repo)
        return repo
    }

    private fun post(command: Command) = context.commandBus().post(command, noOpObserver())

    private fun journalOf(repo: JournalTestPmRepo): List<Event> =
        repo.journal()
            .historyBackward(id, DEPTH)
            .asSequence()
            .toList()

    @Test
    fun `journal the events emitted by successful dispatches, newest first`() {
        val repo = register(journaling = true)

        post(createProject(id))
        post(addTask(id))

        val journaled = journalOf(repo)
        journaled shouldHaveSize 2
        journaled[0].enclosedMessage().shouldBeInstanceOf<PmTaskAdded>()
        journaled[1].enclosedMessage().shouldBeInstanceOf<PmProjectCreated>()
    }

    @Test
    fun `journal an event-emitting dispatch which leaves the state unchanged`() {
        val repo = register(journaling = true)

        post(addTask(id))

        val journaled = journalOf(repo)
        journaled shouldHaveSize 1
        journaled[0].enclosedMessage().shouldBeInstanceOf<PmTaskAdded>()
    }

    @Test
    fun `journal nothing while the journaling is not enabled`() {
        val repo = register(journaling = false)

        post(createProject(id))

        journalOf(repo).shouldBeEmpty()
    }

    @Test
    fun `not journal rejections`() {
        val repo = register(journaling = true)

        post(throwEntityAlreadyArchived(id))

        journalOf(repo).shouldBeEmpty()
    }

    @Test
    fun `not journal the commands emitted by a commander`() {
        val repo = register(journaling = true)

        post(reviewBacklog(id))

        val journaled = journalOf(repo)
        journaled shouldHaveSize 1
        journaled[0].enclosedMessage().shouldBeInstanceOf<PmTaskAdded>()
    }

    @Test
    fun `close the journal when the repository is closed`() {
        val repo = register(journaling = true)
        val journal = repo.journal()

        context.close()

        journal.isOpen shouldBe false
        // Re-create the context so that `tearDown()` closes a live one.
        context = BoundedContextBuilder.assumingTests().build()
    }

    private companion object {

        /** The read window wide enough for every case of this spec. */
        private const val DEPTH = 10
    }
}
