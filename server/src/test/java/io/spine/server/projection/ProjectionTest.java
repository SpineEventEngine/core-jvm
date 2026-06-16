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

package io.spine.server.projection;

import com.google.common.collect.ImmutableList;
import io.spine.base.Identifier;
import io.spine.core.EventId;
import io.spine.core.EventValidationError;
import io.spine.core.MessageId;
import io.spine.core.Versions;
import io.spine.server.BoundedContextBuilder;
import io.spine.server.DefaultRepository;
import io.spine.server.entity.given.Given;
import io.spine.server.projection.given.EntitySubscriberProjection;
import io.spine.server.projection.given.NoDefaultOptionProjection;
import io.spine.server.projection.given.SavedString;
import io.spine.server.projection.given.SavingProjection;
import io.spine.server.type.EventEnvelope;
import io.spine.server.type.given.GivenEvent;
import io.spine.system.server.DiagnosticMonitor;
import io.spine.system.server.event.EntityStateChanged;
import io.spine.test.projection.Project;
import io.spine.test.projection.ProjectId;
import io.spine.test.projection.ProjectTaskNames;
import io.spine.test.projection.Task;
import io.spine.test.projection.TaskId;
import io.spine.test.projection.event.Int32Imported;
import io.spine.test.projection.event.StringImported;
import io.spine.testing.TestValues;
import io.spine.testing.server.TestEventFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static io.spine.base.Identifier.newUuid;
import static io.spine.base.Time.currentTime;
import static io.spine.core.EventValidationError.UNSUPPORTED_EVENT_VALUE;
import static io.spine.protobuf.AnyPacker.pack;
import static io.spine.server.projection.given.NoDefaultOptionProjection.ACCEPTED_VALUE;
import static io.spine.server.projection.given.dispatch.ProjectionEventDispatcher.dispatch;
import static io.spine.server.type.given.GivenEvent.withMessage;
import static io.spine.test.projection.Project.Status.STARTED;
import static io.spine.testing.TestValues.random;
import static io.spine.testing.TestValues.randomString;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link io.spine.server.projection.Projection}.
 */
@DisplayName("`Projection` should")
class ProjectionTest {

    private static final TestEventFactory eventFactory =
            TestEventFactory.newInstance(ProjectionTest.class);

    private SavingProjection projection;

    @BeforeEach
    void setUp() {
        var id = newUuid();
        projection = Given.projectionOfClass(SavingProjection.class)
                          .withId(id)
                          .withVersion(1)
                          .withState(SavedString.newBuilder()
                                                .setId(id)
                                                .setValue("Initial state")
                                                .build())
                          .build();
    }

    @Test
    @DisplayName("handle events")
    void handleEvents() {
        var stringEvent = StringImported.newBuilder()
                .setValue(newUuid())
                .build();
        var strEvt = eventFactory.createEvent(stringEvent);
        dispatch(projection, stringEvent, strEvt.context());
        assertTrue(projection.changed());
        assertThat(projection.state().getValue())
                .contains(stringEvent.getValue());

        var integerEvent = Int32Imported.newBuilder()
                .setValue(42)
                .build();
        var intEvt = eventFactory.createEvent(integerEvent);
        dispatch(projection, integerEvent, intEvt.context());
        assertTrue(projection.changed());
        assertThat(projection.state().getValue())
                .contains(String.valueOf(integerEvent.getValue()));
    }

    @Test
    @DisplayName("receive entity state updates")
    void handleStateUpdates() {
        var id = newId();
        var taskId = TaskId.newBuilder()
                .setId(TestValues.random(1, 1_000))
                .build();
        var task = Task.newBuilder()
                .setTaskId(taskId)
                .setTitle("test task " + random(42))
                .build();
        var projectName = "test project name " + randomString();
        var stateBuilder = Project.newBuilder()
                .setId(id)
                .setName(projectName)
                .setStatus(STARTED)
                .addTask(task);
        var aggregateState = stateBuilder.build();
        var previousAggState = stateBuilder.setName("Old " + stateBuilder.getName()).build();
        var entityId = MessageId.newBuilder()
                .setTypeUrl(aggregateState.typeUrl().value())
                .setId(pack(id))
                .setVersion(Versions.zero())
                .build();
        var eventId = EventId.newBuilder()
                .setValue(newUuid())
                .build();
        var systemEvent = EntityStateChanged.newBuilder()
                .setEntity(entityId)
                .setOldState(pack(previousAggState))
                .setNewState(pack(aggregateState))
                .setWhen(currentTime())
                .addSignalId(MessageId.newBuilder()
                                      .setId(pack(eventId))
                                      .setTypeUrl("example.org/example.test.Event"))
                .build();
        var projection = new EntitySubscriberProjection(id);
        dispatch(projection, withMessage(systemEvent));
        assertThat(projection.state())
                .isEqualTo(ProjectTaskNames.newBuilder()
                                   .setProjectId(id)
                                   .setProjectName(projectName)
                                   .addTaskName(task.getTitle())
                                   .build());
    }

    @Test
    @DisplayName("dispatch unexpected handler failure system rejection " +
            "with `UNSUPPORTED_EVENT` cause")
    void dispatchUnsupportedEventFailure() {
        @SuppressWarnings({"unchecked", "RedundantSuppression"})
        var repository = (ProjectionRepository<String, SavingProjection, SavedString>)
                DefaultRepository.of(SavingProjection.class);
        var context = BoundedContextBuilder.assumingTests().build();
        final var contextAccess = context.internalAccess();
        contextAccess.register(repository);
        var monitor = new DiagnosticMonitor();
        contextAccess.registerEventDispatcher(monitor);
        var event = GivenEvent.arbitrary();
        var envelope = EventEnvelope.of(event);
        var endpoint = ProjectionEndpoint.of(repository, envelope);

        endpoint.dispatchTo(projection.id());

        var systemEvents = monitor.handlerFailureEvents();
        assertThat(systemEvents)
                .hasSize(1);
        var systemEvent = systemEvents.get(0);
        assertThat(systemEvent.getHandledSignal().asEventId())
             .isEqualTo(event.id());
        assertThat(systemEvent.getEntity().getId())
             .isEqualTo(Identifier.pack(projection.id()));
        var error = systemEvent.getError();
        assertThat(error.getType())
                .isEqualTo(EventValidationError.getDescriptor().getFullName());
        assertThat(error.getCode())
                .isEqualTo(UNSUPPORTED_EVENT_VALUE);
    }

    @Test
    @DisplayName("expose `play events` operation to package")
    void exposePlayingEvents() {
        var stringImported = StringImported.newBuilder()
                .setValue("eins zwei drei")
                .build();
        var integerImported = Int32Imported.newBuilder()
                .setValue(123)
                .build();
        var nextVersion = Versions.increment(projection.version());
        var e1 = eventFactory.createEvent(stringImported, nextVersion);
        var e2 = eventFactory.createEvent(integerImported, Versions.increment(nextVersion));

        var projectionChanged = Projection.playOn(projection, ImmutableList.of(e1, e2));
        assertTrue(projectionChanged);

        var projectionState = projection.state().getValue();
        var state = assertThat(projectionState);
        state.contains(stringImported.getValue());
        state.contains(String.valueOf(integerImported.getValue()));
    }

    @Test
    @DisplayName("not dispatch event if it does not match filters")
    void notDeliverIfNotFits() {
        var projection =
                Given.projectionOfClass(NoDefaultOptionProjection.class)
                     .withId(newUuid())
                     .build();
        var skipped = StringImported.newBuilder()
                .setValue("BBB")
                .build();
        dispatch(projection, eventFactory.createEvent(skipped));
        assertThat(projection.state())
                // Ignore the difference in the ID field of the state that
                // was set automatically by the tx.
                .comparingExpectedFieldsOnly()
                .isEqualTo(SavedString.getDefaultInstance());

        var dispatched = StringImported.newBuilder()
                .setValue(ACCEPTED_VALUE)
                .build();
        dispatch(projection, eventFactory.createEvent(dispatched));
        assertThat(projection.state().getValue())
                .isEqualTo(ACCEPTED_VALUE);
    }

    private static ProjectId newId() {
        return ProjectId.newBuilder()
                .setId(newUuid())
                .build();
    }
}
