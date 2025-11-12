/*
 * Copyright 2023, TeamDev. All rights reserved.
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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import io.grpc.stub.StreamObserver;
import io.spine.base.Identifier;
import io.spine.core.Ack;
import io.spine.core.Event;
import io.spine.core.Events;
import io.spine.server.aggregate.given.repo.AnemicAggregateRepository;
import io.spine.server.aggregate.given.repo.EventDiscardingAggregateRepository;
import io.spine.server.aggregate.given.repo.FailingAggregateRepository;
import io.spine.server.aggregate.given.repo.ProjectAggregate;
import io.spine.server.aggregate.given.repo.ProjectAggregateRepository;
import io.spine.server.aggregate.given.repo.ReactingAggregate;
import io.spine.server.aggregate.given.repo.ReactingRepository;
import io.spine.server.aggregate.given.repo.RejectingRepository;
import io.spine.server.aggregate.given.repo.RejectionReactingAggregate;
import io.spine.server.aggregate.given.repo.RejectionReactingRepository;
import io.spine.server.tenant.TenantAwareOperation;
import io.spine.server.type.CommandClass;
import io.spine.server.type.CommandEnvelope;
import io.spine.server.type.EventClass;
import io.spine.server.type.EventEnvelope;
import io.spine.system.server.DiagnosticMonitor;
import io.spine.test.aggregate.AggProject;
import io.spine.test.aggregate.ProjectId;
import io.spine.test.aggregate.Task;
import io.spine.test.aggregate.command.AggAddTask;
import io.spine.test.aggregate.command.AggCreateProject;
import io.spine.test.aggregate.command.AggCreateProjectWithChildren;
import io.spine.test.aggregate.command.AggStartProject;
import io.spine.test.aggregate.command.AggStartProjectWithChildren;
import io.spine.test.aggregate.event.AggProjectArchived;
import io.spine.test.aggregate.event.AggProjectDeleted;
import io.spine.test.aggregate.number.FloatEncountered;
import io.spine.test.aggregate.number.RejectNegativeLong;
import io.spine.testdata.Sample;
import io.spine.testing.logging.mute.MuteLogging;
import io.spine.testing.server.TestEventFactory;
import io.spine.testing.server.blackbox.BlackBox;
import io.spine.testing.server.model.ModelTests;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.base.Time.currentTime;
import static io.spine.grpc.StreamObservers.noOpObserver;
import static io.spine.protobuf.Messages.isNotDefault;
import static io.spine.server.aggregate.AggregateRepository.DEFAULT_SNAPSHOT_TRIGGER;
import static io.spine.server.aggregate.given.repo.AggregateRepositoryTestEnv.context;
import static io.spine.server.aggregate.given.repo.AggregateRepositoryTestEnv.givenAggregate;
import static io.spine.server.aggregate.given.repo.AggregateRepositoryTestEnv.givenAggregateId;
import static io.spine.server.aggregate.given.repo.AggregateRepositoryTestEnv.givenStoredAggregate;
import static io.spine.server.aggregate.given.repo.AggregateRepositoryTestEnv.givenStoredAggregateWithId;
import static io.spine.server.aggregate.given.repo.AggregateRepositoryTestEnv.repository;
import static io.spine.server.aggregate.given.repo.AggregateRepositoryTestEnv.requestFactory;
import static io.spine.server.aggregate.given.repo.AggregateRepositoryTestEnv.resetBoundedContext;
import static io.spine.server.aggregate.given.repo.AggregateRepositoryTestEnv.resetRepository;
import static io.spine.server.aggregate.model.AggregateClass.asAggregateClass;
import static io.spine.testing.core.given.GivenTenantId.generate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings({"InnerClassMayBeStatic", "ClassCanBeStatic"
        /* JUnit nested classes cannot be static. */,
        "DuplicateStringLiteralInspection" /* Common test display names */})
@DisplayName("`AggregateRepository` should")
class AggregateRepositoryTest {

    @BeforeEach
    void setUp() {
        ModelTests.dropAllModels();
        resetBoundedContext();
        resetRepository();
        context().internalAccess()
                 .register(repository());
    }

    @AfterEach
    void tearDown() {
        context().close();
    }

    @Nested
    @DisplayName("expose")
    class Expose {

        @Test
        @DisplayName("aggregate class")
        void aggregateClass() {
            assertEquals(ProjectAggregate.class, repository().entityClass());
        }

        @Test
        @DisplayName("command classes handled by aggregate")
        void aggregateCommandClasses() {
            Set<CommandClass> aggregateCommands =
                    asAggregateClass(ProjectAggregate.class)
                            .commands();
            Set<CommandClass> exposedByRepository = repository().messageClasses();

            assertTrue(exposedByRepository.containsAll(aggregateCommands));
        }

        @Test
        @DisplayName("event classes on which aggregate reacts")
        void aggregateEventClasses() {
            Set<EventClass> eventClasses = repository().events();
            assertTrue(eventClasses.contains(EventClass.from(AggProjectArchived.class)));
            assertTrue(eventClasses.contains(EventClass.from(AggProjectDeleted.class)));
        }
    }

    @Nested
    @DisplayName("store and load aggregate")
    class StoreAndLoadAggregate {

        @Test
        @DisplayName("using snapshot")
        void usingSnapshot() {
            var id = Sample.messageOfType(ProjectId.class);
            repository().setSnapshotTrigger(3);
            var expected = givenAggregate().withUncommittedEvents(id);
            repository().store(expected);

            var actual = assertFound(id);

            assertEquals(expected.id(), actual.id());
            assertEquals(expected.state(), actual.state());
        }

        @Test
        @DisplayName("without using snapshot")
        void notUsingSnapshot() {
            var id = Sample.messageOfType(ProjectId.class);
            var expected = givenAggregate().withUncommittedEvents(id);

            repository().store(expected);
            var actual = assertFound(id);

            assertTrue(isNotDefault(actual.state()));
            assertEquals(expected.id(), actual.id());
            assertEquals(expected.state(), actual.state());
        }

        private ProjectAggregate assertFound(ProjectId id) {
            var optional = repository().find(id);
            assertTrue(optional.isPresent());
            return optional.get();
        }
    }

    @Nested
    @DisplayName("manage snapshots properly")
    class ManageSnapshots {

        @Test
        @DisplayName("when it's required to store snapshot")
        void whenNeededToStore() {
            // This should make the repository write the snapshot.
            repository().setSnapshotTrigger(3);
            var aggregate = givenAggregate().withUncommittedEvents();

            repository().store(aggregate);
            var record = readRecord(aggregate);
            assertTrue(record.hasSnapshot());
            assertEquals(0, record.getEventCount());
        }

        @Test
        @DisplayName("when storing snapshot isn't needed")
        void whenStoreNotNeeded() {
            var aggregate = givenAggregate().withUncommittedEvents();

            repository().store(aggregate);
            var record = readRecord(aggregate);
            assertFalse(record.hasSnapshot());
        }

        private AggregateHistory readRecord(ProjectAggregate aggregate) {
            var optional = repository().aggregateStorage()
                                       .read(aggregate.id(), DEFAULT_SNAPSHOT_TRIGGER);
            assertTrue(optional.isPresent());
            return optional.get();
        }
    }

    @Nested
    @DisplayName("have snapshot trigger")
    class HaveSnapshotTrigger {

        @Test
        @DisplayName("set to default value initially")
        void setToDefault() {
            assertEquals(DEFAULT_SNAPSHOT_TRIGGER, repository().snapshotTrigger());
        }

        @Test
        @DisplayName("set to specified value")
        void setToSpecifiedValue() {
            var newSnapshotTrigger = 1000;

            repository().setSnapshotTrigger(newSnapshotTrigger);

            assertEquals(newSnapshotTrigger, repository().snapshotTrigger());
        }

        @Test
        @DisplayName("never set to negative value")
        void notSetToNegative() {
            assertThrows(IllegalArgumentException.class, () -> repository().setSnapshotTrigger(-1));
        }

        @Test
        @DisplayName("never set to zero value")
        void notSetToZero() {
            assertThrows(IllegalArgumentException.class, () -> repository().setSnapshotTrigger(0));
        }
    }

    @Nested
    @DisplayName("pass (snapshot trigger + 1) to `AggregateReadRequest`")
    class PassSnapshotTrigger {

        @Test
        @DisplayName("when it's set to default value")
        void whenItsDefault() {
            var repository = repository();
            var storage = new TestAggregateStorage(repository.aggregateStorage());
            repository.injectStorage(storage);

            var id = Sample.messageOfType(ProjectId.class);
            loadOrCreate(repository, id);

            assertThat(storage.memoizedId())
                    .isEqualTo(id);

            assertThat(storage.memoizedBatchSize())
                    .isEqualTo(repository.snapshotTrigger() + 1);
        }

        @Test
        @DisplayName("when it's set to non-default value")
        void whenItsNonDefault() {
            var repository = repository();
            var storage = new TestAggregateStorage(repository.aggregateStorage());
            repository.injectStorage(storage);

            var nonDefaultSnapshotTrigger = DEFAULT_SNAPSHOT_TRIGGER * 2;
            repository.setSnapshotTrigger(nonDefaultSnapshotTrigger);
            var id = Sample.messageOfType(ProjectId.class);
            loadOrCreate(repository, id);

            assertThat(storage.memoizedId())
                    .isEqualTo(id);

            assertThat(storage.memoizedBatchSize())
                    .isEqualTo(nonDefaultSnapshotTrigger + 1);
        }
    }

    private static void loadOrCreate(
            AggregateRepository<ProjectId, ProjectAggregate, AggProject> repository, ProjectId id) {
        repository.loadOrCreate(id);
    }

    @Nested
    @DisplayName("find aggregates with status flag")
    class FindWithStatusFlag {

        @Test
        @DisplayName("`archived`")
        void archived() {
            var aggregate = givenStoredAggregate();

            AggregateTransaction<?, ?, ?> tx = AggregateTransaction.start(aggregate);
            aggregate.archive();
            tx.commit();
            repository().store(aggregate);

            assertTrue(repository().find(aggregate.id())
                                   .isPresent());
        }

        @Test
        @DisplayName("`deleted`")
        void deleted() {
            var aggregate = givenStoredAggregate();

            AggregateTransaction<?, ?, ?> tx = AggregateTransaction.start(aggregate);
            aggregate.archive();
            tx.commit();

            repository().store(aggregate);

            assertTrue(repository().find(aggregate.id())
                                   .isPresent());
        }
    }

    @Test
    @DisplayName("not create new aggregates upon lookup")
    void notCreateNewAggregatesOnFind() {
        var newId = Sample.messageOfType(ProjectId.class);
        var optional = repository().find(newId);
        assertFalse(optional.isPresent());
    }

    @SuppressWarnings("CheckReturnValue") // The returned value is not used in this test.
    @Test
    @DisplayName("throw `IllegalStateException` if unable to load entity by ID from storage index")
    void throwWhenUnableToLoadEntity() {
        // Store a valid aggregate.
        givenStoredAggregate();

        // Store a troublesome entity, which cannot be loaded.
        var op = new TenantAwareOperation(generate()) {
            @Override
            public void run() {
                givenStoredAggregateWithId(ProjectAggregateRepository.troublesome.getUuid());
            }
        };
        op.execute();

        var iterator =
                repository().iterator(aggregate -> true);

        // This should iterate through all and fail.
        assertThrows(IllegalStateException.class, () -> Lists.newArrayList(iterator));
    }

    @SuppressWarnings("CheckReturnValue")
    @Test
    @DisplayName("throw an `ISE` when history is corrupted")
    void throwWhenCorrupted() {
        var aggregate = givenStoredAggregate();
        var history = aggregate.recentHistory();
        var eventBuilder = history.stream()
                .findFirst()
                .orElseGet(Assertions::fail)
                .toBuilder();
        eventBuilder.setId(Events.generateId());
        eventBuilder.getContextBuilder()
                    .setTimestamp(currentTime());
        var duplicateEvent = eventBuilder.build();
        var corruptedHistory = AggregateHistory.newBuilder()
                .addEvent(duplicateEvent)
                .build();
        var id = aggregate.id();
        repository().aggregateStorage()
                    .write(id, corruptedHistory);
        assertThrows(IllegalStateException.class, () -> repository().find(id));
    }

    @Nested
    @DisplayName("allow aggregates to react")
    class AllowAggregatesReact {

        @Test
        @DisplayName("on events")
        void onEvents() {
            var repository = new ReactingRepository();
            context().internalAccess()
                     .register(repository);

            var parentId = givenAggregateId("parent");
            var childId = givenAggregateId("child");

            /*
             * Create event factory for which producer ID would be the `parentId`.
             * Custom routing set by {@linkplain ReactingRepository()} would use
             * child IDs from the event.
             */
            var factory = TestEventFactory.newInstance(Identifier.pack(parentId),
                                                       getClass());
            var msg = AggProjectArchived.newBuilder()
                    .setProjectId(parentId)
                    .addChildProjectId(childId)
                    .build();
            var event = factory.createEvent(msg);

            // Posting this event should archive the aggregate.
            context().eventBus()
                     .post(event);

            // Check that the aggregate marked itself as `archived`, and therefore became invisible
            // to regular queries.
            var optional = repository.find(childId);

            // The aggregate was created because of dispatching.
            assertTrue(optional.isPresent());

            // The proper method was called, which we check by the state the aggregate got.
            assertEquals(ReactingAggregate.PROJECT_ARCHIVED,
                         optional.get()
                                 .state()
                                 .getValue());
        }

        @Test
        @DisplayName("on rejections")
        void onRejections() {
            var context = context();
            var contextAccess = context.internalAccess();
            contextAccess.register(new RejectingRepository());
            var repository = new RejectionReactingRepository();
            contextAccess.register(repository);

            var parentId = givenAggregateId("rejectingParent");
            var childId1 = givenAggregateId("acceptingChild-1");
            var childId2 = givenAggregateId("acceptingChild-2");
            var childId3 = givenAggregateId("acceptingChild-3");

            StreamObserver<Ack> observer = noOpObserver();
            var commandBus = context.commandBus();

            // Create the parent project.
            var childProjects = ImmutableSet.of(childId1, childId2, childId3);
            var createParent = requestFactory().createCommand(
                    AggCreateProjectWithChildren.newBuilder()
                            .setProjectId(parentId)
                            .addAllChildProjectId(childProjects)
                            .build()
            );
            commandBus.post(createParent, observer);

            // Fire a command which would cause rejection.
            var startProject = requestFactory().createCommand(
                    AggStartProjectWithChildren.newBuilder()
                            .setProjectId(parentId)
                            .build()
            );
            commandBus.post(startProject, observer);

            for (var childProject : childProjects) {
                var optional = repository.find(childProject);

                assertThat(optional).isPresent();

                // Check that all the aggregates:
                // 1. got Rejections.AggCannotStartArchivedProject;
                // 2. produced the state the event;
                // 3. applied the event.
                var value = optional.get()
                                    .state()
                                    .getValue();
                assertThat(value)
                        .isEqualTo(RejectionReactingAggregate.PARENT_ARCHIVED);
            }
        }
    }

    @Nested
    @MuteLogging
    @DisplayName("post produced events to EventBus")
    class PostEventsToBus {

        private BlackBox context;

        /**
         * Create a fresh instance of the repository since this nested class uses
         * {@code BlackBox}. We cannot use the instance of the repository created by
         * {@link AggregateRepositoryTest#setUp()} because this method registers it with another
         * {@code BoundedContext}.
         */
        @BeforeEach
        void createAnotherRepository() {
            resetRepository();
            context = BlackBox.singleTenantWith(repository());
        }

        @Test
        @DisplayName("after command dispatching")
        void afterCommand() {
            var id = givenAggregateId(Identifier.newUuid());
            var create = AggCreateProject.newBuilder()
                    .setProjectId(id)
                    .setName("Command Dispatching")
                    .build();
            var task = Task.newBuilder()
                    .setTitle("Dummy Task")
                    .setDescription("Dummy Task Description")
                    .build();
            var addTask = AggAddTask.newBuilder()
                    .setProjectId(id)
                    .setTask(task)
                    .build();
            var start = AggStartProject.newBuilder()
                    .setProjectId(id)
                    .build();
            context.receivesCommands(create, addTask, start);
            assertEventVersions(1, 2, 3);
        }

        @Test
        @DisplayName("after event dispatching")
        void afterEvent() {
            var id = givenAggregateId(Identifier.newUuid());
            var create = AggCreateProject.newBuilder()
                    .setProjectId(id)
                    .setName("Command Dispatching")
                    .build();
            var start = AggStartProject.newBuilder()
                    .setProjectId(id)
                    .build();
            var parent = givenAggregateId(Identifier.newUuid());
            var archived = AggProjectArchived.newBuilder()
                    .setProjectId(parent)
                    .addChildProjectId(id)
                    .build();
            context.receivesCommands(create, start)
                   .receivesEvent(archived);
            assertEventVersions(
                    1, 2, // Results of commands.
                    3  // The result of the `archived` event.
            );
        }

        @Test
        @DisplayName("through the repository `EventFilter`")
        void throughEventFilter() {
            var id = givenAggregateId(Identifier.newUuid());
            var create = AggCreateProject.newBuilder()
                    .setProjectId(id)
                    .setName("Test Project")
                    .build();
            var start = AggStartProject.newBuilder()
                    .setProjectId(id)
                    .build();
            var parent = givenAggregateId(Identifier.newUuid());
            var archived = AggProjectArchived.newBuilder()
                    .setProjectId(parent)
                    .addChildProjectId(id)
                    .build();
            try (var context = BlackBox.singleTenantWith(new EventDiscardingAggregateRepository())) {
                context.receivesCommands(create, start)
                       .receivesEvent(archived);
                context.assertEvents()
                       .isEmpty();
            }
        }

        private void assertEventVersions(int... expectedVersions) {
            List<Event> events = context.assertEvents()
                                        .actual();
            assertThat(events).hasSize(expectedVersions.length);
            for (var i = 0; i < events.size(); i++) {
                var event = events.get(i);
                var expectedVersion = expectedVersions[i];
                assertThat(event.context()
                                .getVersion()
                                .getNumber())
                        .isEqualTo(expectedVersion);
            }
        }
    }

    @Test
    @DisplayName("route events to aggregates")
    void routeEventsToAggregates() {
        var parent = givenStoredAggregate();
        var child = givenStoredAggregate();

        assertTrue(repository().find(parent.id())
                               .isPresent());
        assertTrue(repository().find(child.id())
                               .isPresent());

        var factory = TestEventFactory.newInstance(getClass());
        var msg = AggProjectArchived.newBuilder()
                .setProjectId(parent.id())
                .addChildProjectId(child.id())
                .build();
        var event = factory.createEvent(msg);

        context().eventBus()
                 .post(event);

        // Check that the child aggregate was archived.
        var childAfterArchive = repository().find(child.id());
        assertTrue(childAfterArchive.isPresent());
        assertTrue(childAfterArchive.get()
                                    .isArchived());
        // The parent should not be archived since the dispatch route uses only
        // child aggregates from the `ProjectArchived` event.
        var parentAfterArchive = repository().find(parent.id());
        assertTrue(parentAfterArchive.isPresent());
        assertFalse(parentAfterArchive.get()
                                      .isArchived());
    }

    @Test
    @DisplayName("do nothing when event reaction fails")
    @MuteLogging
    void doNothingWhenEventReactionFails() {
        var repository = new FailingAggregateRepository();
        var contextAccess = context().internalAccess();
        contextAccess.register(repository);
        var monitor = new DiagnosticMonitor();
        contextAccess.registerEventDispatcher(monitor);

        var factory = TestEventFactory.newInstance(getClass());

        // Passing negative float value should cause an exception.
        var envelope = EventEnvelope.of(
                factory.createEvent(FloatEncountered.newBuilder()
                                            .setNumber(-412.0f)
                                            .build()));
        context().eventBus()
                 .post(envelope.outerObject());

        var handlerFailureEvents = monitor.handlerFailureEvents();
        assertThat(handlerFailureEvents).hasSize(1);
        var event = handlerFailureEvents.get(0);
        assertThat(event.getHandledSignal()).isEqualTo(envelope.messageId());
        assertThat(event.getEntity()
                        .getTypeUrl())
                .isEqualTo(repository.entityStateType()
                                     .value());
        assertThat(event.getError()
                        .getType())
                .isEqualTo(IllegalArgumentException.class.getCanonicalName());
    }

    @Test
    @MuteLogging
    @DisplayName("not pass command rejection to `onError`")
    void notPassCommandRejectionToOnError() {
        var repository = new FailingAggregateRepository();
        var contextAccess = context().internalAccess();
        contextAccess.register(repository);
        var monitor = new DiagnosticMonitor();
        contextAccess.registerEventDispatcher(monitor);

        // Passing negative long value to `FailingAggregate` should cause a rejection.
        var rejectNegative = RejectNegativeLong.newBuilder()
                .setNumber(-100_000_000L)
                .build();
        var command = requestFactory().createCommand(rejectNegative);
        var envelope = CommandEnvelope.of(
                command);
        context().commandBus()
                 .post(envelope.command(), noOpObserver());
        assertThat(monitor.handlerFailureEvents()).isEmpty();
    }

    @Test
    @DisplayName("not allow anemic aggregates")
    void notAllowAnemicAggregates() {
        assertThrows(IllegalStateException.class,
                     () -> context().internalAccess()
                                    .register(new AnemicAggregateRepository()));
    }
}
