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

package io.spine.server.entity;

import com.google.protobuf.Empty;
import com.google.protobuf.Message;
import io.spine.base.Time;
import io.spine.core.EventId;
import io.spine.core.MessageId;
import io.spine.core.UserId;
import io.spine.server.BoundedContext;
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
    private BoundedContext groupsContext;
    private BoundedContext organizationsContext;

    @BeforeEach
    void setUp() {
        groupsContext = BoundedContext.singleTenant("Groups").build();
        organizationsContext = BoundedContext.singleTenant("Organizations").build();
        subscriber = new TestSubscriber();
        groupsContext.internalAccess()
                     .registerEventDispatcher(subscriber);
    }

    @AfterEach
    void tearDown() throws Exception {
        groupsContext.close();
        organizationsContext.close();
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
