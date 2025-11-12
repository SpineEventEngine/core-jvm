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

package io.spine.server.projection.e2e;

import io.spine.base.Time;
import io.spine.client.ResponseFormat;
import io.spine.core.ActorContext;
import io.spine.core.Event;
import io.spine.core.EventContext;
import io.spine.core.Events;
import io.spine.core.MessageId;
import io.spine.core.UserId;
import io.spine.protobuf.TypeConverter;
import io.spine.server.BoundedContextBuilder;
import io.spine.server.ServerEnvironment;
import io.spine.server.given.groups.GroupId;
import io.spine.server.given.groups.GroupName;
import io.spine.server.given.groups.GroupNameProjection;
import io.spine.server.given.groups.GroupProjection;
import io.spine.server.given.organizations.Organization;
import io.spine.server.given.organizations.OrganizationId;
import io.spine.server.given.organizations.OrganizationProjection;
import io.spine.server.projection.given.EntitySubscriberProjection;
import io.spine.server.projection.given.ProjectionRepositoryTestEnv.GivenEventMessage;
import io.spine.server.projection.given.TestProjection;
import io.spine.server.type.EventEnvelope;
import io.spine.system.server.event.EntityStateChanged;
import io.spine.test.projection.ProjectTaskNames;
import io.spine.testing.core.given.GivenUserId;
import io.spine.testing.server.blackbox.BlackBox;
import io.spine.type.TypeUrl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.base.Time.currentTime;
import static io.spine.protobuf.AnyPacker.pack;
import static io.spine.server.projection.given.ProjectionRepositoryTestEnv.dispatchedEventId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("In end-to-end usage scenario, `Projection` should")
@SuppressWarnings("WeakerAccess" /* Open for re-using in descendant Spine libraries.*/)
public class ProjectionEndToEndTest {

    @AfterEach
    protected void tearDown() {
        ServerEnvironment.instance()
                         .reset();
    }

    @Test
    @DisplayName("receive entity state updates from other entities")
    void receiveUpdates() {
        var created = GivenEventMessage.projectCreated();
        var firstTaskAdded = GivenEventMessage.taskAdded();
        var secondTaskAdded = GivenEventMessage.taskAdded();
        var producerId = created.getProjectId();
        try (var context = BlackBox.singleTenantWith(
                new EntitySubscriberProjection.Repository(),
                new TestProjection.Repository())
        ) {
            context.receivesEventsProducedBy(producerId,
                                             created,
                                             firstTaskAdded,
                                             secondTaskAdded);
            context.assertState(
                    producerId,
                    ProjectTaskNames.newBuilder()
                            .setProjectId(producerId)
                            .setProjectName(created.getName())
                            .addTaskName(firstTaskAdded.getTask()
                                                       .getTitle())
                            .addTaskName(secondTaskAdded.getTask()
                                                        .getTitle())
                            .build()
            );
        }
    }

    @Test
    @DisplayName("receive entity state updates of entities of other context")
    void receiveExternal() {
        var established = GivenEventMessage.organizationEstablished();

        var sender = BlackBox.singleTenant("Organizations", new OrganizationProjection.Repository());
        var receiver = BlackBox.singleTenant("Groups", new GroupNameProjection.Repository());

        var producerId = established.getId();
        sender.receivesEventsProducedBy(producerId, established);
        var groupId = GroupId.newBuilder()
                .setUuid(producerId.getUuid())
                .build();
        receiver.assertEntityWithState(groupId, GroupName.class)
                .hasStateThat()
                .isEqualTo(GroupName.newBuilder()
                                    .setId(groupId)
                                    .setName(established.getName())
                                    .build());
    }

    @Test
    @DisplayName("receive entity state updates along with system event context")
    void receiveEntityStateUpdatesAndEventContext() {
        var repository = new GroupProjection.Repository();
        var groups = BoundedContextBuilder.assumingTests().build();
        groups.internalAccess()
              .register(repository);
        var organizationHead = GivenUserId.newUuid();
        var entityId = MessageId.newBuilder()
                .setTypeUrl(TypeUrl.of(Organization.class).value())
                .setId(pack(organizationHead))
                .build();
        var organizationName = "Contributors";
        var stateBuilder = Organization.newBuilder()
                .setId(OrganizationId.generate())
                .setHead(organizationHead)
                .setName(organizationName)
                .addMember(UserId.getDefaultInstance());
        var newState = stateBuilder.build();
        var oldState = stateBuilder.setName("Old " + stateBuilder.getName()).build();
        var stateChanged = EntityStateChanged.newBuilder()
                .setEntity(entityId)
                .setOldState(pack(oldState))
                .setNewState(pack(newState))
                .setWhen(currentTime())
                .addSignalId(dispatchedEventId())
                .build();
        var producedAt = Time.currentTime();
        var eventContext = EventContext.newBuilder()
                .setTimestamp(producedAt)
                .setExternal(true)
                .setImportContext(ActorContext.getDefaultInstance())
                .setProducerId(TypeConverter.toAny(GivenUserId.newUuid()))
                .build();
        var event = Event.newBuilder()
                .setId(Events.generateId())
                .setMessage(pack(stateChanged))
                .setContext(eventContext)
                .build();
        repository.dispatch(EventEnvelope.of(event));

        var allGroups =
                repository.loadAll(ResponseFormat.getDefaultInstance());
        assertTrue(allGroups.hasNext());
        var singleGroup = allGroups.next();
        assertFalse(allGroups.hasNext());

        var actualGroup = singleGroup.state();
        assertEquals(actualGroup.getName(), organizationName + producedAt);
        var assertParticipants = assertThat(actualGroup.getParticipantList());
        assertParticipants.containsAtLeastElementsIn(newState.getMemberList());
        assertParticipants.contains(organizationHead);

        groups.close();
    }
}
