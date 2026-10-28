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

        try (var sender = BlackBox.singleTenant("Organizations",
                                                new OrganizationProjection.Repository());
             var receiver = BlackBox.singleTenant("Groups",
                                                  new GroupNameProjection.Repository())) {
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
    }

    @Test
    @DisplayName("receive entity state updates along with system event context")
    void receiveEntityStateUpdatesAndEventContext() {
        var repository = new GroupProjection.Repository();
        try (var groups = BoundedContextBuilder.assumingTests().build()) {
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
        }
    }
}
