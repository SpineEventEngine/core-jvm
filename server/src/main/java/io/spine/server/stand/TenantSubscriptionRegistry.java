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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import io.spine.client.Subscription;
import io.spine.client.SubscriptionId;
import io.spine.type.TypeUrl;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkArgument;
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
    public void add(Subscription subscription, Set<Integer> targets) {
        var record = SubscriptionRecord.of(subscription, targets);
        lockAndRun(() -> {
            checkArgument(findById(subscription.getId()) == null,
                          "The subscription with ID `%s` is already in the registry.",
                          subscription.getId().getValue());
            for (var type : record.eventTypes()) {
                typeToRecord.put(type, record);
            }
            subscriptionToAttrs.put(subscription, record);
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
            for (var type : record.eventTypes()) {
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
