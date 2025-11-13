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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import io.spine.client.Subscription;
import io.spine.client.SubscriptionId;
import io.spine.client.Subscriptions;
import io.spine.client.Topic;
import io.spine.type.TypeUrl;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import static com.google.common.collect.Multimaps.synchronizedSetMultimap;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * A slice with subscriptions belonging to one tenant in a multi-tenant application.
 */
final class TenantSubscriptionRegistry implements SubscriptionRegistry {

    private final SetMultimap<TypeUrl, SubscriptionRecord> typeToRecord =
            synchronizedSetMultimap(HashMultimap.create());
    private final Map<Subscription, SubscriptionRecord> subscriptionToAttrs =
            new ConcurrentHashMap<>();
    private final Lock lock = new ReentrantLock();

    @Override
    public void activate(Subscription subscription, SubscriptionCallback callback) {
        lockAndRun(() -> {
            var knownSubscription = asKnownSubscription(subscription);
            requireNonNull(knownSubscription,
                           () -> format(
                                   "Cannot find the subscription with ID `%s` in the registry.",
                                   subscription.getId()));
            var subscriptionRecord = subscriptionToAttrs.get(knownSubscription);
            subscriptionRecord.activate(callback);
        });
    }

    @Override
    public Subscription add(Topic topic) {
        var subscription = Subscriptions.from(topic);
        add(subscription);
        return subscription;
    }

    @Override
    public void add(Subscription subscription) {
        var record = SubscriptionRecord.of(subscription);
        lockAndRun(() -> {
            var types = record.targetTypes();
            for (var type : types) {
                typeToRecord.put(type, record);
                subscriptionToAttrs.put(subscription, record);
            }
        });
    }

    @Override
    public void remove(Subscription subscription) {
        lockAndRun(() -> {
            var toRemove = asKnownSubscription(subscription);
            if (toRemove == null) {
                return;
            }

            var record = subscriptionToAttrs.get(toRemove);
            var types = record.targetTypes();
            for (var type : types) {
                typeToRecord.remove(type, record);
            }
            subscriptionToAttrs.remove(toRemove);
        });
    }

    /**
     * Ensures that the given subscription is known to this subscription registry.
     *
     * <p>If the passed subscription is stored as-is, this method returns it.
     *
     * <p>Otherwise, performs a search by the subscription ID, and returns the result.
     * Such a trick makes sense, as the framework has no control over
     * the {@code Subscription} objects. They may arrive from client-side or other calling sites
     * with some attributes modified (such as timestamps). Therefore, it makes sense
     * to attempt another round of search using the ID of the given subscription.
     */
    private @Nullable Subscription asKnownSubscription(Subscription subscription) {
        if(subscriptionToAttrs.containsKey(subscription)) {
            return subscription;
        }
        var foundById = findById(subscription.getId());
        return foundById;
    }

    private @Nullable Subscription findById(SubscriptionId id) {
        var subscriptions = subscriptionToAttrs.keySet();
        for (var s : subscriptions) {
            if(s.getId().equals(id)) {
                return s;
            }
        }
        return null;
    }

    @Override
    public Set<SubscriptionRecord> byType(TypeUrl type) {
        return ImmutableSet.copyOf(lockAndGet(() -> typeToRecord.get(type)));
    }

    @Override
    public boolean hasType(TypeUrl type) {
        var result = typeToRecord.containsKey(type);
        return result;
    }

    @Override
    public boolean containsId(SubscriptionId subscriptionId) {
        var found = findById(subscriptionId);
        return found != null;
    }

    private void lockAndRun(Runnable operation) {
        lock.lock();
        try {
            operation.run();
        } finally {
            lock.unlock();
        }
    }

    private <T> T lockAndGet(Supplier<T> operation) {
        lock.lock();
        try {
            return operation.get();
        } finally {
            lock.unlock();
        }
    }
}
