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

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.spine.core.Command
import io.spine.core.CommandValidationError
import io.spine.core.Event
import io.spine.core.EventValidationError
import io.spine.grpc.StreamObservers.noOpObserver
import io.spine.server.BoundedContext
import io.spine.server.BoundedContextBuilder
import io.spine.server.procman.given.journal.JournalTestEnv.createProject
import io.spine.server.procman.given.journal.JournalTestEnv.newProjectId
import io.spine.server.procman.given.journal.JournalTestEnv.ownerChanged
import io.spine.server.procman.given.journal.JournalTestEnv.startProject
import io.spine.server.procman.given.journal.JournalTestPmRepo
import io.spine.server.procman.given.journal.JournalTestProcman
import io.spine.server.type.CommandEnvelope
import io.spine.server.type.EventEnvelope
import io.spine.test.procman.ProjectId
import io.spine.testing.server.model.ModelTests
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("`ProcessManager` double-dispatch guard should")
internal class PmDoubleDispatchGuardSpec {

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

    private fun register(journaling: Boolean = true, guard: Boolean = true): JournalTestPmRepo {
        val repo = JournalTestPmRepo(journaling = journaling, guard = guard)
        context.internalAccess().register(repo)
        return repo
    }

    private fun post(command: Command) = context.commandBus().post(command, noOpObserver())

    private fun post(event: Event) = context.eventBus().post(event)

    /**
     * Loads a fresh instance whose in-memory history is empty, so that
     * the duplicate detection below reads through the durable journal.
     */
    private fun freshInstance(repo: JournalTestPmRepo): JournalTestProcman =
        repo.find(id).orElseThrow()

    @Test
    fun `reject a command already dispatched to the entity`() {
        val repo = register()
        val command = createProject(id)
        post(command)

        val duplicate = freshInstance(repo).checkDuplicate(CommandEnvelope.of(command))

        duplicate.shouldNotBeNull()
        duplicate.error.type shouldBe CommandValidationError::class.java.simpleName
        duplicate.error.code shouldBe CommandValidationError.DUPLICATE_COMMAND_VALUE
    }

    @Test
    fun `reject an event already dispatched to the entity`() {
        val repo = register()
        val event = ownerChanged(id)
        post(event)

        val duplicate = freshInstance(repo).checkDuplicate(EventEnvelope.of(event))

        duplicate.shouldNotBeNull()
        duplicate.error.type shouldBe EventValidationError::class.java.simpleName
        duplicate.error.code shouldBe EventValidationError.DUPLICATE_EVENT_VALUE
    }

    @Test
    fun `permit a command outside the scanned window`() {
        val repo = register()
        repo.depth(1)
        val command = createProject(id)
        post(command)
        // Push the created event out of the depth-1 window with a newer dispatch.
        post(startProject(id))

        freshInstance(repo).checkDuplicate(CommandEnvelope.of(command)).shouldBeNull()
    }

    @Test
    fun `permit a command which was not dispatched`() {
        val repo = register()

        val instance = repo.create(id)

        instance.checkDuplicate(CommandEnvelope.of(createProject(id))).shouldBeNull()
    }

    @Test
    fun `permit a command when another command was dispatched`() {
        val repo = register()
        post(createProject(id))

        freshInstance(repo)
            .checkDuplicate(CommandEnvelope.of(startProject(id)))
            .shouldBeNull()
    }

    @Test
    fun `permit duplicates while the guard is off`() {
        val repo = register(guard = false)
        val command = createProject(id)

        post(command)

        // The delivery layer drops a re-posted identical command before it reaches the
        // entity, so the guard-off posture is asserted directly: the dispatched command
        // is present in the journaled history, yet the disabled guard does not flag it.
        val journaled = repo.journal()
            .historyBackward(id, DEPTH)
            .asSequence()
            .toList()
        journaled shouldHaveSize 1
        freshInstance(repo).checkDuplicate(CommandEnvelope.of(command)).shouldBeNull()
    }

    @Nested inner class
    `require the event journal and` {

        @Test
        fun `fail to register with the guard enabled and the journaling off`() {
            val repo = JournalTestPmRepo(journaling = false, guard = true)

            val failure = shouldThrow<IllegalStateException> {
                context.internalAccess().register(repo)
            }

            failure.message.shouldNotBeNull() shouldContain "double-dispatch guard"
        }

        @Test
        fun `register with both the guard and the journaling enabled`() {
            shouldNotThrowAny {
                register(journaling = true, guard = true)
            }
        }
    }

    private companion object {

        /** The journal read window wide enough for every case of this spec. */
        private const val DEPTH = 10
    }
}
