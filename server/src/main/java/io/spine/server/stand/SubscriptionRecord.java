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
package io.spine.server.stand;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.spine.annotation.VisibleForTesting;
import io.spine.client.Subscription;
import io.spine.client.SubscriptionUpdate;
import io.spine.server.type.EventEnvelope;
import io.spine.system.server.event.EntityStateChanged;
import io.spine.type.TypeUrl;

import java.util.Optional;

import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * A {@link SubscriptionRegistry} entry that manages a single subscription.
 */
final class SubscriptionRecord {

    private final Subscription subscription;
    private final ImmutableMap<TypeUrl, UpdateHandler> handlers;

    private SubscriptionRecord(Subscription s, ImmutableSet<UpdateHandler> handlers) {
        this.subscription = s;
        this.handlers = handlers.stream()
                .collect(toImmutableMap(UpdateHandler::eventType, h -> h));
    }

    private SubscriptionRecord(Subscription s, UpdateHandler handler) {
        this(s, ImmutableSet.of(handler));
    }

    /**
     * Creates a subscription record for the given subscription.
     *
     * <p>Distinguishes event and entity subscriptions via the target type URL.
     *
     * <p>By default, assumes that all subscriptions with a non-event type are entity
     * subscriptions.
     */
    static SubscriptionRecord of(Subscription subscription) {
        if (subscription.ofEvent()) {
            return createEventRecord(subscription);
        }
        return createEntityRecord(subscription);
    }

    /**
     * Creates a record managing an event subscription.
     */
    private static SubscriptionRecord createEventRecord(Subscription subscription) {
        var handler = new EventUpdateHandler(subscription);
        return new SubscriptionRecord(subscription, handler);
    }

    /**
     * Creates a record managing a subscription on entity changes.
     *
     * <p>In fact, this is a subscription to {@link EntityStateChanged},
     * {@link EntityDeleted}, and {@link EntityArchived} events with a custom callback
     * and matcher (to validate the entity state packed inside the event).
     */
    private static SubscriptionRecord createEntityRecord(Subscription subscription) {
        ImmutableSet<UpdateHandler> handlers =
                ImmutableSet.of(new EntityChangeHandler(subscription),
                                new EntityDeletionHandler(subscription),
                                new EntityRestorationHandler(subscription),
                                new EntityArchivalHandler(subscription),
                                new EntityUnarchivalHandler(subscription));
        return new SubscriptionRecord(subscription, handlers);
    }

    /**
     * Attaches a callback which notifies the read-side about a subscription update.
     *
     * <p>Only active subscription records are eligible to receiving any event/entity updates.
     *
     * @param callback
     *         the action to attach to the record
     */
    void activate(SubscriptionCallback callback) {
        for (var handler : handlers()) {
            handler.setCallback(callback);
        }
    }

    /**
     * Updates the subscription with the given event.
     *
     * <p>Assumes the event is matching the subscription by both filters and type, and that the
     * subscription is active.
     *
     * @throws IllegalStateException
     *         if the subscription is not activated
     * @see #activate(SubscriptionCallback)
     */
    void update(EventEnvelope event) {
        var handler = handlerForEvent(event);
        handler.handle(event);
    }

    private UpdateHandler handlerForEvent(EventEnvelope event) {
        var eventType = event.typeUrl();
        var handler = handlers.get(eventType);
        requireNonNull(handler,
                       () -> format("Cannot find `UpdateHandler` for the event of type `%s`.",
                                    eventType));
        return handler;
    }

    private Iterable<UpdateHandler> handlers() {
        Iterable<UpdateHandler> updateHandlers = handlers.values();
        return updateHandlers;
    }

    /**
     * Checks whether this record has an active callback attached.
     */
    boolean isActive() {
        return handlers.values()
                .stream()
                .anyMatch(UpdateHandler::isActive);
    }

    /**
     * A test-only method that exposes {@link UpdateHandler#detectUpdate(EventEnvelope)
     * UpdateHandler.detectUpdate(EventEnvelope)} to tests.
     */
    @VisibleForTesting
    Optional<SubscriptionUpdate> detectUpdate(EventEnvelope event) {
        var handler = handlerForEvent(event);
        return handler.detectUpdate(event);
    }

    ImmutableSet<TypeUrl> targetTypes() {
        return handlers.keySet();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SubscriptionRecord)) {
            return false;
        }
        var that = (SubscriptionRecord) o;
        return Objects.equal(subscription, that.subscription);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(subscription);
    }
}
