/*
 * Copyright 2025, TeamDev. All rights reserved.
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

package io.spine.server.stand.given;

import io.spine.base.Identifier;
import io.spine.base.Time;
import io.spine.client.Subscription;
import io.spine.client.Subscriptions;
import io.spine.client.Target;
import io.spine.client.Targets;
import io.spine.client.Topic;
import io.spine.client.TopicId;
import io.spine.core.ActorContext;
import io.spine.core.Event;
import io.spine.core.EventId;
import io.spine.core.MessageId;
import io.spine.protobuf.AnyPacker;
import io.spine.protobuf.TypeConverter;
import io.spine.server.type.EventEnvelope;
import io.spine.server.type.given.GivenEvent;
import io.spine.system.server.event.EntityStateChanged;
import io.spine.test.aggregate.AggProject;
import io.spine.test.aggregate.ProjectId;
import io.spine.test.commandservice.customer.Customer;
import io.spine.test.event.ProjectCreated;
import io.spine.testing.core.given.GivenUserId;
import io.spine.time.ZoneIds;
import io.spine.type.TypeUrl;

import java.util.Collections;

import static io.spine.base.Identifier.newUuid;

public final class SubscriptionRecordTestEnv {

    public static final TypeUrl TYPE = TypeUrl.of(AggProject.class);
    public static final TypeUrl OTHER_TYPE = TypeUrl.of(Customer.class);

    /** Prevents instantiation of this utility class. */
    private SubscriptionRecordTestEnv() {
    }

    public static EventEnvelope
    stateChangedEnvelope(ProjectId id, AggProject oldState, AggProject newState) {
        return stateChangedEnvelope(id, oldState, newState, TYPE);
    }

    public static EventEnvelope
    stateChangedEnvelope(ProjectId id, AggProject oldState, AggProject newState, TypeUrl type) {
        var eventMessage = entityStateChanged(id, oldState, newState, type);
        var packedMessage = TypeConverter.toAny(eventMessage);
        var event = Event.newBuilder()
                .setId(GivenEvent.someId())
                .setMessage(packedMessage)
                .setContext(GivenEvent.context())
                .build();
        var result = EventEnvelope.of(event);
        return result;
    }

    private static EntityStateChanged
    entityStateChanged(ProjectId id, AggProject oldState, AggProject newState, TypeUrl type) {
        var packedId = Identifier.pack(id);
        var entityId = MessageId.newBuilder()
                .setTypeUrl(type.value())
                .setId(packedId)
                .build();
        var packedOldState = TypeConverter.toAny(oldState);
        var packedNewState = TypeConverter.toAny(newState);
        var result = EntityStateChanged.newBuilder()
                .setEntity(entityId)
                .addSignalId(GivenEvent.arbitrary().messageId())
                .setOldState(packedOldState)
                .setNewState(packedNewState)
                .build();
        return result;
    }

    public static EventEnvelope projectCreatedEnvelope(EventId eventId) {
        //TODO:2022-10-15:alexander.yevsyukov: Fix usage of the type from another package.
        var project = io.spine.test.event.ProjectId.newBuilder()
                .setId(Identifier.newUuid())
                .build();
        var eventMessage = ProjectCreated.newBuilder()
                .setProjectId(project)
                .build();
        return projectCreatedEnvelope(eventId, eventMessage);
    }

    public static EventEnvelope
    projectCreatedEnvelope(EventId eventId, ProjectCreated eventMessage) {
        var packedMessage = AnyPacker.pack(eventMessage);
        var event = Event.newBuilder()
                .setId(eventId)
                .setMessage(packedMessage)
                .setContext(GivenEvent.context())
                .build();
        var result = EventEnvelope.of(event);
        return result;
    }

    public static Subscription subscription() {
        var target = target();
        var subscription = subscription(target);
        return subscription;
    }

    public static Subscription subscription(ProjectId targetId) {
        var target = target(targetId);
        var subscription = subscription(target);
        return subscription;
    }

    public static Subscription subscription(Target target) {
        var context = ActorContext.newBuilder()
                .setTimestamp(Time.currentTime())
                .setActor(GivenUserId.newUuid())
                .setZoneId(ZoneIds.systemDefault())
                .build();
        var topic = Topic.newBuilder()
                .setId(TopicId.newBuilder().setValue(newUuid()))
                .setTarget(target)
                .setContext(context)
                .build();
        var result = Subscription.newBuilder()
                .setId(Subscriptions.generateId())
                .setTopic(topic)
                .build();
        return result;
    }

    private static Target target() {
        var target = Targets.allOf(AggProject.class);
        return target;
    }

    private static Target target(ProjectId targetId) {
        var target = Targets.someOf(AggProject.class, Collections.singleton(targetId));
        return target;
    }


    public static AggProject projectWithName(String name) {
        return AggProject.newBuilder()
                .setId(projectId(newUuid()))
                .setName(name)
                .build();
    }

    public static io.spine.test.aggregate.ProjectId projectId(String id) {
        return ProjectId.newBuilder()
                .setUuid(id)
                .build();
    }

    public static io.spine.test.event.ProjectId subscriptionProjectId(String value) {
        return io.spine.test.event.ProjectId.newBuilder()
                .setId(value)
                .build();
    }
}
