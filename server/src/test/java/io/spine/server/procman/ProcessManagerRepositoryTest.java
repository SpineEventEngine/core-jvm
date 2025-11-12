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

package io.spine.server.procman;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.truth.Correspondence;
import com.google.protobuf.Timestamp;
import io.spine.base.CommandMessage;
import io.spine.base.EventMessage;
import io.spine.core.Command;
import io.spine.core.CommandId;
import io.spine.core.Event;
import io.spine.core.MessageId;
import io.spine.core.Origin;
import io.spine.core.TenantId;
import io.spine.server.BoundedContext;
import io.spine.server.BoundedContextBuilder;
import io.spine.server.entity.RecordBasedRepository;
import io.spine.server.entity.RecordBasedRepositoryTest;
import io.spine.server.entity.given.Given;
import io.spine.server.entity.rejection.StandardRejections.EntityAlreadyArchived;
import io.spine.server.entity.rejection.StandardRejections.EntityAlreadyDeleted;
import io.spine.server.procman.given.delivery.GivenMessage;
import io.spine.server.procman.given.repo.EventDiscardingProcManRepository;
import io.spine.server.procman.given.repo.ProjectCompletion;
import io.spine.server.procman.given.repo.RandomFillProcess;
import io.spine.server.procman.given.repo.RememberingSubscriber;
import io.spine.server.procman.given.repo.SensoryDeprivedPmRepository;
import io.spine.server.procman.given.repo.SetTestProcessId;
import io.spine.server.procman.given.repo.SetTestProcessName;
import io.spine.server.procman.given.repo.TestProcessManager;
import io.spine.server.procman.given.repo.TestProcessManagerRepository;
import io.spine.server.procman.migration.MarkPmArchived;
import io.spine.server.procman.migration.MarkPmDeleted;
import io.spine.server.procman.migration.RemovePmFromStorage;
import io.spine.server.procman.migration.UpdatePmState;
import io.spine.server.type.CommandClass;
import io.spine.server.type.CommandEnvelope;
import io.spine.server.type.EventClass;
import io.spine.server.type.EventEnvelope;
import io.spine.server.type.given.GivenEvent;
import io.spine.system.server.DiagnosticMonitor;
import io.spine.system.server.event.EntityStateChanged;
import io.spine.test.procman.PmDontHandle;
import io.spine.test.procman.Project;
import io.spine.test.procman.ProjectId;
import io.spine.test.procman.command.PmCreateProject;
import io.spine.test.procman.command.PmStartProject;
import io.spine.test.procman.command.PmThrowEntityAlreadyArchived;
import io.spine.test.procman.event.PmProjectCreated;
import io.spine.test.procman.event.PmProjectStarted;
import io.spine.test.procman.event.PmTaskAdded;
import io.spine.testing.client.TestActorRequestFactory;
import io.spine.testing.logging.mute.MuteLogging;
import io.spine.testing.server.blackbox.BlackBox;
import io.spine.type.TypeUrl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static io.spine.base.Identifier.newUuid;
import static io.spine.base.Time.currentTime;
import static io.spine.protobuf.AnyPacker.pack;
import static io.spine.protobuf.AnyPacker.unpack;
import static io.spine.server.procman.given.repo.GivenCommandMessage.ID;
import static io.spine.server.procman.given.repo.GivenCommandMessage.addTask;
import static io.spine.server.procman.given.repo.GivenCommandMessage.archiveProject;
import static io.spine.server.procman.given.repo.GivenCommandMessage.createProject;
import static io.spine.server.procman.given.repo.GivenCommandMessage.deleteProject;
import static io.spine.server.procman.given.repo.GivenCommandMessage.doNothing;
import static io.spine.server.procman.given.repo.GivenCommandMessage.projectCreated;
import static io.spine.server.procman.given.repo.GivenCommandMessage.projectStarted;
import static io.spine.server.procman.given.repo.GivenCommandMessage.startProject;
import static io.spine.server.procman.given.repo.GivenCommandMessage.taskAdded;
import static io.spine.server.procman.given.repo.SetTestProcessName.NEW_NAME;
import static io.spine.testing.TestValues.randomString;
import static io.spine.testing.server.Assertions.assertCommandClasses;
import static io.spine.testing.server.Assertions.assertEventClasses;
import static java.lang.String.format;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("`ProcessManagerRepository` should")
class ProcessManagerRepositoryTest
        extends RecordBasedRepositoryTest<TestProcessManager, ProjectId, Project> {

    private final TestActorRequestFactory requestFactory =
            new TestActorRequestFactory(getClass(), TenantId.newBuilder()
                                                            .setValue(newUuid())
                                                            .build());
    private BoundedContext context;

    @Override
    protected RecordBasedRepository<ProjectId, TestProcessManager, Project> createRepository() {
        return new TestProcessManagerRepository();
    }

    @Override
    protected TestProcessManager createEntity(ProjectId id) {
        var state = Project.newBuilder()
                .setId(id)
                .build();
        var result =
                Given.processManagerOfClass(TestProcessManager.class)
                     .withId(id)
                     .withState(state)
                     .build();
        return result;
    }

    @Override
    protected List<TestProcessManager> createEntities(int count) {
        return createNamedEntities(count, id -> randomString());
    }

    @Override
    protected List<TestProcessManager> createWithNames(int count, Supplier<String> nameSupplier) {
        return createNamedEntities(count, id -> nameSupplier.get());
    }

    private List<TestProcessManager>
    createNamedEntities(int count, Function<ProjectId, String> nameSupplier) {
        List<TestProcessManager> procmans = newArrayList();

        for (var i = 0; i < count; i++) {
            var id = createId(i);
            var name = nameSupplier.apply(id);

            var pm =
                    Given.processManagerOfClass(TestProcessManager.class)
                         .withId(id)
                         .withState(Project.newBuilder()
                                           .setId(id)
                                           .setName(name)
                                           .build())
                         .build();
            procmans.add(pm);
        }
        return procmans;
    }

    @Override
    protected List<TestProcessManager> orderedByName(List<TestProcessManager> entities) {
        return entities.stream()
                       .sorted(comparing(ProcessManagerRepositoryTest::entityName))
                       .collect(toList());
    }

    private static String entityName(TestProcessManager entity) {
        return entity.state()
                     .getName();
    }

    @Override
    protected ProjectId createId(int value) {
        return ProjectId.newBuilder()
                .setId(format("procman-number-%s", value))
                .build();
    }

    @Override
    @BeforeEach
    protected void setUp() {
        super.setUp();
        setCurrentTenant(requestFactory.tenantId());
        context = BoundedContextBuilder.assumingTests(true).build();
        context.internalAccess()
               .register(repository());
        TestProcessManager.clearMessageDeliveryHistory();
        repository().clearConfigureCalledFlag();
    }

    @Override
    @AfterEach
    protected void tearDown() throws Exception {
        context.close();
        super.tearDown();
    }

    @Override
    protected TestProcessManagerRepository repository() {
        return (TestProcessManagerRepository) super.repository();
    }

    @SuppressWarnings("CheckReturnValue")
    // We can ignore the ID of the PM handling the command in the calling tests.
    private void dispatchCommand(Command command) {
        repository().dispatchCommand(CommandEnvelope.of(command));
    }

    private void testDispatchCommand(CommandMessage cmdMsg) {
        var cmd = requestFactory.command()
                                    .create(cmdMsg);
        dispatchCommand(cmd);
        assertTrue(TestProcessManager.processed(cmdMsg));
    }

    private void testDispatchEvent(EventMessage eventMessage) {
        var event = GivenEvent.withMessage(eventMessage);
        dispatchEvent(event);
        assertTrue(TestProcessManager.processed(eventMessage));
    }

    @SuppressWarnings("CheckReturnValue") // can ignore IDs of target PMs in this test.
    private void dispatchEvent(Event event) {
        var randomCommandId = CommandId.generate();
        var originId = MessageId.newBuilder()
                .setId(pack(randomCommandId))
                .setTypeUrl("example.org/example.test.InjectEvent")
                .buildPartial();
        var actor = requestFactory.newActorContext();
        var origin = Origin.newBuilder()
                .setActorContext(actor)
                .setMessage(originId)
                .buildPartial();
        var eventContextWithTenantId =
                GivenEvent.context()
                          .toBuilder()
                          .setPastMessage(origin)
                          .buildPartial();
        var eventWithTenant = event.toBuilder()
                                     .setContext(eventContextWithTenantId)
                                     .build();
        repository().dispatch(EventEnvelope.of(eventWithTenant));
    }

    @Test
    @DisplayName("allow customizing command routing")
    void setupOfCommandRouting() {
        var repo = new ProjectCompletion.Repository();
        context.internalAccess()
               .register(repo);
        assertTrue(repo.callbackCalled());
    }

    @Nested
    @DisplayName("dispatch")
    class Dispatch {

        @Test
        @DisplayName("command")
        void command() {
            testDispatchCommand(addTask());
        }

        @Test
        @DisplayName("event")
        void event() {
            testDispatchEvent(projectCreated());
        }
    }

    @Test
    @DisplayName("dispatch command and post events")
    void dispatchCommandAndPostEvents() {
        var subscriber = new RememberingSubscriber();
        context.internalAccess()
               .registerEventDispatcher(subscriber);

        testDispatchCommand(addTask());

        var message = subscriber.getRemembered();
        assertNotNull(message);
        assertThat(message.getProjectId())
                .isEqualTo(ID);
    }

    @Nested
    @MuteLogging
    @DisplayName("not dispatch duplicate")
    class AvoidDuplicates {

        @Test
        @DisplayName("events")
        void events() {
            var monitor = new DiagnosticMonitor();
            context.internalAccess()
                   .registerEventDispatcher(monitor);
            var event = GivenMessage.projectStarted();

            dispatchEvent(event);
            assertTrue(TestProcessManager.processed(event.enclosedMessage()));
            dispatchEvent(event);

            var duplicateEventEvents = monitor.duplicateEventEvents();
            assertThat(duplicateEventEvents).hasSize(1);
            var systemEvent = duplicateEventEvents.get(0);
            assertThat(systemEvent.getDuplicateEvent())
                    .comparingExpectedFieldsOnly()
                    .isEqualTo(event.messageId());
            var eventMessage = (PmProjectStarted) event.enclosedMessage();
            assertThat(unpack(systemEvent.getEntity()
                                         .getId()))
                    .comparingExpectedFieldsOnly()
                    .isEqualTo(eventMessage.getProjectId());
        }

        @Test
        @DisplayName("commands")
        void commands() {
            var monitor = new DiagnosticMonitor();
            context.internalAccess()
                   .registerEventDispatcher(monitor);
            var command = GivenMessage.createProject();

            dispatchCommand(command);
            assertTrue(TestProcessManager.processed(command.enclosedMessage()));
            dispatchCommand(command);

            var duplicateCommandEvents =
                    monitor.duplicateCommandEvents();
            assertThat(duplicateCommandEvents).hasSize(1);
            var event = duplicateCommandEvents.get(0);
            assertThat(event.getDuplicateCommand())
                    .isEqualTo(command.messageId());
            var commandMessage = (PmCreateProject) command.enclosedMessage();
            assertThat(unpack(event.getEntity()
                                   .getId()))
                    .isEqualTo(commandMessage.getProjectId());
        }
    }

    @Nested
    @DisplayName("dispatch several")
    class DispatchSeveral {

        @Test
        @DisplayName("commands")
        void commands() {
            testDispatchCommand(createProject());
            testDispatchCommand(addTask());
            testDispatchCommand(startProject());
        }

        @Test
        @DisplayName("events")
        void events() {
            testDispatchEvent(projectCreated());
            testDispatchEvent(taskAdded());
            testDispatchEvent(projectStarted());
        }
    }

    @Nested
    @DisplayName("given archived process manager, dispatch")
    class DispatchToArchivedProcman {

        @Test
        @DisplayName("command")
        void command() {
            var archiveProject = archiveProject();
            testDispatchCommand(archiveProject);
            var projectId = archiveProject.getProjectId();
            var processManager = repository().findOrCreate(projectId);
            assertTrue(processManager.isArchived());

            // Dispatch a command to the deleted process manager.
            testDispatchCommand(addTask());
            processManager = repository().findOrCreate(projectId);
            var addedTasks = processManager.state()
                                           .getTaskList();
            assertFalse(addedTasks.isEmpty());

            // Check that the process manager was not re-created before dispatching.
            assertTrue(processManager.isArchived());
        }

        @Test
        @DisplayName("event")
        void event() {
            var archiveProject = archiveProject();
            testDispatchCommand(archiveProject);
            var projectId = archiveProject.getProjectId();
            var processManager = repository().findOrCreate(projectId);
            assertTrue(processManager.isArchived());

            // Dispatch an event to the archived process manager.
            testDispatchEvent(taskAdded());
            processManager = repository().findOrCreate(projectId);
            var addedTasks = processManager.state()
                                           .getTaskList();
            assertFalse(addedTasks.isEmpty());

            // Check that the process manager was not re-created before dispatching.
            assertTrue(processManager.isArchived());
        }
    }

    @Nested
    @DisplayName("given deleted process manager, dispatch")
    class DispatchToDeletedProcman {

        @Test
        @DisplayName("command")
        void command() {
            var deleteProject = deleteProject();
            testDispatchCommand(deleteProject);
            var projectId = deleteProject.getProjectId();
            var processManager = repository().findOrCreate(projectId);
            assertTrue(processManager.isDeleted());

            // Dispatch a command to the archived process manager.
            testDispatchCommand(addTask());
            processManager = repository().findOrCreate(projectId);
            var addedTasks = processManager.state()
                                           .getTaskList();
            assertFalse(addedTasks.isEmpty());

            // Check that the process manager was not re-created before dispatching.
            assertTrue(processManager.isDeleted());
        }

        @Test
        @DisplayName("event")
        void event() {
            var deleteProject = deleteProject();
            testDispatchCommand(deleteProject);
            var projectId = deleteProject.getProjectId();
            var processManager = repository().findOrCreate(projectId);
            assertTrue(processManager.isDeleted());

            // Dispatch an event to the deleted process manager.
            testDispatchEvent(taskAdded());
            processManager = repository().findOrCreate(projectId);
            var addedTasks = processManager.state()
                                           .getTaskList();
            assertFalse(addedTasks.isEmpty());

            // Check that the process manager was not re-created before dispatching.
            assertTrue(processManager.isDeleted());
        }
    }

    @Test
    @DisplayName("allow process manager have unmodified state after command handling")
    void allowUnmodifiedStateAfterCommand() {
        testDispatchCommand(doNothing());
    }

    @Test
    @DisplayName("produce `RoutingFailed` when dispatching unknown command")
    @MuteLogging
    void throwOnUnknownCommand() {
        var unknownCommand = requestFactory.createCommand(PmDontHandle.getDefaultInstance());
        var command = CommandEnvelope.of(unknownCommand);
        ProcessManagerRepository<ProjectId, ?, ?> repo = repository();
        var monitor = new DiagnosticMonitor();
        context.internalAccess()
               .registerEventDispatcher(monitor);
        repo.dispatchCommand(command);
        var failures = monitor.routingFailures();
        assertThat(failures).hasSize(1);
        var failure = failures.get(0);
        assertThat(failure.getEntityType()
                          .getJavaClassName())
                .isEqualTo(repo.entityClass()
                               .getCanonicalName());
        assertThat(failure.getError()
                          .getType())
                .isEqualTo(IllegalStateException.class.getName());
    }

    @Nested
    @DisplayName("return classes of")
    class ReturnClasses {

        @Test
        @DisplayName("commands")
        void command() {
            Set<CommandClass> commandClasses = repository().commandClasses();

            assertCommandClasses(
                    commandClasses,
                    PmCreateProject.class, PmCreateProject.class, PmStartProject.class
            );
        }

        @Test
        @DisplayName("events")
        void event() {
            Set<EventClass> eventClasses = repository().messageClasses();

            assertEventClasses(
                    eventClasses,
                    PmProjectCreated.class, PmTaskAdded.class, PmProjectStarted.class,
                    EntityAlreadyArchived.class, EntityAlreadyDeleted.class
            );
        }
    }

    @Test
    @DisplayName("post command rejections")
    void postCommandRejections() {
        var id = ProjectId.newBuilder()
                .setId(newUuid())
                .build();
        var commandMsg = PmThrowEntityAlreadyArchived.newBuilder()
                .setProjectId(id)
                .build();
        var command = requestFactory.createCommand(commandMsg);
        dispatchCommand(command);
        var expected = EntityAlreadyArchived.newBuilder()
                .setEntityId(pack(id))
                .build();
        assertTrue(TestProcessManager.processed(expected));
    }

    @Test
    @DisplayName("check that its `ProcessManager` class is subscribed to at least one message")
    void notRegisterIfSubscribedToNothing() {
        var repo = new SensoryDeprivedPmRepository();
        var context = BoundedContextBuilder
                .assumingTests()
                .build();
        assertThrows(IllegalStateException.class, () -> repo.registerWith(context));
    }

    @Test
    @DisplayName("provide `EventFilter` which discards `EntityStateChanged` events")
    void discardEntityStateChangedEvents() {
        var filter = repository().eventFilter();
        var projectId = ProjectId.newBuilder()
                .setId(newUuid())
                .build();
        EventMessage arbitraryEvent = PmTaskAdded.newBuilder()
                .setProjectId(projectId)
                .build();
        assertThat(filter.filter(arbitraryEvent))
                .isPresent();

        var newState = pack(currentTime());
        var oldState = pack(Timestamp.getDefaultInstance());
        var entityId = MessageId.newBuilder()
                .setTypeUrl(TypeUrl.ofEnclosed(newState)
                                   .value())
                .setId(pack(projectId))
                .build();
        var discardedEvent = EntityStateChanged.newBuilder()
                .setEntity(entityId)
                .setOldState(oldState)
                .setNewState(newState)
                .addSignalId(GivenEvent.arbitrary().messageId())
                .build();
        assertThat(filter.filter(discardedEvent))
                .isEmpty();
    }

    @Test
    @DisplayName("post all domain events through an `EventFilter`")
    void postEventsThroughFilter() {
        var projectId = ProjectId
                .newBuilder()
                .setId(newUuid())
                .build();
        var command = PmCreateProject
                .newBuilder()
                .setProjectId(projectId)
                .build();
        var context = BlackBox.singleTenantWith(new EventDiscardingProcManRepository());

        context.receivesCommand(command)
               .assertEvents()
               .isEmpty();
    }

    @DisplayName("call `configure()` callback when a `ProcessManager` instance is created")
    @Nested
    class CallingConfigure {

        @Test
        void whenFinding() {
            var repository = repository();
            assertFalse(repository.configureCalled());

            var created = repository.create(ID);
            repository.store(created);

            repository.findOrCreate(ID);
            assertTrue(repository.configureCalled());
        }

        @Test
        void whenCreating() {
            var repository = repository();
            assertFalse(repository.configureCalled());

            repository.create(ID);
            assertTrue(repository.configureCalled());
        }
    }

    @Test
    @DisplayName("update entity via a custom migration")
    void performCustomMigration() {
        // Store a new process manager instance in the repository.
        var id = createId(42);
        var repository = repository();
        var pm = new TestProcessManager(id);
        repository.store(pm);

        // Init filters by the `id_string` column.
        var query =
                Project.query()
                       .idString().is(id.toString())
                       .build();

        // Check nothing is found as column now should be empty.
        var found = repository.find(query);
        assertThat(found.hasNext()).isFalse();

        // Apply the migration.
        repository.applyMigration(id, new SetTestProcessId());

        // Check the entity is now found by the provided filters.
        var afterMigration = repository.find(query);
        assertThat(afterMigration.hasNext()).isTrue();

        // Check the new entity state has all fields updated as expected.
        var entityWithColumns = afterMigration.next();
        var expectedState = pm
                .state()
                .toBuilder()
                .setIdString(pm.getIdString())
                .build();
        assertThat(entityWithColumns.state()).isEqualTo(expectedState);
    }

    @Test
    @DisplayName("update multiple entities via a custom migration")
    void performCustomMigrationForMultiple() {
        // Store three entities to the repository.
        var id1 = createId(1);
        var id2 = createId(2);
        var id3 = createId(3);
        var repository = repository();
        var pm1 = new TestProcessManager(id1);
        var pm2 = new TestProcessManager(id2);
        var pm3 = new TestProcessManager(id3);
        repository.store(pm1);
        repository.store(pm2);
        repository.store(pm3);

        // Query by the `name` column.
        var query =
                Project.query()
                       .name().is(NEW_NAME)
                       .build();

        // Check nothing is found as the entity states were not yet updated.
        var found = repository.find(query);
        assertThat(found.hasNext()).isFalse();

        // Apply the column update to two of the three entities.
        repository.applyMigration(ImmutableSet.of(id1, id2), new SetTestProcessName());

        // Check the entities are now found by the provided filters.
        var foundAfterMigration = repository.find(query);

        var results = ImmutableList.copyOf(foundAfterMigration);
        var expectedState1 = expectedState(pm1, NEW_NAME);
        var expectedState2 = expectedState(pm2, NEW_NAME);
        assertThat(results).hasSize(2);
        assertThat(results)
                .comparingElementsUsing(entityState())
                .containsExactly(expectedState1, expectedState2);
    }

    @Test
    @DisplayName("replace the state of the migrated process")
    void replaceState() {
        var id = createId(42);
        var entity = new TestProcessManager(id);
        var repository = repository();
        repository.store(entity);
        repository.applyMigration(id, new RandomFillProcess());

        var byNewName =
                Project.query()
                       .name().is(NEW_NAME)
                       .build();

        // Ensure nothing found.
        var expectedEmpty = repository.find(byNewName);
        assertThat(expectedEmpty.hasNext()).isFalse();

        repository.applyMigration(id, new SetTestProcessName());

        // Now we should have found a single instance.
        var shouldHaveOne = repository.find(byNewName);
        var expectedState = expectedState(entity, NEW_NAME);
        var actualList = ImmutableList.copyOf(shouldHaveOne);
        assertThat(actualList)
                .comparingElementsUsing(entityState())
                .containsExactly(expectedState);
    }

    @Test
    @DisplayName("update state via migration operation")
    void updateState() {
        // Store a new process manager instance in the repository.
        var id = createId(42);
        var repository = repository();
        var pm = new TestProcessManager(id);
        repository.store(pm);

        // Init filters by the `id_string` column.
        var query =
                Project.query()
                       .idString().is(id.toString())
                       .build();

        // Check nothing is found as column now should be empty.
        var found = repository.find(query);
        assertThat(found.hasNext()).isFalse();

        // Apply the state update.
        repository.applyMigration(id, new UpdatePmState<>());

        // Check the entity is now found by the provided filters.
        var afterMigration = repository.find(query);
        assertThat(afterMigration.hasNext()).isTrue();

        // Check the column value is propagated to the entity state.
        var entityWithColumns = afterMigration.next();
        var expectedState = pm.state().toBuilder()
                .setIdString(pm.getIdString())
                .build();
        assertThat(entityWithColumns.state()).isEqualTo(expectedState);
    }

    @Test
    @DisplayName("update state of multiple entities")
    void updateStateForMultiple() {
        // Store three entities to the repository.
        var id1 = createId(1);
        var id2 = createId(2);
        var id3 = createId(3);
        var repository = repository();
        var pm1 = new TestProcessManager(id1);
        var pm2 = new TestProcessManager(id2);
        var pm3 = new TestProcessManager(id3);
        repository.store(pm1);
        repository.store(pm2);
        repository.store(pm3);

        // Apply the state update to two of the three entities.
        repository.applyMigration(ImmutableSet.of(id1, id2), new UpdatePmState<>());

        // Check that entities to which the migration has been applied now have columns updated.
        var query =
                Project.query()
                       .either(p -> p.idString().is(id1.toString()),
                               p -> p.idString().is(id2.toString()),
                               p -> p.idString().is(id3.toString()))
                       .build();
        var found = repository.find(query);

        var results = ImmutableList.copyOf(found);
        var expectedState1 = pm1
                .state()
                .toBuilder()
                .setIdString(pm1.getIdString())
                .build();
        var expectedState2 = pm2
                .state()
                .toBuilder()
                .setIdString(pm2.getIdString())
                .build();
        assertThat(results).hasSize(2);
        assertThat(results)
                .comparingElementsUsing(entityState())
                .containsExactly(expectedState1, expectedState2);
    }

    @Test
    @DisplayName("archive entity via migration")
    void archiveEntityViaMigration() {
        var id = createId(42);
        var entity = createEntity(id);
        repository().store(entity);

        repository().applyMigration(id, new MarkPmArchived<>());

        var found = repository().find(id);
        assertThat(found).isPresent();
        assertThat(found.get()
                        .isArchived()).isTrue();
    }

    @Test
    @DisplayName("delete entity via migration")
    void deleteEntityViaMigration() {
        var id = createId(42);
        var entity = createEntity(id);
        repository().store(entity);

        repository().applyMigration(id, new MarkPmDeleted<>());

        var found = repository().find(id);
        assertThat(found).isPresent();
        assertThat(found.get()
                        .isDeleted()).isTrue();
    }

    @Test
    @DisplayName("remove entity record via migration")
    void removeRecordViaMigration() {
        var id = createId(42);
        var entity = createEntity(id);
        repository().store(entity);

        repository().applyMigration(id, new RemovePmFromStorage<>());

        var found = repository().find(id);
        assertThat(found).isEmpty();
    }

    private static Correspondence<TestProcessManager, Project> entityState() {
        return Correspondence.from(ProcessManagerRepositoryTest::hasState, "has state");
    }

    private static boolean hasState(TestProcessManager actual, Project expected) {
        return actual.state().equals(expected);
    }

    private static Project expectedState(TestProcessManager entity, String expectedName) {
        return entity
                .state()
                .toBuilder()
                .setId(entity.id())
                .setName(expectedName)
                .setIdString(entity.getIdString())
                .build();
    }
}
