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

import io.spine.client.Subscription;
import io.spine.client.SubscriptionId;
import io.spine.type.TypeUrl;

import java.util.Optional;
import java.util.Set;

/**
 * Registry for subscription management.
 *
 * <p>Provides a quick access to the subscription records by {@link TypeUrl}.
 */
interface SubscriptionRegistry {

    /**
     * Activates the subscription with the passed action.
     *
     * <p>The passed action will be used to notify the read-side about the subscription update.
     *
     * @param subscription
     *         the subscription to activate
     * @param callback
     *         the action that notifies the subscription listeners on the read-side
     */
    void activate(Subscription subscription, SubscriptionCallback callback);

    /**
     * Registers an existing subscription for the given targets of its topic.
     *
     * <p>Some subscriptions may be present in many registries at the same time since Bounded
     * Contexts may share subscriptions. Each registry serves the targets that its Bounded
     * Context serves.
     *
     * @param subscription
     *         the existing subscription
     * @param targets
     *         the indexes of the topic targets to serve
     * @throws IllegalArgumentException
     *         if the registry already has a subscription with the same ID, or
     *         the targets are empty or out of the range of the topic targets
     */
    void add(Subscription subscription, Set<Integer> targets);

    /**
     * Removes the subscription from this registry.
     *
     * <p>If there is no such subscription in this instance of {@code SubscriptionRegistry},
     * invocation has no effect.
     *
     * @param subscription
     *         the subscription to remove
     * @return the removed record, or {@code Optional.empty()} if there was no such subscription
     */
    Optional<SubscriptionRecord> remove(Subscription subscription);

    /**
     * Removes the subscription from this registry if it is activated with the given callback.
     *
     * <p>Leaves the subscription in the registry if it is not activated, or is activated with
     * another callback.
     *
     * @param subscription
     *         the subscription to remove
     * @param callback
     *         the callback with which the subscription must be activated
     * @return {@code true} if the subscription was removed
     */
    boolean removeIfActivatedWith(Subscription subscription, SubscriptionCallback callback);

    /**
     * Allows determining if this registry has an item with the specified ID.
     *
     * @param subscriptionId
     *         the subscription ID to look for
     * @return {@code true}, if this registry has a subscription with the given ID,
     *         {@code false} otherwise.
     */
    boolean containsId(SubscriptionId subscriptionId);

    /**
     * Filters the registered {@link SubscriptionRecord}s by their type.
     *
     * @param type
     *         the type to filter by
     * @return the collection of filtered records
     */
    Set<SubscriptionRecord> byType(TypeUrl type);

    /**
     * Checks whether the current registry has the records related to a given type.
     *
     * @param type
     *         the type to check records for
     * @return {@code true} if there are records with the given type, {@code false} otherwise
     */
    boolean hasType(TypeUrl type);
}
