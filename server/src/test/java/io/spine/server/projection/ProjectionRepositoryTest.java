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

package io.spine.server.projection;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.truth.Correspondence;
import com.google.protobuf.Any;
import com.google.protobuf.Timestamp;
import io.spine.base.EventMessage;
import io.spine.client.ResponseFormat;
import io.spine.core.Event;
import io.spine.core.MessageId;
import io.spine.core.TenantId;
import io.spine.core.Versions;
import io.spine.server.BoundedContext;
import io.spine.server.BoundedContextBuilder;
import io.spine.server.entity.RecordBasedRepository;
import io.spine.server.entity.RecordBasedRepositoryTest;
import io.spine.server.entity.given.Given;
import io.spine.server.entity.storage.EntityRecordStorage;
import io.spine.server.projection.given.EntitySubscriberProjection;
import io.spine.server.projection.given.ProjectionRepositoryTestEnv.GivenEventMessage;
import io.spine.server.projection.given.ProjectionRepositoryTestEnv.NoOpTaskNamesRepository;
import io.spine.server.projection.given.ProjectionRepositoryTestEnv.SensoryDeprivedProjectionRepository;
import io.spine.server.projection.given.ProjectionRepositoryTestEnv.TestProjectionRepository;
import io.spine.server.projection.given.RandomFillProjection;
import io.spine.server.projection.given.SetTestProjectionId;
import io.spine.server.projection.given.SetTestProjectionName;
import io.spine.server.projection.given.TestProjection;
import io.spine.server.projection.migration.MarkProjectionArchived;
import io.spine.server.projection.migration.MarkProjectionDeleted;
import io.spine.server.projection.migration.RemoveProjectionFromStorage;
import io.spine.server.projection.migration.UpdateProjectionState;
import io.spine.server.type.EventClass;
import io.spine.server.type.EventEnvelope;
import io.spine.server.type.given.GivenEvent;
import io.spine.system.server.DiagnosticMonitor;
import io.spine.system.server.event.EntityStateChanged;
import io.spine.test.projection.Project;
import io.spine.test.projection.ProjectId;
import io.spine.test.projection.ProjectTaskNames;
import io.spine.test.projection.event.PrjProjectCreated;
import io.spine.test.projection.event.PrjProjectStarted;
import io.spine.test.projection.event.PrjTaskAdded;
import io.spine.testing.client.TestActorRequestFactory;
import io.spine.testing.logging.mute.MuteLogging;
import io.spine.testing.server.TestEventFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.base.Identifier.pack;
import static io.spine.base.Time.currentTime;
import static io.spine.server.projection.ProjectionRepository.nullToDefault;
import static io.spine.server.projection.given.ProjectionRepositoryTestEnv.GivenEventMessage.projectCreated;
import static io.spine.server.projection.given.ProjectionRepositoryTestEnv.dispatchedEventId;
import static io.spine.server.projection.given.SetTestProjectionName.NEW_NAME;
import static io.spine.server.projection.given.dispatch.ProjectionEventDispatcher.dispatch;
import static io.spine.testing.TestValues.randomString;
import static io.spine.testing.server.Assertions.assertEventClasses;
import static java.lang.String.format;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("`ProjectionRepository` should")
class ProjectionRepositoryTest
        extends RecordBasedRepositoryTest<TestProjection, ProjectId, Project> {

    private static final Any PRODUCER_ID = pack(GivenEventMessage.ENTITY_ID);

    private BoundedContext context;

    private static TestEventFactory newEventFactory(TenantId tenantId, Any producerId) {
        var requestFactory =
                new TestActorRequestFactory(ProjectionRepositoryTest.class, tenantId);
        return TestEventFactory.newInstance(producerId, requestFactory);
    }

    private static Event createEvent(TenantId tenantId, EventMessage eventMessage, Timestamp when) {
        var version = Versions.increment(Versions.zero());
        return newEventFactory(tenantId, PRODUCER_ID).createEvent(eventMessage, version, when);
    }

    /**
     * Simulates updating TenantIndex, which occurs during command processing
     * in multi-tenant context.
     */
    private static void keepTenantIdFromEvent(BoundedContext context, Event event) {
        var tenantId = event.tenant();
        if (context.isMultitenant()) {
            context.internalAccess()
                   .tenantIndex()
                   .keep(tenantId);
        }
    }

    @Override
    protected TestProjectionRepository repository() {
        return (TestProjectionRepository) super.repository();
    }

    @Override
    protected RecordBasedRepository<ProjectId, TestProjection, Project> createRepository() {
        var repository = new TestProjectionRepository();
        repository.registerWith(context);
        return repository;
    }

    @Override
    protected TestProjection createEntity(ProjectId id) {
        var project = Project.newBuilder()
                .setId(id)
                .build();
        var projection =
                Given.projectionOfClass(TestProjection.class)
                     .withId(id)
                     .withState(project)
                     .build();
        return projection;
    }

    @Override
    protected List<TestProjection> createEntities(int count) {
        List<TestProjection> projections = Lists.newArrayList();

        for (var i = 0; i < count; i++) {
            var id = createId(i);
            var projection =
                    Given.projectionOfClass(TestProjection.class)
                         .withId(id)
                         .withState(Project.newBuilder()
                                           .setId(id)
                                           .setName("Test name " + randomString())
                                           .build())
                         .build();
            projections.add(projection);
        }

        return projections;
    }

    @Override
    protected List<TestProjection> createWithNames(int count, Supplier<String> nameSupplier) {
        List<TestProjection> projections = Lists.newArrayList();

        for (var i = 0; i < count; i++) {
            var id = createId(i);
            var projection = Given.projectionOfClass(TestProjection.class)
                                  .withId(id)
                                  .withState(newProject(id, nameSupplier.get()))
                                  .build();
            projections.add(projection);
        }

        return projections;
    }

    @Override
    protected List<TestProjection> orderedByName(List<TestProjection> entities) {
        return entities.stream()
                .sorted(comparing(ProjectionRepositoryTest::entityName))
                .collect(toList());
    }

    private static String entityName(TestProjection entity) {
        return entity.state()
                     .getName();
    }

    private static Project newProject(ProjectId id, String name) {
        return Project.newBuilder()
                .setId(id)
                .setName(name)
                .build();
    }

    @Override
    protected ProjectId createId(int i) {
        return ProjectId.newBuilder()
                .setId(format("test-projection-%s", i))
                .build();
    }

    @Override
    @BeforeEach
    protected void setUp() {
        context = BoundedContext
                .multitenant(getClass().getSimpleName())
                .build();
        super.setUp();
        context.internalAccess()
               .register(this.repository());
        TestProjection.clearMessageDeliveryHistory();
    }

    /**
     * Closes the BoundedContest and shuts down the gRPC service.
     *
     * <p>The {@link #tearDown()} method of the super class will be invoked by JUnit automatically
     * after calling this method.
     */
    @AfterEach
    void shutDown() throws Exception {
        context.close();
    }

    /*
     * Tests
     ************/

    @Nested
    @DisplayName("dispatch")
    class Dispatch {

        @Test
        @DisplayName("event")
        void event() {
            var msg = projectCreated();

            // Ensure no instances are present in the repository now.
            assertFalse(repository().loadAll(ResponseFormat.getDefaultInstance())
                                    .hasNext());
            // And no instances of `TestProjection` processed the event message we are posting.
            assertTrue(TestProjection.whoProcessed(msg)
                                     .isEmpty());

            // Post an event message and grab the ID of the projection, which processed it.
            checkDispatchesEvent(msg);
            var projectIds = TestProjection.whoProcessed(msg);
            assertEquals(1, projectIds.size());
            var receiverId = projectIds.iterator()
                                       .next();

            // Check that the projection item has actually been stored and now can be loaded.
            var allItems =
                    repository().loadAll(ResponseFormat.getDefaultInstance());
            assertTrue(allItems.hasNext());
            var storedProjection = allItems.next();
            assertFalse(allItems.hasNext());

            // Check that the stored instance has the same ID as the instance handling the event.
            assertEquals(storedProjection.id(), receiverId);
        }

        @Test
        @DisplayName("several events")
        void severalEvents() {
            checkDispatchesEvent(projectCreated());
            checkDispatchesEvent(GivenEventMessage.taskAdded());
            checkDispatchesEvent(GivenEventMessage.projectStarted());
        }

        @Test
        @DisplayName("event to archived projection")
        void eventToArchived() {
            checkDispatchesEvent(projectCreated());
            var projectArchived = GivenEventMessage.projectArchived();
            checkDispatchesEvent(projectArchived);
            var projectId = projectArchived.getProjectId();
            var projection = repository().findOrCreate(projectId);
            assertTrue(projection.isArchived());

            // Dispatch an event to the archived projection.
            checkDispatchesEvent(GivenEventMessage.taskAdded());
            projection = repository().findOrCreate(projectId);
            var addedTasks = projection.state()
                                       .getTaskList();
            assertFalse(addedTasks.isEmpty());

            // Check that the projection was not re-created before dispatching.
            assertTrue(projection.isArchived());
        }

        @Test
        @DisplayName("event to deleted projection")
        void eventToDeleted() {
            checkDispatchesEvent(projectCreated());
            var projectDeleted = GivenEventMessage.projectDeleted();
            checkDispatchesEvent(projectDeleted);
            var projectId = projectDeleted.getProjectId();
            var projection = repository().findOrCreate(projectId);
            assertTrue(projection.isDeleted());

            // Dispatch an event to the deleted projection.
            checkDispatchesEvent(GivenEventMessage.taskAdded());
            projection = repository().findOrCreate(projectId);
            var addedTasks = projection.state()
                                       .getTaskList();
            assertTrue(projection.isDeleted());

            // Check that the projection was not re-created before dispatching.
            assertFalse(addedTasks.isEmpty());
        }

        @SuppressWarnings("OverlyCoupledMethod")
            // A complex test case with many test domain messages.
        @Test
        @DisplayName("entity state update")
        void entityState() throws Exception {
            var projectCreated = GivenEventMessage.projectCreated();
            var taskAdded = GivenEventMessage.taskAdded();
            var id = projectCreated.getProjectId();
            var eventFactory = TestEventFactory.newInstance(id, ProjectionRepositoryTest.class);
            var project = new TestProjection(id);
            dispatch(project, eventFactory.createEvent(projectCreated));
            var oldState = pack(project.state());
            dispatch(project, eventFactory.createEvent(taskAdded));
            var newState = pack(project.state());
            var entityId = MessageId.newBuilder()
                    .setTypeUrl(newState.getTypeUrl())
                    .setId(pack(id))
                    .build();
            var changedEvent = EntityStateChanged.newBuilder()
                    .setEntity(entityId)
                    .setWhen(currentTime())
                    .setOldState(oldState)
                    .setNewState(newState)
                    .addSignalId(dispatchedEventId())
                    .build();
            var repository = new EntitySubscriberProjection.Repository();
            var context = BoundedContextBuilder.assumingTests().build();
            context.internalAccess()
                   .register(repository);
            var envelope = EventEnvelope.of(eventFactory.createEvent(changedEvent));
            repository.dispatch(envelope);
            var expectedValue = ProjectTaskNames.newBuilder()
                    .setProjectId(id)
                    .setProjectName(projectCreated.getName())
                    .addTaskName(taskAdded.getTask()
                                          .getTitle())
                    .build();
            var projection = repository.find(id);
            assertTrue(projection.isPresent());
            assertEquals(expectedValue, projection.get().state());

            context.close();
        }

        private void checkDispatchesEvent(EventMessage eventMessage) {
            var eventFactory = newEventFactory(tenantId(), PRODUCER_ID);
            var event = eventFactory.createEvent(eventMessage);

            keepTenantIdFromEvent(context, event);

            dispatchEvent(event);
            assertTrue(TestProjection.processed(eventMessage));
        }
    }

    @Nested
    @DisplayName("not allow duplicate")
    @MuteLogging
    class AvoidDuplicates {

        private DiagnosticMonitor monitor;

        @BeforeEach
        void setUp() {
            monitor = new DiagnosticMonitor();
            context.internalAccess()
                   .registerEventDispatcher(monitor);
        }

        @Test
        @DisplayName("events")
        void events() {
            var msg = projectCreated();
            var eventFactory = newEventFactory(tenantId(), pack(msg.getProjectId()));
            var event = eventFactory.createEvent(msg);

            dispatchSuccessfully(event);
            dispatchDuplicate(event);
        }

        @Test
        @DisplayName("different events with same ID")
        void differentEventsWithSameId() {
            var created = projectCreated();
            var archived = GivenEventMessage.projectArchived();
            var id = created.getProjectId();
            var eventFactory = newEventFactory(tenantId(), pack(id));

            var firstEvent = eventFactory.createEvent(created);
            var secondEvent = eventFactory.createEvent(archived)
                                            .toBuilder()
                                            .setId(firstEvent.getId())
                                            .build();
            dispatchSuccessfully(firstEvent);
            dispatchDuplicate(secondEvent);
        }

        private void dispatchSuccessfully(Event event) {
            dispatchEvent(event);
            assertTrue(TestProjection.processed(event.enclosedMessage()));
            var events = monitor.duplicateEventEvents();
            assertThat(events).isEmpty();
        }

        private void dispatchDuplicate(Event event) {
            dispatchEvent(event);
            var events = monitor.duplicateEventEvents();
            assertThat(events).hasSize(1);
            var systemEvent = events.get(0);
            assertThat(systemEvent.getDuplicateEvent())
                    .isEqualTo(event.messageId());
        }
    }

    @SuppressWarnings("CheckReturnValue") // Can ignore dispatch() result in this test.
    private void dispatchEvent(Event event) {
        repository().dispatch(EventEnvelope.of(event));
    }

    @Test
    @DisplayName("emit `RoutingFailed` when dispatching unknown event")
    void emitRoutingFailedOnUnknownEvent() {
        var event = GivenEvent.arbitrary();
        var monitor = new DiagnosticMonitor();
        context.internalAccess()
               .registerEventDispatcher(monitor);

        dispatchEvent(event);

        var failures = monitor.routingFailures();
        assertThat(failures.size()).isEqualTo(1);
        var failure = failures.get(0);
        assertThat(failure.getError()
                          .getMessage()).contains(repository().idClass()
                                                              .getName());
    }

    @Nested
    @DisplayName("return")
    class Return {

        @Test
        @DisplayName("processed event classes")
        void eventClasses() {
            Set<EventClass> eventClasses = repository().messageClasses();
            assertEventClasses(eventClasses,
                               PrjProjectCreated.class,
                               PrjTaskAdded.class,
                               PrjProjectStarted.class);
        }

        @Test
        @DisplayName("entity storage")
        void entityStorage() {
            EntityRecordStorage<ProjectId, ?> recordStorage = repository().recordStorage();
            assertNotNull(recordStorage);
        }
    }

    @Test
    @DisplayName("convert `null` `Timestamp` to default")
    void convertNullTimestamp() {
        var timestamp = currentTime();
        assertThat(nullToDefault(timestamp)).isEqualTo(timestamp);
        assertThat(nullToDefault(null)).isEqualTo(Timestamp.getDefaultInstance());
    }

    @SuppressWarnings("CheckReturnValue") // can ignore dispatch() result in this test
    @Test
    @DisplayName("not create record if entity is not updated")
    void notCreateRecordForUnchanged() {
        var repo = new NoOpTaskNamesRepository();
        context.internalAccess()
               .register(repo);

        assertFalse(repo.loadAll(ResponseFormat.getDefaultInstance())
                        .hasNext());

        var event = createEvent(tenantId(), projectCreated(), currentTime());
        repo.dispatch(EventEnvelope.of(event));

        Iterator<?> items = repo.loadAll(ResponseFormat.getDefaultInstance());
        assertFalse(items.hasNext());
    }

    @Nested
    @DisplayName("provide package-private access to")
    class ExposeToPackage {

        /**
         * Ensures that {@link ProjectionRepository#eventStore()} which is used by the catch-up
         * functionality is exposed to the package.
         */
        @Test
        @DisplayName("event store")
        void eventStore() {
            ProjectionRepository<?, ?, ?> repository = repository();
            assertNotNull(repository.eventStore());
        }
    }

    @Test
    @DisplayName("check that its `Projection` class is subscribed to at least one message")
    void notRegisterIfSubscribedToNothing() {
        var repo = new SensoryDeprivedProjectionRepository();
        var context = BoundedContextBuilder.assumingTests().build();

        assertThrows(IllegalStateException.class, () ->
                repo.registerWith(context));
    }

    @Test
    @DisplayName("update entity via a custom migration")
    void performCustomMigration() {
        // Store a new process manager instance in the repository.
        var id = createId(42);
        var repository = repository();
        var projection = new TestProjection(id);
        repository.store(projection);

        // Init filters by the `id_string` column.
        var query =
                Project.query()
                       .idString().is(id.toString())
                       .build();

        // Check nothing is found as column now should be empty.
        var found = repository.find(query);
        assertThat(found.hasNext()).isFalse();

        // Apply the migration.
        repository.applyMigration(id, new SetTestProjectionId());

        // Check the entity is now found by the provided filters.
        var afterMigration = repository.find(query);
        assertThat(afterMigration.hasNext()).isTrue();

        // Check the new entity state has all fields updated as expected.
        var entityWithColumns = afterMigration.next();
        var expectedState = projection.state().toBuilder()
                .setIdString(projection.getIdString())
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
        var projection1 = new TestProjection(id1);
        var projection2 = new TestProjection(id2);
        var projection3 = new TestProjection(id3);
        repository.store(projection1);
        repository.store(projection2);
        repository.store(projection3);

        // Query by the `name` column.
        var query = queryByName(NEW_NAME);

        // Check nothing is found as the entity states were not yet updated.
        var found = repository.find(query);
        assertThat(found.hasNext()).isFalse();

        // Apply the column update to two of the three entities.
        repository.applyMigration(ImmutableSet.of(id1, id2), new SetTestProjectionName());

        // Check the entities are now found by the provided filters.
        var foundAfterMigration = repository.find(query);

        var results = ImmutableList.copyOf(foundAfterMigration);
        var expectedState1 = expectedState(projection1, NEW_NAME);
        var expectedState2 = expectedState(projection2, NEW_NAME);
        assertThat(results).hasSize(2);
        assertThat(results)
                .comparingElementsUsing(entityState())
                .containsExactly(expectedState1, expectedState2);
    }

    @Test
    @DisplayName("replace the state of the migrated entity")
    void replaceState() {
        var id = createId(42);
        var entity = new TestProjection(id);
        var repository = repository();
        repository.store(entity);
        repository.applyMigration(id, new RandomFillProjection());
        // Query by the `name` column.
        var byNewName = queryByName(NEW_NAME);

        // Ensure nothing found.
        var expectedEmpty = repository.find(byNewName);
        assertThat(expectedEmpty.hasNext()).isFalse();

        repository.applyMigration(id, new SetTestProjectionName());

        // Now we should have found a single instance.
        var shouldHaveOne = repository.find(byNewName);
        var expectedState = expectedState(entity, NEW_NAME);
        var actualList = ImmutableList.copyOf(shouldHaveOne);
        assertThat(actualList)
                .comparingElementsUsing(entityState())
                .containsExactly(expectedState);
    }

    @Test
    @DisplayName("update state through migration operation")
    void updateState() {
        // Store a new projection instance in the repository.
        var id = createId(42);
        var repository = repository();
        var projection = new TestProjection(id);
        repository.store(projection);

        // Init filters by the `id_string` column.
        var query =
                Project.query()
                       .idString().is(id.toString())
                       .build();

        // Check nothing is found as column now should be empty.
        var found = repository.find(query);
        assertThat(found.hasNext()).isFalse();

        // Apply the state update.
        repository.applyMigration(id, new UpdateProjectionState<>());

        // Check the entity is now found by the provided filters.
        var afterMigration = repository.find(query);
        assertThat(afterMigration.hasNext()).isTrue();

        // Check the column value is propagated to the entity state.
        var entityWithColumns = afterMigration.next();
        var expectedState = projection.state().toBuilder()
                .setIdString(projection.getIdString())
                .build();
        assertThat(entityWithColumns.state()).isEqualTo(expectedState);
    }

    @Test
    @DisplayName("update state of multiple entities")
    void updateStateForMultiple() {
        // Store three projections to the repository.
        var id1 = createId(1);
        var id2 = createId(2);
        var id3 = createId(3);
        var repository = repository();
        var projection1 = new TestProjection(id1);
        var projection2 = new TestProjection(id2);
        var projection3 = new TestProjection(id3);
        repository.store(projection1);
        repository.store(projection2);
        repository.store(projection3);

        // Apply the state update to two of the three entities.
        repository.applyMigration(ImmutableSet.of(id1, id2), new UpdateProjectionState<>());

        // Check that entities to which migration has been applied now have column values updated.
        var query =
                Project.query()
                       .either(p -> p.idString().is(id1.toString()),
                               p -> p.idString().is(id2.toString()),
                               p -> p.idString().is(id3.toString()))
                       .build();
        var found = repository.find(query);

        var results = ImmutableList.copyOf(found);
        var expectedState1 = projection1.state().toBuilder()
                .setIdString(projection1.getIdString())
                .build();
        var expectedState2 = projection2.state().toBuilder()
                .setIdString(projection2.getIdString())
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
        var projection = createEntity(id);
        repository().store(projection);

        repository().applyMigration(id, new MarkProjectionArchived<>());

        var found = repository().find(id);
        assertThat(found).isPresent();
        assertThat(found.get()
                        .isArchived()).isTrue();
    }

    @Test
    @DisplayName("delete entity via migration")
    void deleteEntityViaMigration() {
        var id = createId(42);
        var projection = createEntity(id);
        repository().store(projection);

        repository().applyMigration(id, new MarkProjectionDeleted<>());

        var found = repository().find(id);
        assertThat(found).isPresent();
        assertThat(found.get()
                        .isDeleted()).isTrue();
    }

    @Test
    @DisplayName("remove entity record via migration")
    void removeRecordViaMigration() {
        var id = createId(42);
        var projection = createEntity(id);
        repository().store(projection);

        repository().applyMigration(id, new RemoveProjectionFromStorage<>());

        var found = repository().find(id);
        assertThat(found).isEmpty();
    }

    private static Project.Query queryByName(String value) {
        return Project.query()
                      .name().is(value)
                      .build();
    }

    private static Correspondence<TestProjection, Project> entityState() {
        return Correspondence.from(ProjectionRepositoryTest::hasState, "has state");
    }

    private static boolean hasState(TestProjection actual, Project expected) {
        return actual.state().equals(expected);
    }

    private static Project expectedState(TestProjection entity, String newName) {
        return entity.state().toBuilder()
                .setId(entity.id())
                .setName(newName)
                .setIdString(entity.getIdString())
                .build();
    }
}
