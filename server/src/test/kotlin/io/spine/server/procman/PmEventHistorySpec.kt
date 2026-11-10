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
import io.spine.server.procman.given.journal.JournalTestEnv.completeProject
import io.spine.server.procman.given.journal.JournalTestEnv.createProject
import io.spine.server.procman.given.journal.JournalTestEnv.newProjectId
import io.spine.server.procman.given.journal.JournalTestEnv.startProject
import io.spine.server.procman.given.journal.JournalTestPmRepo
import io.spine.server.procman.given.journal.JournalTestProcman
import io.spine.system.server.DiagnosticMonitor
import io.spine.test.procman.ProjectId
import io.spine.test.procman.event.PmProjectCreated
import io.spine.test.procman.event.PmProjectStarted
import io.spine.test.procman.event.PmTaskAdded
import io.spine.testing.logging.mute.MuteLogging
import io.spine.testing.server.buildForTests
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
        context = BoundedContextBuilder.assumingTests().buildForTests()
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

    /**
     * Unlike the case above, which reads on a freshly loaded instance, this case reads
     * on the very instance which has just dispatched a signal within the same delivery
     * batch. The read must fail fast too: were the endpoint to record the produced
     * events on a non-journaling repository, they would serve this read from memory,
     * silently defeating the fail-fast contract.
     */
    @Test
    @MuteLogging
    fun `fail fast on the instance which just dispatched`() {
        val repo = register(journaling = false)
        val monitor = DiagnosticMonitor()
        context.internalAccess().registerEventDispatcher(monitor)

        repo.beginBatch(id)
        post(createProject(id))
        post(completeProject(id))
        repo.endBatch(id)

        val failures = monitor.handlerFailureEvents()
        failures shouldHaveSize 1
        val error = failures[0].error
        error.type shouldBe IllegalStateException::class.java.canonicalName
        error.message shouldContain "does not journal"
    }

    @Test
    fun `serve empty history on an instance created outside a repository`() {
        val instance = JournalTestProcman()

        instance.readEventsBackward(5).hasNext() shouldBe false
    }
}
