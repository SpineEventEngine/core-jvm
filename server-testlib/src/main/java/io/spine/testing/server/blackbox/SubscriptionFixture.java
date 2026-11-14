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

package io.spine.testing.server.blackbox;

import com.google.common.truth.extensions.proto.IterableOfProtosSubject;
import io.grpc.stub.StreamObserver;
import io.spine.base.EntityState;
import io.spine.base.EventMessage;
import io.spine.client.Subscription;
import io.spine.client.SubscriptionUpdate;
import io.spine.client.Topic;
import io.spine.server.Application;
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
 * Allows asserting updates received on a subscription.
 */
public final class SubscriptionFixture {

    private final Application application;
    private final Topic topic;
    private final List<SubscriptionUpdate> updates = synchronizedList(new ArrayList<>());

    SubscriptionFixture(Application application, Topic topic) {
        this.application = checkNotNull(application);
        this.topic = checkNotNull(topic);
    }

    /**
     * Creates and activates a subscription on the topic.
     */
    void activate() {
        var service = SubscriptionService.of(application);
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
