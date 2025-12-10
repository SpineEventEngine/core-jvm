/*
 * Copyright 2022, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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

package io.spine.server.aggregate;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.extensions.proto.ProtoSubject;
import com.google.protobuf.Message;
import io.grpc.stub.StreamObserver;
import io.spine.base.Error;
import io.spine.base.Identifier;
import io.spine.base.Time;
import io.spine.core.Ack;
import io.spine.core.Event;
import io.spine.core.MessageId;
import io.spine.core.TenantId;
import io.spine.server.BoundedContext;
import io.spine.server.BoundedContextBuilder;
import io.spine.server.aggregate.given.Given;
import io.spine.server.aggregate.given.aggregate.AggregateWithMissingApplier;
import io.spine.server.aggregate.given.aggregate.AmishAggregate;
import io.spine.server.aggregate.given.aggregate.FaultyAggregate;
import io.spine.server.aggregate.given.aggregate.IntAggregate;
import io.spine.server.aggregate.given.aggregate.TaskAggregate;
import io.spine.server.aggregate.given.aggregate.TaskAggregateRepository;
import io.spine.server.aggregate.given.aggregate.TestAggregate;
import io.spine.server.aggregate.given.aggregate.TestAggregateRepository;
import io.spine.server.aggregate.given.thermometer.SafeThermometer;
import io.spine.server.aggregate.given.thermometer.SafeThermometerRepo;
import io.spine.server.aggregate.given.thermometer.Thermometer;
import io.spine.server.aggregate.given.thermometer.ThermometerId;
import io.spine.server.aggregate.given.thermometer.event.TemperatureChanged;
import io.spine.server.delivery.MessageEndpoint;
import io.spine.server.model.ModelError;
import io.spine.server.type.CommandClass;
import io.spine.server.type.CommandEnvelope;
import io.spine.server.type.EventClass;
import io.spine.server.type.EventEnvelope;
import io.spine.system.server.DiagnosticMonitor;
import io.spine.test.aggregate.AggProject;
import io.spine.test.aggregate.ProjectId;
import io.spine.test.aggregate.Status;
import io.spine.test.aggregate.command.AggAddTask;
import io.spine.test.aggregate.command.AggAssignTask;
import io.spine.test.aggregate.command.AggCancelProject;
import io.spine.test.aggregate.command.AggCreateProject;
import io.spine.test.aggregate.command.AggPauseProject;
import io.spine.test.aggregate.command.AggReassignTask;
import io.spine.test.aggregate.command.AggStartProject;
import io.spine.test.aggregate.event.AggProjectCreated;
import io.spine.test.aggregate.event.AggProjectDeleted;
import io.spine.test.aggregate.event.AggProjectStarted;
import io.spine.test.aggregate.event.AggTaskAdded;
import io.spine.test.aggregate.event.AggTaskAssigned;
import io.spine.test.aggregate.event.AggTaskCreated;
import io.spine.test.aggregate.event.AggUserNotified;
import io.spine.test.aggregate.rejection.Rejections.AggCannotReassignUnassignedTask;
import io.spine.testing.logging.mute.MuteLogging;
import io.spine.testing.server.blackbox.ContextAwareTest;
import io.spine.testing.server.model.ModelTests;
import io.spine.testing.time.BackToTheFuture;
import io.spine.testing.time.FrozenMadHatterParty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static io.spine.grpc.StreamObservers.noOpObserver;
import static io.spine.protobuf.AnyPacker.unpack;
import static io.spine.server.aggregate.given.Given.ACommand.addTask;
import static io.spine.server.aggregate.given.Given.EventMessage.projectCreated;
import static io.spine.server.aggregate.given.Given.EventMessage.projectStarted;
import static io.spine.server.aggregate.given.Given.EventMessage.taskAdded;
import static io.spine.server.aggregate.given.aggregate.AggregateTestEnv.assignTask;
import static io.spine.server.aggregate.given.aggregate.AggregateTestEnv.command;
import static io.spine.server.aggregate.given.aggregate.AggregateTestEnv.createTask;
import static io.spine.server.aggregate.given.aggregate.AggregateTestEnv.env;
import static io.spine.server.aggregate.given.aggregate.AggregateTestEnv.event;
import static io.spine.server.aggregate.given.aggregate.AggregateTestEnv.newTenantId;
import static io.spine.server.aggregate.given.aggregate.AggregateTestEnv.reassignTask;
import static io.spine.server.aggregate.given.dispatch.AggregateMessageDispatcher.dispatchCommand;
import static io.spine.server.aggregate.model.AggregateClass.asAggregateClass;
import static io.spine.server.tenant.TenantAwareRunner.with;
import static io.spine.testing.server.Assertions.assertCommandClasses;
import static io.spine.testing.server.Assertions.assertEventClasses;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings({
        "InnerClassMayBeStatic", "ClassCanBeStatic" /* JUnit nested classes cannot be static. */,
})
@DisplayName("`Aggregate` should")
public class AggregateTest {

    private static final ProjectId ID = ProjectId.newBuilder()
                                                 .setUuid("prj-01")
                                                 .build();

    private static final AggCreateProject createProject = Given.CommandMessage.createProject(ID);
    private static final AggPauseProject pauseProject = Given.CommandMessage.pauseProject(ID);
    private static final AggCancelProject cancelProject = Given.CommandMessage.cancelProject(ID);
    private static final AggAddTask addTask = Given.CommandMessage.addTask(ID);
    private static final AggStartProject startProject = Given.CommandMessage.startProject(ID);

    private TestAggregate aggregate;
    private AmishAggregate amishAggregate;
    private BoundedContext context;
    private TestAggregateRepository repository;

    private static TestAggregate newAggregate(ProjectId id) {
        var result = new TestAggregate(id);
        return result;
    }

    private static AmishAggregate newAmishAggregate(ProjectId id) {
        var result = new AmishAggregate(id);
        return result;
    }

    private static List<Event> generateProjectEvents() {
        var projectName = AggregateTest.class.getSimpleName();
        var events = ImmutableList.<Event>builder()
                .add(event(projectCreated(ID, projectName), 1))
                .add(event(taskAdded(ID), 3))
                .add(event(projectStarted(ID), 4))
                .build();
        return events;
    }

    /**
     * Casts {@linkplain TestAggregate the aggregate under the test} to {@link Aggregate},
     * class, which is in the same package with this test, so that we call package-access methods.
     */
    private Aggregate<?, ?, ?> aggregate() {
        return aggregate;
    }

    @BeforeEach
    void setUp() {
        ModelTests.dropAllModels();
        aggregate = newAggregate(ID);
        amishAggregate = newAmishAggregate(ID);
        context = BoundedContextBuilder.assumingTests(true)
                                       .build();
        repository = new TestAggregateRepository();
        context.internalAccess()
               .register(repository);
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Nested
    @DisplayName("expose")
    class Expose {

        @Test
        @DisplayName("handled command classes")
        void handledCommandClasses() {
            Set<CommandClass> commandClasses =
                    asAggregateClass(TestAggregate.class)
                            .commands();

            assertEquals(3, commandClasses.size());

            assertCommandClasses(commandClasses,
                                 AggCreateProject.class,
                                 AggAddTask.class,
                                 AggStartProject.class);
        }

        @Test
        @DisplayName("current state")
        void currentState() {
            dispatchCommand(aggregate, command(createProject));
            assertEquals(Status.CREATED, aggregate.state()
                                                  .getStatus());

            dispatchCommand(aggregate, command(startProject));
            assertEquals(Status.STARTED, aggregate.state()
                                                  .getStatus());
        }

        @Test
        @DisplayName("non-null last modification time")
        void timeLastModified() {
            var creationTime = new TestAggregate(ID).whenModified();
            assertNotNull(creationTime);
        }
    }

    @Test
    @DisplayName("handle one command and apply appropriate event")
    void handleCommandProperly() {
        dispatchCommand(aggregate, command(createProject));

        assertTrue(aggregate.createProjectCommandHandled);
        assertTrue(aggregate.projectCreatedEventApplied);
    }

    @Nested
    @DisplayName("advance version")
    class AdvanceVersion {

        @Test
        @DisplayName("by one upon handling command with one event")
        void byOne() {
            var version = aggregate.versionNumber();

            dispatchCommand(aggregate, command(createProject));

            assertEquals(version + 1, aggregate.versionNumber());
        }

        /**
         * This is a most typical use-case with a single event returned in response to a command.
         */
        @Test
        @DisplayName("by one upon handling command with single event and empty event applier")
        void byOneForEmptyApplier() {
            var version = amishAggregate.versionNumber();

            var command = command(pauseProject);
            List<? extends Message> messages = dispatchCommand(amishAggregate, command)
                    .getSuccess()
                    .getProducedEvents()
                    .getEventList();
            assertEquals(1, messages.size());

            assertEquals(version + 1, amishAggregate.versionNumber());
        }

        /**
         * This tests a use-case implying returning a {@code List} of events in response to a
         * command.
         */
        @Test
        @DisplayName("by number of events upon handling command with several events")
        void byNumberOfEvents() {
            var version = amishAggregate.versionNumber();

            var command = command(cancelProject);
            List<? extends Message> eventMessages =
                    dispatchCommand(amishAggregate, command)
                            .getSuccess()
                            .getProducedEvents()
                            .getEventList();
            // Expecting to return more than one to differ from other testing scenarios.
            assertTrue(eventMessages.size() > 1);

            assertEquals(version + eventMessages.size(), amishAggregate.versionNumber());
        }

        @Test
        @DisplayName("by number of commands upon handling several commands")
        void byNumberOfCommands() {
            var version = aggregate.versionNumber();

            dispatchCommand(aggregate, command(createProject));
            dispatchCommand(aggregate, command(startProject));
            dispatchCommand(aggregate, command(addTask));

            assertEquals(version + 3, aggregate.versionNumber());
        }
    }

    @Test
    @DisplayName("write its version into event context")
    void writeVersionIntoEventContext() {
        dispatchCommand(aggregate, command(createProject));

        // Get the first event since the command assignee produces only one event message.
        Aggregate<?, ?, ?> agg = aggregate;
        List<Event> uncommittedEvents = agg.getUncommittedEvents()
                                           .list();
        var event = uncommittedEvents.get(0);
        var context = event.context();
        assertThat(aggregate.version())
                .isEqualTo(context.getVersion());
    }

    @Test
    @DisplayName("handle only dispatched commands")
    void handleOnlyDispatchedCommands() {
        dispatchCommand(aggregate, command(createProject));

        assertTrue(aggregate.createProjectCommandHandled);
        assertTrue(aggregate.projectCreatedEventApplied);

        assertFalse(aggregate.addTaskCommandHandled);
        assertFalse(aggregate.taskAddedEventApplied);

        assertFalse(aggregate.startProjectCommandHandled);
        assertFalse(aggregate.projectStartedEventApplied);
    }

    @Test
    @DisplayName("invoke event applier after command assignee")
    void invokeApplierAfterCommandHandler() {
        dispatchCommand(aggregate, command(createProject));
        assertTrue(aggregate.createProjectCommandHandled);
        assertTrue(aggregate.projectCreatedEventApplied);

        dispatchCommand(aggregate, command(addTask));
        assertTrue(aggregate.addTaskCommandHandled);
        assertTrue(aggregate.taskAddedEventApplied);

        dispatchCommand(aggregate, command(startProject));
        assertTrue(aggregate.startProjectCommandHandled);
        assertTrue(aggregate.projectStartedEventApplied);
    }

    @Nested
    @DisplayName("throw when missing")
    class ThrowOnMissing {

        @Test
        @MuteLogging
        @DisplayName("command assignee")
        void commandHandler() {
            ModelTests.dropAllModels();
            var aggregate = new AggregateWithMissingApplier(ID);

            // Pass a command for which the target aggregate does not have a handling method.
            assertThrows(ModelError.class,
                         () -> dispatchCommand(aggregate, command(addTask)));
        }

        @Test
        @MuteLogging
        @DisplayName("event applier for the event emitted in a result of command handling")
        void eventApplier() {
            ModelTests.dropAllModels();
            var aggregate = new AggregateWithMissingApplier(ID);
            var command = command(createProject);
            var outcome = dispatchCommand(aggregate, command);
            assertTrue(aggregate.commandHandled());
            assertTrue(outcome.hasError());
            var error = outcome.getError();
            assertThat(error.getType())
                    .isEqualTo(ModelError.class.getCanonicalName());
        }
    }

    @Nested
    @DisplayName("have state")
    class HaveState {

        @Test
        @DisplayName("updated when command is handled")
        void updatedUponCommandHandled() {
            dispatchCommand(aggregate, command(createProject));

            var state = aggregate.state();

            assertEquals(ID, state.getId());
            assertEquals(Status.CREATED, state.getStatus());
        }
    }

    @Test
    @DisplayName("record modification time when command is handled")
    void recordModificationUponCommandHandled() {
        try {
            var frozenTime = Time.currentTime();
            Time.setProvider(new FrozenMadHatterParty(frozenTime));

            dispatchCommand(aggregate, command(createProject));

            assertEquals(frozenTime, aggregate.whenModified());
        } finally {
            Time.resetProvider();
        }
    }

    @Test
    @DisplayName("replay historical events")
    void playEvents() {
        var events = generateProjectEvents();
        var aggregateHistory = AggregateHistory.newBuilder()
                .addAllEvent(events)
                .build();

        AggregateTransaction<?, ?, ?> tx = AggregateTransaction.start(aggregate);
        aggregate().replay(aggregateHistory);
        tx.commit();

        assertTrue(aggregate.projectCreatedEventApplied);
        assertTrue(aggregate.taskAddedEventApplied);
        assertTrue(aggregate.projectStartedEventApplied);
    }

    @Test
    @DisplayName("restore snapshot during play")
    void restoreSnapshot() {
        dispatchCommand(aggregate, command(createProject));

        var snapshot = aggregate().toSnapshot();

        Aggregate<?, ?, ?> anotherAggregate = newAggregate(aggregate.id());

        AggregateTransaction<?, ?, ?> tx = AggregateTransaction.start(anotherAggregate);
        anotherAggregate.replay(AggregateHistory.newBuilder()
                                                .setSnapshot(snapshot)
                                                .build());
        tx.commit();

        assertEquals(aggregate, anotherAggregate);
    }

    @Nested
    @DisplayName("after dispatch, return event records")
    class ReturnEventRecords {

        @Test
        @DisplayName("which are uncommitted")
        void uncommittedAfterDispatch() {
            aggregate.dispatchCommands(command(createProject),
                                       command(addTask),
                                       command(startProject));

            List<Event> events = aggregate().getUncommittedEvents()
                                            .list();

            assertEventClasses(getEventClasses(events),
                               AggProjectCreated.class, AggTaskAdded.class,
                               AggProjectStarted.class);
        }

        @Test
        @DisplayName("which are being committed")
        void beingCommittedAfterDispatch() {
            aggregate.dispatchCommands(command(createProject),
                                       command(addTask),
                                       command(startProject));
            aggregate().commitEvents();
            var historyBackward =
                    ImmutableList.copyOf(aggregate().historyBackward());
            assertEventClasses(
                    getEventClasses(historyBackward),
                    AggProjectCreated.class, AggTaskAdded.class, AggProjectStarted.class
            );
        }

        private Collection<EventClass> getEventClasses(Collection<Event> events) {
            var result = events.stream()
                    .map(EventClass::of)
                    .collect(toList());
            return result;
        }

    }

    @Nested
    @DisplayName("by default, not have any event records")
    class NotHaveEventRecords {

        @Test
        @DisplayName("which are uncommitted")
        void uncommittedByDefault() {
            var events = aggregate().getUncommittedEvents();

            assertFalse(events.nonEmpty());
        }

        @Test
        @DisplayName("which are being committed")
        void beingCommittedByDefault() {
            aggregate().commitEvents();
            assertFalse(aggregate.historyBackward()
                                 .hasNext());
        }
    }

    @Test
    @DisplayName("clear event records when commit after dispatch")
    void clearEventsWhenCommitAfterDispatch() {
        aggregate.dispatchCommands(command(createProject),
                                   command(addTask),
                                   command(startProject));
        assertTrue(aggregate().getUncommittedEvents()
                              .nonEmpty());
        aggregate().commitEvents();
        assertFalse(aggregate().getUncommittedEvents()
                               .nonEmpty());
    }

    @Test
    @DisplayName("transform current state to snapshot event")
    void transformCurrentStateToSnapshot() {

        dispatchCommand(aggregate, command(createProject));

        var snapshot = aggregate().toSnapshot();
        var state = unpack(snapshot.getState(), AggProject.class);

        assertEquals(ID, state.getId());
        assertEquals(Status.CREATED, state.getStatus());
    }

    @Test
    @DisplayName("restore state from snapshot")
    void restoreStateFromSnapshot() {

        dispatchCommand(aggregate, command(createProject));

        var snapshotNewProject = aggregate().toSnapshot();

        Aggregate<?, ?, ?> anotherAggregate = newAggregate(aggregate.id());

        AggregateTransaction<?, ?, ?> tx = AggregateTransaction.start(anotherAggregate);
        anotherAggregate.restore(snapshotNewProject);
        tx.commit();

        assertEquals(aggregate.state(), anotherAggregate.state());
        assertEquals(aggregate.version(), anotherAggregate.version());
        assertEquals(aggregate.lifecycleFlags(), anotherAggregate.lifecycleFlags());
    }

    @Test
    @DisplayName("increment version upon state changing event applied")
    void incrementVersionOnEventApplied() {
        var version = aggregate.version()
                               .getNumber();
        // Dispatch two commands that cause events that modify aggregate state.
        aggregate.dispatchCommands(command(createProject), command(startProject));

        assertEquals(version + 2, aggregate.version()
                                           .getNumber());
    }

    @Test
    @DisplayName("record modification timestamp")
    void recordModificationTimestamp() {
        try {
            var provider = new BackToTheFuture();
            Time.setProvider(provider);

            var currentTime = Time.currentTime();

            aggregate.dispatchCommands(command(createProject));

            assertEquals(currentTime, aggregate.whenModified());

            currentTime = provider.forward(10);

            aggregate.dispatchCommands(command(startProject));

            assertEquals(currentTime, aggregate.whenModified());
        } finally {
            Time.resetProvider();
        }
    }

    @Nested
    @DisplayName("prohibit")
    class Prohibit {

        @Test
        @MuteLogging
        @DisplayName("to call `state()` from within applier")
        void callStateFromApplier() {
            ModelTests.dropAllModels();
            var faultyAggregate =
                    new FaultyAggregate(ID, false, false);
            var command = addTask(ID);
            var outcome = dispatchCommand(faultyAggregate, env(command));

            assertThat(outcome.hasError())
                    .isTrue();
            var error = outcome.getError();
            var expectedError = Error.newBuilder()
                    .setType(IllegalStateException.class.getCanonicalName())
                    .buildPartial();
            assertThat(error)
                    .comparingExpectedFieldsOnly()
                    .isEqualTo(expectedError);
        }
    }

    @Nested
    @DisplayName("catch `RuntimeException`s in")
    class CatchHandlerFailures {

        @Test
        @MuteLogging
        @DisplayName("handlers")
        void whenHandlerThrows() {
            ModelTests.dropAllModels();

            var faultyAggregate = new FaultyAggregate(ID, true, false);

            var command = Given.ACommand.createProject();
            var outcome = dispatchCommand(faultyAggregate, env(command));
            assertTrue(outcome.hasError());
            var error = outcome.getError();
            assertThat(error)
                    .comparingExpectedFieldsOnly()
                    .isEqualTo(Error.newBuilder()
                                    .setType(IllegalStateException.class.getCanonicalName())
                                    .setMessage(FaultyAggregate.BROKEN_HANDLER)
                                    .buildPartial());
        }

        @Test
        @MuteLogging
        @DisplayName("appliers")
        void whenApplierThrows() {
            ModelTests.dropAllModels();
            var faultyAggregate =
                    new FaultyAggregate(ID, false, true);

            var command = Given.ACommand.createProject();
            var outcome = dispatchCommand(faultyAggregate, env(command));

            assertThat(outcome.hasError()).isTrue();
            var error = outcome.getError();
            assertThat(error)
                    .comparingExpectedFieldsOnly()
                    .isEqualTo(Error.newBuilder()
                                    .setType(IllegalStateException.class.getCanonicalName())
                                    .setMessage(FaultyAggregate.BROKEN_APPLIER)
                                    .buildPartial());
        }

        @Test
        @DisplayName("the event replay")
        void whenPlayThrows() {
            ModelTests.dropAllModels();
            var faultyAggregate = new FaultyAggregate(ID, false, true);

            var event = event(projectCreated(ID, getClass().getSimpleName()), 1);
            AggregateTransaction.start(faultyAggregate);
            var history = AggregateHistory.newBuilder()
                    .addEvent(event)
                    .build();
            var batchDispatchOutcome =
                    ((Aggregate<?, ?, ?>) faultyAggregate).replay(history);
            assertThat(batchDispatchOutcome.getSuccessful()).isFalse();
            var expectedTarget = MessageId.newBuilder()
                    .setId(Identifier.pack(faultyAggregate.id()))
                    .setTypeUrl(faultyAggregate.modelClass()
                                               .stateTypeUrl()
                                               .value())
                    .buildPartial();
            assertThat(batchDispatchOutcome.getTargetEntity())
                    .comparingExpectedFieldsOnly()
                    .isEqualTo(expectedTarget);
            assertThat(batchDispatchOutcome.getOutcomeCount()).isEqualTo(1);
            var outcome = batchDispatchOutcome.getOutcome(0);
            assertThat(outcome.hasError()).isTrue();
            assertThat(outcome.getPropagatedSignal()).isEqualTo(event.messageId());
            var error = outcome.getError();
            assertThat(error.getType()).isEqualTo(IllegalStateException.class.getCanonicalName());
            assertThat(error.getMessage()).isEqualTo(FaultyAggregate.BROKEN_APPLIER);
        }
    }

    @Test
    @DisplayName("not allow getting state builder from outside event applier")
    void notGetStateBuilderOutsideOfApplier() {
        assertThrows(IllegalStateException.class, () -> new IntAggregate(100).builder());
    }

    @Nested
    @DisplayName("traverse history")
    class TraverseHistory {

        private Iterator<Event> history;

        @Test
        @DisplayName("iterating through newest events first")
        void throughNewestEventsFirst() {
            var tenantId = newTenantId();
            var createCommand = command(createProject, tenantId);
            var startCommand = command(startProject, tenantId);
            var addTaskCommand = command(addTask, tenantId);
            var addTaskCommand2 = command(addTask, tenantId);

            var commandBus = context.commandBus();
            StreamObserver<Ack> noOpObserver = noOpObserver();
            commandBus.post(createCommand, noOpObserver);
            commandBus.post(addTaskCommand, noOpObserver);
            commandBus.post(newArrayList(addTaskCommand2, startCommand), noOpObserver);

            var aggregate = repository.loadAggregate(tenantId, ID);
            history = aggregate.historyBackward();

            assertNextCommandId().isEqualTo(startCommand.id());
            assertNextCommandId().isEqualTo(addTaskCommand2.id());
            assertNextCommandId().isEqualTo(addTaskCommand.id());
            assertNextCommandId().isEqualTo(createCommand.id());

            assertThat(history.hasNext())
                    .isFalse();
        }

        private ProtoSubject assertNextCommandId() {
            var event = history.next();
            return assertThat(event.rootMessage()
                                   .asCommandId());
        }

        @Test
        @DisplayName("up to latest snapshot")
        void upToLatestSnapshot() {
            repository.setSnapshotTrigger(3);

            var tenantId = newTenantId();
            var createCommand = command(createProject, tenantId);
            var startCommand = command(startProject, tenantId);
            var addTaskCommand = command(addTask, tenantId);
            var addTaskCommand2 = command(addTask, tenantId);

            var commandBus = context.commandBus();
            StreamObserver<Ack> noOpObserver = noOpObserver();
            commandBus.post(createCommand, noOpObserver);
            commandBus.post(startCommand, noOpObserver);
            commandBus.post(ImmutableList.of(addTaskCommand, addTaskCommand2), noOpObserver);

            var aggregate = repository.loadAggregate(tenantId, ID);

            history = aggregate.historyBackward();

            assertNextCommandId()
                    .isEqualTo(addTaskCommand2.id());
            assertThat(history.hasNext())
                    .isFalse();
        }
    }

    @Test
    @DisplayName("throw `DuplicateCommandException` for a duplicated command")
    @MuteLogging
    void acknowledgeExceptionForDuplicateCommand() {
        var monitor = new DiagnosticMonitor();
        context.internalAccess()
               .registerEventDispatcher(monitor);

        var tenantId = newTenantId();
        var createCommand = command(createProject, tenantId);
        var envelope = CommandEnvelope.of(createCommand);
        repository.dispatch(envelope);
        repository.dispatch(envelope);
        var duplicateCommandEvents = monitor.duplicateCommandEvents();
        assertThat(duplicateCommandEvents).hasSize(1);
        var event = duplicateCommandEvents.get(0);
        assertThat(event.getDuplicateCommand())
                .isEqualTo(envelope.messageId());
    }

    @Test
    @DisplayName("run `IdempotencyGuard` when dispatching commands")
    void checkCommandsUponHistory() {
        var monitor = new DiagnosticMonitor();
        context.internalAccess()
               .registerEventDispatcher(monitor);
        var createCommand = command(createProject);
        var cmd = CommandEnvelope.of(createCommand);
        var tenantId = newTenantId();
        Supplier<MessageEndpoint<ProjectId, ?>> endpoint =
                () -> new AggregateCommandEndpoint<>(repository, cmd);
        dispatch(tenantId, endpoint);
        dispatch(tenantId, endpoint);
        var events = monitor.duplicateCommandEvents();
        assertThat(events).hasSize(1);
        var systemEvent = events.get(0);
        assertThat(systemEvent.getDuplicateCommand())
                .isEqualTo(cmd.messageId());
    }

    @Test
    @DisplayName("run Idempotency guard when dispatching events")
    void checkEventsUponHistory() {
        var monitor = new DiagnosticMonitor();
        context.internalAccess()
               .registerEventDispatcher(monitor);
        var eventMessage = AggProjectDeleted.newBuilder()
                .setProjectId(ID)
                .build();
        var event = event(eventMessage, 2);
        var envelope = EventEnvelope.of(event);
        Supplier<MessageEndpoint<ProjectId, ?>> endpoint =
                () -> new AggregateEventReactionEndpoint<>(repository, envelope);
        var tenantId = newTenantId();
        dispatch(tenantId, endpoint);
        dispatch(tenantId, endpoint);
        var events = monitor.duplicateEventEvents();
        assertThat(events).hasSize(1);
        var systemEvent = events.get(0);
        assertThat(systemEvent.getDuplicateEvent())
                .isEqualTo(envelope.messageId());
    }

    private static void dispatch(TenantId tenant,
                                 Supplier<MessageEndpoint<ProjectId, ?>> endpoint) {
        with(tenant).run(
                () -> endpoint.get()
                              .dispatchTo(ID)
        );
    }

    @Nested
    @DisplayName("create a single event when emitting a pair without second value")
    class CreateSingleEventForPair extends ContextAwareTest {

        @Override
        protected BoundedContextBuilder contextBuilder() {
            return BoundedContextBuilder.assumingTests()
                                        .add(new TaskAggregateRepository());
        }

        /**
         * Ensures that a {@linkplain io.spine.server.tuple.Pair pair} with an empty second
         * optional value returned from a command assignee stores a single event.
         *
         * <p>The command assignee that should return a pair is
         * {@link TaskAggregate#handle(AggAssignTask)
         * TaskAggregate#handle(AggAssignTask)}.
         */
        @Test
        @DisplayName("when dispatching a command")
        void fromCommandDispatch() {
            context().receivesCommand(createTask())
                     .assertEvents()
                     .withType(AggTaskCreated.class)
                     .isNotEmpty();
        }

        /**
         * Ensures that a {@linkplain io.spine.server.tuple.Pair pair} with an empty second optional
         * value returned from a reaction on an event stores a single event.
         *
         * <p>The first event is produced while handling a command by the
         * {@link TaskAggregate#handle(AggAssignTask) TaskAggregate#handle(AggAssignTask)}.
         * Then as a reaction to this event a single event should be fired as part of the pair by
         * {@link TaskAggregate#on(AggTaskAssigned) TaskAggregate#on(AggTaskAssigned)}.
         */
        @Test
        @DisplayName("when reacting on an event")
        void fromEventReact() {
            var assertEvents = context().receivesCommand(assignTask())
                                        .assertEvents();
            assertEvents.hasSize(2);
            assertEvents.withType(AggTaskAssigned.class)
                        .hasSize(1);
            assertEvents.withType(AggUserNotified.class)
                        .hasSize(1);
        }

        /**
         * Ensures that a {@linkplain io.spine.server.tuple.Pair pair} with an empty second optional
         * value returned from a reaction on a rejection stores a single event.
         *
         * <p>The rejection is fired by the {@link TaskAggregate#handle(AggReassignTask)
         * TaskAggregate.handle(AggReassignTask)}
         * and handled by the {@link TaskAggregate#on(AggCannotReassignUnassignedTask)
         * TaskAggregate.on(AggCannotReassignUnassignedTask)}.
         */
        @Test
        @DisplayName("when reacting on a rejection")
        void fromRejectionReact() {
            var assertEvents = context().receivesCommand(reassignTask())
                                        .assertEvents();
            assertEvents.hasSize(2);
            assertEvents.withType(AggCannotReassignUnassignedTask.class)
                        .hasSize(1);
            assertEvents.withType(AggUserNotified.class)
                        .hasSize(1);
        }
    }

    @Nested
    @DisplayName("allow having validation on the aggregate state and")
    class AllowValidatedAggregates extends ContextAwareTest {

        private final ThermometerId thermometer = ThermometerId.generate();

        @Override
        protected BoundedContextBuilder contextBuilder() {
            return BoundedContextBuilder
                    .assumingTests()
                    .add(new SafeThermometerRepo(thermometer));
        }

        @Test
        @DisplayName("not change the Aggregate state when there is no reaction on the event")
        void notChangeStateIfNoReaction() {
            var booksOnFire = TemperatureChanged.newBuilder()
                    .setFahrenheit(451)
                    .build();
            context().receivesExternalEvent(booksOnFire)
                     .assertEntity(thermometer, SafeThermometer.class)
                     .doesNotExist();
        }

        @Test
        @DisplayName("save valid aggregate state on change")
        void safelySaveValidState() {
            var gettingWarmer = TemperatureChanged.newBuilder()
                    .setFahrenheit(72)
                    .build();
            context().receivesExternalEvent(gettingWarmer);
            var expected = Thermometer.newBuilder()
                    .setId(thermometer)
                    .setFahrenheit(72)
                    .build();
            context().assertState(thermometer, expected);
        }
    }
}
