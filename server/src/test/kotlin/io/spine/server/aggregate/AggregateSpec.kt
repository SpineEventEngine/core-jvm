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

import com.google.common.truth.extensions.proto.ProtoTruth.assertThat
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.spine.base.Error
import io.spine.base.Time
import io.spine.core.Ack
import io.spine.core.Event
import io.spine.core.TenantId
import io.spine.grpc.StreamObservers.noOpObserver
import io.spine.server.BoundedContext
import io.spine.server.BoundedContextBuilder
import io.spine.server.aggregate.given.Given
import io.spine.server.aggregate.given.aggregate.AggregateTestEnv.assignTask
import io.spine.server.aggregate.given.aggregate.AggregateTestEnv.command
import io.spine.server.aggregate.given.aggregate.AggregateTestEnv.createTask
import io.spine.server.aggregate.given.aggregate.AggregateTestEnv.env
import io.spine.server.aggregate.given.aggregate.AggregateTestEnv.event
import io.spine.server.aggregate.given.aggregate.AggregateTestEnv.newTenantId
import io.spine.server.aggregate.given.aggregate.AggregateTestEnv.reassignTask
import io.spine.server.aggregate.given.aggregate.AggregateWithMissingApplier
import io.spine.server.aggregate.given.aggregate.AmishAggregate
import io.spine.server.aggregate.given.aggregate.FaultyAggregate
import io.spine.server.aggregate.given.aggregate.IntAggregate
import io.spine.server.aggregate.given.aggregate.TaskAggregateRepository
import io.spine.server.aggregate.given.aggregate.TestAggregate
import io.spine.server.aggregate.given.aggregate.TestAggregateRepository
import io.spine.server.aggregate.given.dispatch.AggregateMessageDispatcher.dispatchCommand
import io.spine.server.aggregate.given.thermometer.SafeThermometer
import io.spine.server.aggregate.given.thermometer.SafeThermometerRepo
import io.spine.server.aggregate.given.thermometer.ThermometerId
import io.spine.server.aggregate.given.thermometer.event.TemperatureChanged
import io.spine.server.aggregate.given.thermometer.thermometer
import io.spine.server.aggregate.model.AggregateClass.asAggregateClass
import io.spine.server.delivery.MessageEndpoint
import io.spine.server.entity.SignalDispatchingEntity.Companion.DEFAULT_HISTORY_DEPTH
import io.spine.server.entity.UncommittedEvents
import io.spine.server.tenant.TenantAwareRunner.with
import io.spine.server.type.CommandEnvelope
import io.spine.server.type.EventClass
import io.spine.server.type.EventEnvelope
import io.spine.system.server.DiagnosticMonitor
import io.spine.test.aggregate.ProjectId
import io.spine.test.aggregate.Status
import io.spine.test.aggregate.command.AggAddTask
import io.spine.test.aggregate.command.AggCreateProject
import io.spine.test.aggregate.command.AggStartProject
import io.spine.test.aggregate.event.AggProjectCreated
import io.spine.test.aggregate.event.AggProjectStarted
import io.spine.test.aggregate.event.AggTaskAdded
import io.spine.test.aggregate.event.AggTaskAssigned
import io.spine.test.aggregate.event.AggTaskCreated
import io.spine.test.aggregate.event.AggUserNotified
import io.spine.test.aggregate.event.aggProjectDeleted
import io.spine.test.aggregate.projectId
import io.spine.test.aggregate.rejection.Rejections.AggCannotReassignUnassignedTask
import io.spine.testing.logging.mute.MuteLogging
import io.spine.testing.server.Assertions.assertCommandClasses
import io.spine.testing.server.Assertions.assertEventClasses
import io.spine.testing.server.blackbox.ContextAwareTest
import io.spine.testing.server.model.ModelTests
import io.spine.testing.time.BackToTheFuture
import io.spine.testing.time.FrozenMadHatterParty
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("`Aggregate` should")
internal class AggregateSpec {

    private lateinit var aggregate: TestAggregate
    private lateinit var amishAggregate: AmishAggregate
    private lateinit var context: BoundedContext
    private lateinit var repository: TestAggregateRepository

    @BeforeEach
    fun setUp() {
        ModelTests.dropAllModels()
        aggregate = TestAggregate(ID)
        amishAggregate = AmishAggregate(ID)
        context = BoundedContextBuilder.assumingTests(true).build()
        repository = TestAggregateRepository()
        context.internalAccess()
            .register(repository)
    }

    @AfterEach
    fun tearDown() {
        context.close()
    }

    @Nested inner class
    Expose {

        @Test
        fun `handled command classes`() {
            val commandClasses = asAggregateClass(TestAggregate::class.java).commands()

            commandClasses shouldHaveSize 3

            assertCommandClasses(
                commandClasses,
                AggCreateProject::class.java,
                AggAddTask::class.java,
                AggStartProject::class.java
            )
        }

        @Test
        fun `current state`() {
            dispatchCommand(aggregate, command(createProject))
            aggregate.state().status shouldBe Status.CREATED

            dispatchCommand(aggregate, command(startProject))
            aggregate.state().status shouldBe Status.STARTED
        }

        @Test
        fun `non-null last modification time`() {
            val creationTime = TestAggregate(ID).whenModified()
            creationTime.shouldNotBeNull()
        }
    }

    @Test
    fun `handle one command and apply appropriate event`() {
        dispatchCommand(aggregate, command(createProject))

        aggregate.createProjectCommandHandled shouldBe true
        aggregate.projectCreatedEventApplied shouldBe true
    }

    @Nested inner class
    `advance version` {

        @Test
        fun `by one upon handling command with one event`() {
            val version = aggregate.versionNumber()

            dispatchCommand(aggregate, command(createProject))

            aggregate.versionNumber() shouldBe version + 1
        }

        /**
         * This is the most typical use-case with a single event returned in response
         * to a command.
         */
        @Test
        fun `by one upon handling command with single event and empty event applier`() {
            val version = amishAggregate.versionNumber()

            val command = command(pauseProject)
            val messages = dispatchCommand(amishAggregate, command)
                .success
                .producedEvents
                .eventList
            messages shouldHaveSize 1

            amishAggregate.versionNumber() shouldBe version + 1
        }

        /**
         * This tests a use-case implying returning a `List` of events in response to a command.
         */
        @Test
        fun `by one upon handling a command that emits several events`() {
            val version = amishAggregate.versionNumber()

            val command = command(cancelProject)
            val eventMessages = dispatchCommand(amishAggregate, command)
                .success
                .producedEvents
                .eventList
            // Expecting more than one event to differ from the single-event scenarios; the
            // aggregate version still advances by exactly one per command (ADR D3).
            (eventMessages.size > 1) shouldBe true

            amishAggregate.versionNumber() shouldBe version + 1
        }

        @Test
        fun `by number of commands upon handling several commands`() {
            val version = aggregate.versionNumber()

            dispatchCommand(aggregate, command(createProject))
            dispatchCommand(aggregate, command(startProject))
            dispatchCommand(aggregate, command(addTask))

            aggregate.versionNumber() shouldBe version + 3
        }
    }

    @Test
    fun `write its pre-dispatch version into the event context`() {
        val versionBeforeDispatch = aggregate.version()

        dispatchCommand(aggregate, command(createProject))

        // Get the first event since the command assignee produces only one event message.
        val uncommittedEvents = aggregate.uncommittedEvents().list()
        val event = uncommittedEvents[0]
        val context = event.context()
        // Since the event-sourcing cutover, an emitted event carries the aggregate's
        // pre-dispatch version (ADR D3); the aggregate itself advances by one per command.
        context.version shouldBe versionBeforeDispatch
    }

    @Test
    fun `handle only dispatched commands`() {
        dispatchCommand(aggregate, command(createProject))

        aggregate.createProjectCommandHandled shouldBe true
        aggregate.projectCreatedEventApplied shouldBe true

        aggregate.addTaskCommandHandled shouldBe false
        aggregate.taskAddedEventApplied shouldBe false

        aggregate.startProjectCommandHandled shouldBe false
        aggregate.projectStartedEventApplied shouldBe false
    }

    @Test
    fun `invoke event applier after command assignee`() {
        dispatchCommand(aggregate, command(createProject))
        aggregate.createProjectCommandHandled shouldBe true
        aggregate.projectCreatedEventApplied shouldBe true

        dispatchCommand(aggregate, command(addTask))
        aggregate.addTaskCommandHandled shouldBe true
        aggregate.taskAddedEventApplied shouldBe true

        dispatchCommand(aggregate, command(startProject))
        aggregate.startProjectCommandHandled shouldBe true
        aggregate.projectStartedEventApplied shouldBe true
    }

    @Nested inner class
    `report an error` {

        @Test
        @MuteLogging
        fun `when dispatched a command it cannot handle`() {
            ModelTests.dropAllModels()
            val aggregate = AggregateWithMissingApplier(ID)

            // The aggregate has no receptor for this command. Since the event-sourcing cutover
            // the aggregate is dispatched exactly like a `ProcessManager`: the receptor lookup
            // runs inside the transaction, so the resulting `ModelError` is caught by the
            // transaction failsafe and surfaced as an error outcome rather than being thrown.
            val outcome = dispatchCommand(aggregate, command(addTask))

            outcome.hasError() shouldBe true
        }
    }

    @Nested inner class
    `have state` {

        @Test
        fun `updated when command is handled`() {
            dispatchCommand(aggregate, command(createProject))

            val state = aggregate.state()

            state.id shouldBe ID
            state.status shouldBe Status.CREATED
        }
    }

    @Test
    fun `record modification time when command is handled`() {
        try {
            val frozenTime = Time.currentTime()
            Time.setProvider(FrozenMadHatterParty(frozenTime))

            dispatchCommand(aggregate, command(createProject))

            aggregate.whenModified() shouldBe frozenTime
        } finally {
            Time.resetProvider()
        }
    }

    @Nested inner class
    `after dispatch, return event records` {

        @Test
        fun `which are uncommitted`() {
            aggregate.dispatchCommands(
                command(createProject),
                command(addTask),
                command(startProject)
            )

            val events = aggregate.uncommittedEvents().list()

            assertEventClasses(
                events.map { EventClass.of(it) },
                AggProjectCreated::class.java,
                AggTaskAdded::class.java,
                AggProjectStarted::class.java
            )
        }
    }

    @Nested inner class
    `by default, not have any event records` {

        @Test
        fun `which are uncommitted`() {
            val events = aggregate.uncommittedEvents()

            events.nonEmpty() shouldBe false
        }

        @Test
        fun `which are being committed`() {
            aggregate.commitEvents()
            aggregate.readEventsBackward(DEFAULT_HISTORY_DEPTH).hasNext() shouldBe false
        }
    }

    @Test
    fun `clear event records when commit after dispatch`() {
        aggregate.dispatchCommands(
            command(createProject),
            command(addTask),
            command(startProject)
        )
        aggregate.uncommittedEvents().nonEmpty() shouldBe true
        aggregate.commitEvents()
        aggregate.uncommittedEvents().nonEmpty() shouldBe false
    }

    // Since the removal of `Aggregate.restore(EntityRecord)` (2026-07-16), an aggregate is
    // reconstructed from its latest state record by the standard `StorageConverter` path of the
    // record-based repository — the same way as a Process Manager. The record round-trip is
    // covered by `AggregateRepositoryTest` ("restoring the stored state"). The former tolerance
    // of a pre-cutover stateless record (a `NONE`-visibility aggregate persisting only its ID,
    // version, and lifecycle flags) is retired with the method: such records are a documented
    // hard break (see `docs/migration/aggregates-without-event-sourcing.md`, §8), and loading
    // one fails loudly instead of silently resurrecting the aggregate with its default state.

    @Test
    fun `increment version upon state changing event applied`() {
        val version = aggregate.version().number
        // Dispatch two commands that cause events that modify aggregate state.
        aggregate.dispatchCommands(command(createProject), command(startProject))

        aggregate.version().number shouldBe version + 2
    }

    @Test
    fun `record modification timestamp`() {
        try {
            val provider = BackToTheFuture()
            Time.setProvider(provider)

            var currentTime = Time.currentTime()

            aggregate.dispatchCommands(command(createProject))

            aggregate.whenModified() shouldBe currentTime

            currentTime = provider.forward(10)

            aggregate.dispatchCommands(command(startProject))

            aggregate.whenModified() shouldBe currentTime
        } finally {
            Time.resetProvider()
        }
    }

    @Nested inner class
    `catch runtime exceptions in` {

        @Test
        @MuteLogging
        fun handlers() {
            ModelTests.dropAllModels()

            val faultyAggregate = FaultyAggregate(ID, true, false)

            val command = Given.ACommand.createProject()
            val outcome = dispatchCommand(faultyAggregate, env(command))
            outcome.hasError() shouldBe true
            assertThat(outcome.error)
                .comparingExpectedFieldsOnly()
                .isEqualTo(
                    Error.newBuilder()
                        .setType(IllegalStateException::class.java.canonicalName)
                        .setMessage(FaultyAggregate.BROKEN_HANDLER)
                        .buildPartial()
                )
        }

        @Test
        @MuteLogging
        fun appliers() {
            ModelTests.dropAllModels()
            val faultyAggregate = FaultyAggregate(ID, false, true)

            val command = Given.ACommand.createProject()
            val outcome = dispatchCommand(faultyAggregate, env(command))

            outcome.hasError() shouldBe true
            assertThat(outcome.error)
                .comparingExpectedFieldsOnly()
                .isEqualTo(
                    Error.newBuilder()
                        .setType(IllegalStateException::class.java.canonicalName)
                        .setMessage(FaultyAggregate.BROKEN_APPLIER)
                        .buildPartial()
                )
        }
    }

    @Test
    fun `not allow getting state builder from outside event applier`() {
        shouldThrow<IllegalStateException> { IntAggregate(100).builder() }
    }

    @Nested inner class
    `traverse history` {

        private lateinit var history: Iterator<Event>

        @Test
        fun `iterating through newest events first`() {
            val tenantId = newTenantId()
            val createCommand = command(createProject, tenantId)
            val startCommand = command(startProject, tenantId)
            val addTaskCommand = command(addTask, tenantId)
            val addTaskCommand2 = command(addTask, tenantId)

            val commandBus = context.commandBus()
            val noOpObserver = noOpObserver<Ack>()
            commandBus.post(createCommand, noOpObserver)
            commandBus.post(addTaskCommand, noOpObserver)
            commandBus.post(listOf(addTaskCommand2, startCommand), noOpObserver)

            val aggregate = repository.loadAggregate(tenantId, ID)
            // Since the cutover, recent history is read lazily from storage on demand rather
            // than replayed eagerly at load. Reading it therefore requires the tenant context
            // (the read is eager, so the returned iterator is safe to consume outside
            // the context).
            history = with(tenantId).evaluate {
                aggregate.readEventsBackward(DEFAULT_HISTORY_DEPTH)
            }

            nextCommandId() shouldBe startCommand.id()
            nextCommandId() shouldBe addTaskCommand2.id()
            nextCommandId() shouldBe addTaskCommand.id()
            nextCommandId() shouldBe createCommand.id()

            history.hasNext() shouldBe false
        }

        private fun nextCommandId() = history.next()
            .rootMessage()
            .asCommandId()
    }

    @Test
    @MuteLogging
    fun `throw 'DuplicateCommandException' for a duplicated command`() {
        val monitor = DiagnosticMonitor()
        context.internalAccess()
            .registerEventDispatcher(monitor)

        val tenantId = newTenantId()
        val createCommand = command(createProject, tenantId)
        val envelope = CommandEnvelope.of(createCommand)
        repository.dispatchCommand(envelope)
        repository.dispatchCommand(envelope)
        val duplicateCommandEvents = monitor.duplicateCommandEvents()
        duplicateCommandEvents shouldHaveSize 1
        val event = duplicateCommandEvents[0]
        event.duplicateCommand shouldBe envelope.messageId()
    }

    @Test
    fun `run 'DoubleDispatchGuard' when dispatching commands`() {
        val monitor = DiagnosticMonitor()
        context.internalAccess()
            .registerEventDispatcher(monitor)
        repository.enableGuard()
        val createCommand = command(createProject)
        val cmd = CommandEnvelope.of(createCommand)
        val tenantId = newTenantId()
        val endpoint = { AggregateCommandEndpoint(repository, cmd) }
        dispatch(tenantId, endpoint)
        dispatch(tenantId, endpoint)
        val events = monitor.duplicateCommandEvents()
        events shouldHaveSize 1
        val systemEvent = events[0]
        systemEvent.duplicateCommand shouldBe cmd.messageId()
    }

    @Test
    fun `run 'DoubleDispatchGuard' when dispatching events`() {
        val monitor = DiagnosticMonitor()
        context.internalAccess()
            .registerEventDispatcher(monitor)
        repository.enableGuard()
        val eventMessage = aggProjectDeleted { projectId = ID }
        val event = event(eventMessage, 2)
        val envelope = EventEnvelope.of(event)
        val endpoint = { AggregateEventReactionEndpoint(repository, envelope) }
        val tenantId = newTenantId()
        dispatch(tenantId, endpoint)
        dispatch(tenantId, endpoint)
        val events = monitor.duplicateEventEvents()
        events shouldHaveSize 1
        val systemEvent = events[0]
        systemEvent.duplicateEvent shouldBe envelope.messageId()
    }

    private fun dispatch(tenant: TenantId, endpoint: () -> MessageEndpoint<ProjectId, *>) {
        with(tenant).run {
            endpoint().dispatchTo(ID)
        }
    }

    /**
     * Obtains the events produced by this aggregate but not yet committed to its journal.
     */
    private fun Aggregate<*, *, *>.uncommittedEvents(): UncommittedEvents =
        uncommittedEventHistory().events()

    @Nested inner class
    `create a single event when emitting a pair without second value` : ContextAwareTest() {

        override fun contextBuilder(): BoundedContextBuilder =
            BoundedContextBuilder.assumingTests()
                .add(TaskAggregateRepository())

        /**
         * Ensures that a [pair][io.spine.server.tuple.Pair] with an empty second
         * optional value returned from a command assignee stores a single event.
         *
         * The command assignee that should return a pair is
         * `TaskAggregate.handle(AggAssignTask)`.
         */
        @Test
        fun `when dispatching a command`() {
            context().receivesCommand(createTask())
                .assertEvents()
                .withType(AggTaskCreated::class.java)
                .isNotEmpty()
        }

        /**
         * Ensures that a [pair][io.spine.server.tuple.Pair] with an empty second optional
         * value returned from a reaction on an event stores a single event.
         *
         * The first event is produced while handling a command by
         * `TaskAggregate.handle(AggAssignTask)`. Then as a reaction to this event a single
         * event should be fired as part of the pair by `TaskAggregate.on(AggTaskAssigned)`.
         */
        @Test
        fun `when reacting on an event`() {
            val assertEvents = context().receivesCommand(assignTask())
                .assertEvents()
            assertEvents.hasSize(2)
            assertEvents.withType(AggTaskAssigned::class.java)
                .hasSize(1)
            assertEvents.withType(AggUserNotified::class.java)
                .hasSize(1)
        }

        /**
         * Ensures that a [pair][io.spine.server.tuple.Pair] with an empty second optional
         * value returned from a reaction on a rejection stores a single event.
         *
         * The rejection is fired by `TaskAggregate.handle(AggReassignTask)`
         * and handled by `TaskAggregate.on(AggCannotReassignUnassignedTask)`.
         */
        @Test
        fun `when reacting on a rejection`() {
            val assertEvents = context().receivesCommand(reassignTask())
                .assertEvents()
            assertEvents.hasSize(2)
            assertEvents.withType(AggCannotReassignUnassignedTask::class.java)
                .hasSize(1)
            assertEvents.withType(AggUserNotified::class.java)
                .hasSize(1)
        }
    }

    @Nested inner class
    `allow having validation on the aggregate state and` : ContextAwareTest() {

        private val thermometerId = ThermometerId.generate()

        override fun contextBuilder(): BoundedContextBuilder =
            BoundedContextBuilder.assumingTests()
                .add(SafeThermometerRepo(thermometerId))

        @Test
        fun `not change the Aggregate state when there is no reaction on the event`() {
            val booksOnFire = TemperatureChanged.newBuilder()
                .setFahrenheit(451.0)
                .build()
            context().receivesExternalEvent(booksOnFire)
                .assertEntity(thermometerId, SafeThermometer::class.java)
                .doesNotExist()
        }

        @Test
        fun `save valid aggregate state on change`() {
            val gettingWarmer = TemperatureChanged.newBuilder()
                .setFahrenheit(72.0)
                .build()
            context().receivesExternalEvent(gettingWarmer)
            val expected = thermometer {
                id = thermometerId
                fahrenheit = 72.0
            }
            context().assertState(thermometerId, expected)
        }
    }

    private companion object {

        val ID: ProjectId = projectId { uuid = "prj-01" }

        val createProject: AggCreateProject = Given.CommandMessage.createProject(ID)
        val pauseProject = Given.CommandMessage.pauseProject(ID)
        val cancelProject = Given.CommandMessage.cancelProject(ID)
        val addTask: AggAddTask = Given.CommandMessage.addTask(ID)
        val startProject: AggStartProject = Given.CommandMessage.startProject(ID)
    }
}
