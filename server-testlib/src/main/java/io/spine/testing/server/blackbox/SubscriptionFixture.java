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

package io.spine.testing.server.blackbox;

import com.google.common.truth.extensions.proto.IterableOfProtosSubject;
import io.grpc.stub.StreamObserver;
import io.spine.base.EntityState;
import io.spine.base.EventMessage;
import io.spine.client.Subscription;
import io.spine.client.SubscriptionUpdate;
import io.spine.client.Topic;
import io.spine.server.BoundedContext;
import io.spine.server.SubscriptionService;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static io.spine.client.SubscriptionUpdate.UpdateCase.ENTITY_UPDATES;
import static io.spine.client.SubscriptionUpdate.UpdateCase.EVENT_UPDATES;
import static java.util.Collections.synchronizedList;

/**
 * Allows to assert updates received on a subscription.
 */
public final class SubscriptionFixture {

    private final BoundedContext context;
    private final Topic topic;
    private final List<SubscriptionUpdate> updates = synchronizedList(new ArrayList<>());

    SubscriptionFixture(BoundedContext context, Topic topic) {
        this.context = checkNotNull(context);
        this.topic = checkNotNull(topic);
    }

    /**
     * Creates and activates a subscription on the topic.
     */
    void activate() {
        var service = SubscriptionService.newBuilder()
                .add(context)
                .build();
        var updateObserver = new Observer(updates::add);
        StreamObserver<Subscription> activator = new Activator(service, updateObserver);
        service.subscribe(topic, activator);
    }

    /**
     * Obtains a subject for asserting event messages received on the subscribed topic.
     *
     * <p>If messages of other kind were received instead, the returned iterable would be empty.
     */
    public IterableOfProtosSubject<EventMessage> assertEventMessages() {
        var eventMessages = updates.stream()
                .flatMap(SubscriptionFixture::toEventMessages)
                .collect(toImmutableList());
        var subject = assertThat(eventMessages);
        return subject;
    }

    /**
     * Obtains a subject for asserting entity states received on the subscribed topic.
     *
     * <p>If messages of other kind were received instead, the returned iterable would be empty.
     */
    public IterableOfProtosSubject<EntityState<?>> assertEntityStates() {
        var states = updates.stream()
                .flatMap(SubscriptionFixture::toEntityState)
                .collect(toImmutableList());
        var subject = assertThat(states);
        return subject;
    }

    private static Stream<EventMessage> toEventMessages(SubscriptionUpdate update) {
        if (update.getUpdateCase() == EVENT_UPDATES) {
            return update.eventMessages().stream();
        }
        return Stream.empty();
    }

    private static Stream<EntityState<?>> toEntityState(SubscriptionUpdate update) {
        if (update.getUpdateCase() == ENTITY_UPDATES) {
            return update.states().stream();
        }
        return Stream.empty();
    }

    /**
     * Counts the incoming subscription updates and feeds them to the given {@link Consumer}.
     *
     * <p>Re-throws all incoming errors as {@link IllegalStateException}.
     */
    private static final class Observer implements StreamObserver<SubscriptionUpdate> {

        private final Consumer<SubscriptionUpdate> consumer;

        private Observer(Consumer<SubscriptionUpdate> consumer) {
            this.consumer = checkNotNull(consumer);
        }

        @Override
        public void onNext(SubscriptionUpdate update) {
            consumer.accept(update);
        }

        @Override
        public void onError(Throwable t) {
            throw new IllegalStateException(t);
        }

        @Override
        public void onCompleted() {
            // Do nothing.
        }
    }

    /**
     * Activates every {@link Subscription} it receives.
     *
     * <p>The purpose of this class is to be used as an argument for the call to
     * {@link SubscriptionService#subscribe(Topic, StreamObserver)}.
     *
     * <p>Re-throws all incoming errors as {@link IllegalStateException}.
     */
    static final class Activator implements StreamObserver<Subscription> {

        private final SubscriptionService service;
        private final StreamObserver<SubscriptionUpdate> observer;

        private Activator(SubscriptionService service,
                          StreamObserver<SubscriptionUpdate> observer) {
            this.service = checkNotNull(service);
            this.observer = checkNotNull(observer);
        }

        @Override
        public void onNext(Subscription subscription) {
            service.activate(subscription, observer);
        }

        @Override
        public void onError(Throwable t) {
            throw new IllegalStateException(t);
        }

        @Override
        public void onCompleted() {
            // Do nothing.
        }
    }
}
