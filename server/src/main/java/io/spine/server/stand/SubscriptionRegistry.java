/*
 * Copyright 2026, TeamDev. All rights reserved.
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

package io.spine.server.stand;

import io.spine.client.Subscription;
import io.spine.client.SubscriptionId;
import io.spine.client.Topic;
import io.spine.type.TypeUrl;

import java.util.Set;

/**
 * Registry for subscription management.
 *
 * <p>Provides a quick access to the subscription records by {@link TypeUrl}.
 *
 * <p>Responsible for {@link Subscription} object instantiation.
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
     * Creates a subscription for the passed {@link Topic} and adds it to the registry.
     *
     * @param topic
     *         the topic to subscribe to
     * @return the created subscription
     */
    Subscription add(Topic topic);

    /**
     * Registers an existing subscription.
     *
     * <p>Some subscriptions may be present in many registries at the same time since Bounded
     * Contexts may share subscriptions.
     *
     * @param subscription
     *         the existing subscription
     */
    void add(Subscription subscription);

    /**
     * Removes the subscription from this registry.
     *
     * <p>If there is no such subscription in this instance of {@code SubscriptionRegistry},
     * invocation has no effect.
     *
     * @param subscription
     *         the subscription to remove
     */
    void remove(Subscription subscription);

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
