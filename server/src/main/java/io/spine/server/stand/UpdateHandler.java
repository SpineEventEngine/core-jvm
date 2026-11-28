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

import com.google.protobuf.Any;
import io.spine.client.IdFilter;
import io.spine.client.Subscription;
import io.spine.client.SubscriptionUpdate;
import io.spine.client.Target;
import io.spine.logging.WithLogging;
import io.spine.server.type.EventEnvelope;
import io.spine.type.TypeUrl;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

import java.util.Optional;

import static io.spine.core.Responses.ok;
import static io.spine.util.Exceptions.newIllegalStateException;
import static java.lang.String.format;

/**
 * Handles the domain and system events that correspond to a target of
 * the specified {@code Subscription}.
 *
 * <p>Compares the event data to the filtering criteria of the target and
 * notifies the {@linkplain #setCallback(SubscriptionCallback) callback} with
 * the detected {@linkplain SubscriptionUpdate subscription updates}.
 */
abstract class UpdateHandler implements WithLogging {

    private final Subscription subscription;

    /**
     * The index of the handled target among the targets of the subscription topic.
     */
    private final int targetIndex;

    /**
     * The handled target of the subscription topic.
     */
    private final Target target;

    /**
     * The type of {@code EntityLifecycle} event handled by this instance.
     */
    private final TypeUrl eventType;

    /**
     * An action that accepts the update and notifies the read-side accordingly.
     */
    private @MonotonicNonNull SubscriptionCallback callback = null;

    /**
     * Creates an update handler acting according to the criteria of the given target of
     * the passed {@code Subscription}, reacting to the events of the specified
     * {@code eventType}.
     *
     * @param subscription
     *         the subscription to serve
     * @param targetIndex
     *         the index of the served target among the targets of the subscription topic
     * @param eventType
     *         the type of the handled events
     */
    UpdateHandler(Subscription subscription, int targetIndex, TypeUrl eventType) {
        this.subscription = subscription;
        this.targetIndex = targetIndex;
        this.target = targetOf(subscription, targetIndex);
        this.eventType = eventType;
    }

    /**
     * Obtains the target with the given index from the topic of the given subscription.
     */
    static Target targetOf(Subscription subscription, int targetIndex) {
        return subscription.getTopic()
                           .getTarget(targetIndex);
    }

    /**
     * Analyzes the incoming event against the {@code Subscription} of this handler and notifies
     * the read-side if the event triggered any subscription updates.
     *
     * <p>The subscription must be {@linkplain #isActive() active}.
     *
     * @param event
     *         the event that may trigger subscription updates
     */
    void handle(EventEnvelope event) {
        checkActive(event);
        matchingUpdate(event).ifPresent(this::deliverUpdate);
    }

    /**
     * Detects the update that the passed event brings to the subscription.
     *
     * <p>If the event cannot be matched against the criteria of the target, for example,
     * because a filter value cannot be compared to the field value, the failure is logged,
     * and no update is detected. So, the event still reaches the other subscriptions
     * to its type.
     */
    private Optional<SubscriptionUpdate> matchingUpdate(EventEnvelope event) {
        try {
            return detectUpdate(event);
        } catch (RuntimeException e) {
            var subscriptionId = subscription.getId().getValue();
            logger().atError().withCause(e).log(() -> format(
                    "Unable to match the event of type `%s` against the target `%d`" +
                            " of the subscription with the ID `%s`.",
                    event.typeUrl(), targetIndex, subscriptionId));
            return Optional.empty();
        }
    }

    /**
     * Ensures that the handler is active, otherwise throws {@link IllegalStateException} with
     * the information on the passed event.
     */
    private void checkActive(EventEnvelope event) {
        if (!isActive()) {
            throw newIllegalStateException(
                    "Dispatched an event of type `%s` to the non-active subscription with the ID `%s`.",
                    event.typeUrl(),
                    subscription.getId()
                                .getValue()
            );
        }
    }

    private void deliverUpdate(SubscriptionUpdate update) {
        try {
            callback.accept(update);
        } catch (Throwable t) {
            logger().atError().withCause(t).log();
        }
    }

    /**
     * Obtains the handled {@code Target} of the subscription topic.
     */
    final Target target() {
        return target;
    }

    /**
     * Creates a builder of a successful update of the handled target of the subscription.
     */
    final SubscriptionUpdate.Builder newUpdate() {
        return SubscriptionUpdate.newBuilder()
                .setSubscription(subscription)
                .setResponse(ok())
                .setTargetIndex(targetIndex);
    }

    /**
     * Obtains the handled subscription.
     */
    final Subscription subscription() {
        return subscription;
    }

    /**
     * Returns the type of system events to which this handler reacts.
     */
    final TypeUrl eventType() {
        return eventType;
    }

    /**
     * Tries to detect the passed event as a {@code SubscriptionUpdate} according
     * to the {@code Subscription} criteria.
     *
     * @param event
     *         the event to analyze against this subscription
     * @return {@code SubscriptionUpdate} packed as {@code Optional} if the event matches the
     *         subscription criteria, {@code Optional.empty()} otherwise
     */
    abstract Optional<SubscriptionUpdate> detectUpdate(EventEnvelope event);

    /**
     * Extracts the ID value of the updated {@code Entity} or received {@code Event}.
     *
     * @param event
     *         system event transmitting the {@code Entity} update info
     *         or the domain event to which a subscription exists
     */
    abstract Any extractId(EventEnvelope event);

    /**
     * Tells whether the type of the updated {@code Entity} or received domain {@code Event}
     * matches the type configured for the handled {@code Subscription}.
     *
     * @param event
     *         system event transmitting the {@code Entity} update info
     *         or the domain event to which a subscription exists
     */
    abstract boolean typeMatches(EventEnvelope event);

    /**
     * Checks if the event matches the subscription ID filter.
     */
    boolean idMatches(EventEnvelope event) {
        var filters = target().getFilters();
        var idFilter = filters.getIdFilter();
        var idFilterSet = !IdFilter.getDefaultInstance()
                                   .equals(idFilter);
        if (!idFilterSet) {
            return true;
        }
        var id = extractId(event);
        var result = idFilter.getIdList()
                             .contains(id);
        return result;
    }

    /**
     * Checks if the subscription has {@code include_all} clause.
     */
    boolean includeAll() {
        return target().getIncludeAll();
    }

    /**
     * Activates this handler with a given callback.
     */
    void setCallback(SubscriptionCallback callback) {
        this.callback = callback;
    }

    /**
     * Checks if this handler has a callback set.
     */
    boolean isActive() {
        return callback != null;
    }
}
