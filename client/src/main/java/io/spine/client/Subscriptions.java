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

package io.spine.client;

import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.Message;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import io.spine.annotation.VisibleForTesting;
import io.spine.base.Identifier;
import io.spine.client.grpc.SubscriptionServiceGrpc;
import io.spine.client.grpc.SubscriptionServiceGrpc.SubscriptionServiceBlockingStub;
import io.spine.client.grpc.SubscriptionServiceGrpc.SubscriptionServiceStub;
import io.spine.core.Response;
import io.spine.logging.Logger;
import io.spine.logging.WithLogging;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.type.ProtoTexts.shortDebugString;
import static java.lang.String.format;
import static java.util.Collections.synchronizedSet;

/**
 * Maintains the list of the <strong>active</strong> subscriptions created by the {@link Client}.
 *
 * <p>Subscriptions are created when a client application:
 * <ul>
 *     <li>{@linkplain ClientRequest#subscribeToEvent(Class) subscribes to events};
 *     <li>{@linkplain ClientRequest#subscribeTo(Class) subscribes to entity states};
 *     <li>{@linkplain CommandRequest#post() posts a command}.
 * </ul>
 *
 * <p>The subscriptions should be {@linkplain Subscriptions#cancel(Subscription) cancelled} after
 * an update is delivered to the client and no further updates are expected.
 *
 * <p>All remaining subscriptions are {@linkplain #cancelAll() cancelled} by the {@code Client}
 * when it {@linkplain Client#close() closes}.
 *
 * @see ClientRequest#subscribeTo(Class)
 * @see ClientRequest#subscribeToEvent(Class)
 * @see CommandRequest#post()
 */
public final class Subscriptions implements WithLogging {

    private final SubscriptionServiceStub service;
    private final SubscriptionServiceBlockingStub blockingServiceStub;
    private final Set<Subscription> items;
    private final @Nullable ErrorHandler streamingErrorHandler;
    private final @Nullable ServerErrorHandler serverErrorHandler;

    Subscriptions(ManagedChannel channel,
                  @Nullable ErrorHandler streamingErrorHandler,
                  @Nullable ServerErrorHandler serverErrorHandler) {
        this.service = SubscriptionServiceGrpc.newStub(channel);
        this.blockingServiceStub = SubscriptionServiceGrpc.newBlockingStub(channel);
        this.streamingErrorHandler = streamingErrorHandler;
        this.serverErrorHandler = serverErrorHandler;
        this.items = synchronizedSet(new HashSet<>());
    }

    /**
     * Generates a new subscription identifier.
     *
     * <p>The result is based upon UUID generation.
     *
     * @return new subscription identifier.
     */
    public static SubscriptionId generateId() {
        var formattedId = format("s-%s", Identifier.newUuid());
        return newId(formattedId);
    }

    /**
     * Wraps a given {@code String} as a subscription identifier.
     *
     * <p>Should not be used in production. Use {@linkplain #generateId() automatic generation}
     * instead.
     *
     * @return new subscription identifier.
     */
    public static SubscriptionId newId(String value) {
        return SubscriptionId.newBuilder()
                             .setValue(value)
                             .build();
    }

    /**
     * Creates a new subscription with the given {@link Topic} and a random ID.
     *
     * @param topic
     *         subscription topic
     * @return new subscription
     */
    public static Subscription from(Topic topic) {
        checkNotNull(topic);

        var id = generateId();
        var subscription = Subscription.newBuilder()
                .setId(id)
                .setTopic(topic)
                .build();
        return subscription;
    }

    /**
     * Subscribes the given {@link StreamObserver} to the given topic and activates
     * the subscription.
     *
     * @param topic
     *         the topic to subscribe to
     * @param observer
     *         the observer to subscribe
     * @param <M>
     *         the type of the result messages
     * @return the activated subscription
     * @see #cancel(Subscription)
     */
    <M extends Message> Subscription subscribeTo(Topic topic, StreamObserver<M> observer) {
        var subscription = blockingServiceStub.subscribe(topic);
        service.activate(subscription, new SubscriptionObserver<>(observer));
        add(subscription);
        return subscription;
    }

    <M extends Message> Subscription subscribeTo(Topic topic,
                                                 StreamObserver<M> observer,
                                                 StreamObserver<SubscriptionUpdate> chain) {
        var subscription = blockingServiceStub.subscribe(topic);
        service.activate(subscription, new SubscriptionObserver<>(observer, chain));
        add(subscription);
        return subscription;
    }

    /** Adds all the passed subscriptions. */
    void addAll(Iterable<Subscription> newSubscriptions) {
        newSubscriptions.forEach(this::add);
    }

    /** Remembers the passed subscription for future use. */
    private void add(Subscription s) {
        items.add(checkNotNull(s));
    }

    /**
     * Requests cancellation the passed subscription.
     *
     * <p>The cancellation of the subscription is done asynchronously.
     *
     * @return {@code true} if the subscription was previously made
     */
    @CanIgnoreReturnValue
    public boolean cancel(Subscription s) {
        checkNotNull(s);
        var isActive = items.contains(s);
        if (isActive) {
            requestCancellation(s);
        }
        return isActive;
    }

    private void requestCancellation(Subscription s) {
        service.cancel(s, new CancellationObserver(s));
    }

    /**
     * Requests cancellation of all subscriptions.
     */
    public void cancelAll() {
        // Create the copy for iterating to avoid `ConcurrentModificationException` on removal.
        ImmutableSet.copyOf(items)
                    .forEach(this::requestCancellation);
    }

    @VisibleForTesting
    boolean contains(Subscription s) {
        return items.contains(s);
    }

    /**
     * Verifies if there are any active subscriptions.
     */
    public boolean isEmpty() {
        return items.isEmpty();
    }

    /**
     * Handles responses of cancellation requests.
     */
    private final class CancellationObserver implements StreamObserver<Response> {

        private static final String UNABLE_TO_CANCEL = "Unable to cancel the subscription `%s`.";
        private final Subscription subscription;

        private CancellationObserver(Subscription subscription) {
            this.subscription = checkNotNull(subscription);
        }

        @Override
        public void onNext(Response response) {
            if (response.isError()) {
                var err = response.error();
                serverErrorHandler().accept(subscription, err);
            }
        }

        @Override
        public void onError(Throwable t) {
            streamingErrorHandler().accept(t);
        }

        @Override
        public void onCompleted() {
            items.remove(subscription);
        }

        private ErrorHandler streamingErrorHandler() {
            return Optional.ofNullable(Subscriptions.this.streamingErrorHandler)
                           .orElse(new LoggingErrorHandler(
                                   logger(), UNABLE_TO_CANCEL, shortDebugString(subscription)
                           ));
        }

        private ServerErrorHandler serverErrorHandler() {
            return Optional.ofNullable(Subscriptions.this.serverErrorHandler)
                           .orElse(new LoggingServerErrorHandler(
                                   logger(), UNABLE_TO_CANCEL + " Returned error: `%s`."
                           ));
        }

        private Logger logger() {
            return Subscriptions.this.logger();
        }
    }
}
