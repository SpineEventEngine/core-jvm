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

package io.spine.server.entity;

import com.google.protobuf.Empty;
import com.google.protobuf.Message;
import io.spine.base.Time;
import io.spine.core.EventId;
import io.spine.core.MessageId;
import io.spine.core.UserId;
import io.spine.server.Application;
import io.spine.server.BoundedContext;
import io.spine.server.ServerEnvironment;
import io.spine.server.event.model.InsufficientVisibilityError;
import io.spine.server.given.groups.FilteredStateSubscriber;
import io.spine.server.given.groups.Group;
import io.spine.server.given.groups.GroupId;
import io.spine.server.given.groups.HiddenEntitySubscriber;
import io.spine.server.given.groups.TestSubscriber;
import io.spine.server.given.groups.WronglyDomesticSubscriber;
import io.spine.server.given.groups.WronglyExternalSubscriber;
import io.spine.server.given.organizations.Organization;
import io.spine.server.given.organizations.OrganizationId;
import io.spine.server.model.ModelError;
import io.spine.server.model.SignalOriginMismatchError;
import io.spine.server.type.given.GivenEvent;
import io.spine.system.server.SystemBoundedContexts;
import io.spine.system.server.event.EntityStateChanged;
import io.spine.type.TypeUrl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.protobuf.AnyPacker.pack;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("`AbstractEventSubscriber` should")
class AbstractEventSubscriberTest {

    private TestSubscriber subscriber;
    private Application application;
    private BoundedContext groupsContext;
    private BoundedContext organizationsContext;

    /**
     * Builds the contexts of the groups and of the organizations in one application,
     * so that they exchange external messages through its transport.
     */
    @BeforeEach
    void setUp() {
        application = Application.newBuilder()
                .setServerEnvironment(ServerEnvironment.newBuilder()
                                                       .build())
                .add(BoundedContext.singleTenant("Groups"))
                .add(BoundedContext.singleTenant("Organizations"))
                .build();
        groupsContext = application.contexts()
                                   .get(0);
        organizationsContext = application.contexts()
                                          .get(1);
        subscriber = new TestSubscriber();
        groupsContext.internalAccess()
                     .registerEventDispatcher(subscriber);
    }

    @AfterEach
    void tearDown() {
        application.close();
    }

    @Test
    @DisplayName("receive domestic entity state updates")
    void receiveEntityStateUpdates() {
        var builder = Group.newBuilder()
                .setId(GroupId.generate())
                .setName("Admins")
                .addParticipant(UserId.getDefaultInstance())
                .addParticipant(UserId.getDefaultInstance());
        var newState = builder.build();
        var oldState = builder.setName("Old " + builder.getName()).build();
        var event = EntityStateChanged.newBuilder()
                .setEntity(messageIdWithType(Group.class))
                .setWhen(Time.currentTime())
                .setOldState(pack(oldState))
                .setNewState(pack(newState))
                .addSignalId(emptyEventId())
                .build();
        SystemBoundedContexts.systemOf(groupsContext)
                             .eventBus()
                             .post(GivenEvent.withMessage(event));
        var receivedState = subscriber.domestic();
        assertTrue(receivedState.isPresent());
        assertThat(receivedState)
                .hasValue(newState);
        assertThat(subscriber.external())
                .isEmpty();
    }

    @Test
    @DisplayName("receive external entity state updates")
    void receiveExternalEntityStateUpdates() {
        var builder = Organization.newBuilder()
                .setId(OrganizationId.generate())
                .setName("Developers")
                .setHead(UserId.getDefaultInstance())
                .addMember(UserId.getDefaultInstance())
                .addMember(UserId.getDefaultInstance());
        var newState = builder.build();
        var oldState = builder.setName("Old " + builder.getName()).build();
        var event = EntityStateChanged.newBuilder()
                .setEntity(messageIdWithType(Organization.class))
                .setWhen(Time.currentTime())
                .setOldState(pack(oldState))
                .setNewState(pack(newState))
                .addSignalId(emptyEventId())
                .build();
        SystemBoundedContexts.systemOf(organizationsContext)
                             .eventBus()
                             .post(GivenEvent.withMessage(event));
        var receivedState = subscriber.external();
        assertThat(receivedState)
                .hasValue(newState);
        assertThat(subscriber.domestic())
                .isEmpty();
    }

    @Test
    @DisplayName("fail to subscribe to external events without `external = true`")
    void failIfNotExternal() {
        assertThrows(SignalOriginMismatchError.class, WronglyDomesticSubscriber::new);
    }

    @Test
    @DisplayName("fail to subscribe to domestic events without `external = false`")
    void failIfNotDomestic() {
        assertThrows(SignalOriginMismatchError.class, WronglyExternalSubscriber::new);
    }

    @Test
    @DisplayName("fail to subscribe to entity states with filters")
    void failToSubscribeToStateWithFilters() {
        assertThrows(ModelError.class, FilteredStateSubscriber::new);
    }

    @Test
    @DisplayName("fail to subscribe to entity states with insufficient visibility level")
    void failOnInsufficientVisibility() {
        assertThrows(InsufficientVisibilityError.class, HiddenEntitySubscriber::new);
    }

    private static MessageId messageIdWithType(Class<? extends Message> type) {
        var historyId = MessageId.newBuilder()
                .setId(pack(Empty.getDefaultInstance()))
                .setTypeUrl(TypeUrl.of(type).value())
                .build();
        return historyId;
    }

    private static MessageId emptyEventId() {
        return MessageId.newBuilder()
                .setId(pack(EventId.getDefaultInstance()))
                .setTypeUrl("example.org/test.Event")
                .build();
    }
}
