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

package io.spine.server.stand;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Multimaps;
import io.spine.annotation.VisibleForTesting;
import io.spine.client.Subscription;
import io.spine.client.SubscriptionUpdate;
import io.spine.logging.WithLogging;
import io.spine.server.type.EventEnvelope;
import io.spine.system.server.event.EntityStateChanged;
import io.spine.type.TypeUrl;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.lang.String.format;

/**
 * A {@link SubscriptionRegistry} entry that manages a single subscription.
 *
 * <p>The record holds the update handlers of the targets of the subscription topic that
 * it serves. Each target has its own handlers, so a message that matches several targets
 * produces an update for each of them.
 *
 * <p>The record activates, updates, and cancels the subscription one at a time, so that
 * a callback receives no update after it is told about the cancellation. A callback may
 * call the registry, so the registry calls these methods only outside its lock.
 */
final class SubscriptionRecord implements WithLogging {

    private final Subscription subscription;
    private final ImmutableListMultimap<TypeUrl, UpdateHandler> handlers;

    /**
     * The callback that the record was last activated with, or {@code null} if the record
     * is not activated.
     *
     * <p>The field is {@code volatile} because {@link #isActivatedWith(SubscriptionCallback)}
     * reads it under the lock of the registry, not the monitor of the record.
     */
    private volatile @Nullable SubscriptionCallback callback;

    /**
     * Tells whether the subscription is cancelled.
     */
    private boolean cancelled = false;

    private SubscriptionRecord(Subscription s, ImmutableList<UpdateHandler> handlers) {
        this.subscription = s;
        this.handlers = Multimaps.index(handlers, UpdateHandler::eventType);
    }

    /**
     * Creates a subscription record for the given targets of the subscription topic.
     *
     * <p>Distinguishes event and entity targets via the target type URL.
     *
     * <p>By default, assumes that all targets with a non-event type are entity targets.
     *
     * <p>The handlers follow the order of the targets in the topic, whatever order
     * the indexes come in.
     *
     * @param subscription
     *         the subscription to serve
     * @param targets
     *         the indexes of the topic targets to serve
     * @throws IllegalArgumentException
     *         if there are no targets, or an index is out of the range of the topic targets
     */
    static SubscriptionRecord of(Subscription subscription, Set<Integer> targets) {
        checkArgument(!targets.isEmpty(),
                      "A subscription record must serve at least one target.");
        var count = subscription.getTopic()
                                .getTargetCount();
        var handlers = ImmutableList.<UpdateHandler>builder();
        for (int index : ImmutableSortedSet.copyOf(targets)) {
            checkArgument(index >= 0 && index < count,
                          "The topic has no target with the index %s.", index);
            var target = subscription.getTopic()
                                     .getTarget(index);
            if (target.ofEvent()) {
                handlers.add(new EventUpdateHandler(subscription, index));
            } else {
                handlers.addAll(entityHandlers(subscription, index));
            }
        }
        return new SubscriptionRecord(subscription, handlers.build());
    }

    /**
     * Creates a subscription record for all the targets of the subscription topic.
     */
    @VisibleForTesting
    static SubscriptionRecord of(Subscription subscription) {
        return of(subscription, allTargetsOf(subscription));
    }

    /**
     * Obtains the indexes of all the targets of the topic of the given subscription.
     */
    static ImmutableSet<Integer> allTargetsOf(Subscription subscription) {
        var count = subscription.getTopic()
                                .getTargetCount();
        return IntStream.range(0, count)
                        .boxed()
                        .collect(toImmutableSet());
    }

    /**
     * Creates the handlers of an entity target.
     *
     * <p>In fact, an entity target is served by handling {@link EntityStateChanged},
     * {@link io.spine.system.server.event.EntityDeleted EntityDeleted},
     * {@link io.spine.system.server.event.EntityArchived EntityArchived}, and other entity
     * lifecycle events with a matcher that validates the entity state packed inside the event.
     */
    private static ImmutableList<UpdateHandler> entityHandlers(Subscription subscription,
                                                               int target) {
        return ImmutableList.of(new EntityChangeHandler(subscription, target),
                                new EntityDeletionHandler(subscription, target),
                                new EntityRestorationHandler(subscription, target),
                                new EntityArchivalHandler(subscription, target),
                                new EntityUnarchivalHandler(subscription, target));
    }

    /**
     * Attaches a callback that notifies the read-side about a subscription update.
     *
     * <p>Only active subscription records are eligible to receive any event/entity updates.
     *
     * <p>If the record is activated with another callback, that callback serves
     * the subscription no more, and is {@linkplain SubscriptionCallback#onCancelled() told}
     * about it.
     *
     * @param callback
     *         the action to attach to the record
     * @return {@code true} if the record is activated, {@code false} if the subscription is
     *         cancelled
     */
    synchronized boolean activate(SubscriptionCallback callback) {
        if (cancelled) {
            return false;
        }
        var displaced = this.callback;
        for (var handler : handlers()) {
            handler.setCallback(callback);
        }
        this.callback = callback;
        if (displaced != null && displaced != callback) {
            tellCancelled(displaced);
        }
        return true;
    }

    /**
     * Tells whether the record was last activated with the given callback.
     */
    boolean isActivatedWith(SubscriptionCallback callback) {
        return this.callback == callback;
    }

    /**
     * Marks the subscription cancelled, and notifies the callback with which the record is
     * activated, if any, about the cancellation.
     *
     * <p>The callback receives no updates after that.
     */
    synchronized void notifyCancelled() {
        cancelled = true;
        var current = callback;
        if (current != null) {
            tellCancelled(current);
        }
    }

    /**
     * Marks the subscription cancelled without notifying the callback.
     *
     * <p>The callback receives no updates after that.
     */
    synchronized void release() {
        cancelled = true;
    }

    /**
     * Notifies the given callback that it serves the subscription no more.
     *
     * <p>A failure of the callback is logged: the subscription is cancelled anyway.
     */
    private void tellCancelled(SubscriptionCallback callback) {
        try {
            callback.onCancelled();
        } catch (RuntimeException e) {
            logger().atError().withCause(e).log(() -> format(
                    "Unable to tell the callback of the subscription `%s` about its cancellation.",
                    subscription.getId().getValue()));
        }
    }

    /**
     * Updates the subscription with the given event.
     *
     * <p>Passes the event to the handlers of every served target that handles events of its
     * type. Assumes that the subscription is active.
     *
     * <p>Does nothing once the subscription is cancelled, including when a callback cancels
     * it while the event is passed to the handlers of several targets.
     *
     * @throws IllegalStateException
     *         if the subscription is not activated
     * @see #activate(SubscriptionCallback)
     */
    synchronized void update(EventEnvelope event) {
        for (var handler : handlersFor(event)) {
            if (cancelled) {
                return;
            }
            handler.handle(event);
        }
    }

    private ImmutableList<UpdateHandler> handlersFor(EventEnvelope event) {
        var eventType = event.typeUrl();
        var result = handlers.get(eventType);
        checkArgument(!result.isEmpty(),
                      "Cannot find `UpdateHandler` for the event of type `%s`.", eventType);
        return result;
    }

    private Iterable<UpdateHandler> handlers() {
        Iterable<UpdateHandler> updateHandlers = handlers.values();
        return updateHandlers;
    }

    /**
     * Checks whether this record has an active callback attached.
     */
    synchronized boolean isActive() {
        return handlers.values()
                .stream()
                .anyMatch(UpdateHandler::isActive);
    }

    /**
     * A test-only method that exposes {@link UpdateHandler#detectUpdate(EventEnvelope)
     * UpdateHandler.detectUpdate(EventEnvelope)} to tests.
     *
     * @return the updates that the handlers of the served targets detect in the event,
     *         in the order of the targets
     */
    @VisibleForTesting
    ImmutableList<SubscriptionUpdate> detectUpdates(EventEnvelope event) {
        return handlersFor(event).stream()
                .map(handler -> handler.detectUpdate(event))
                .flatMap(Optional::stream)
                .collect(toImmutableList());
    }

    /**
     * Obtains the types of the events that the handlers of this record handle.
     */
    ImmutableSet<TypeUrl> eventTypes() {
        return handlers.keySet();
    }

    @Override
    public boolean equals(Object o) {
        return (this == o) ||
                ((o instanceof SubscriptionRecord that) &&
                        Objects.equal(subscription, that.subscription));
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(subscription);
    }
}
