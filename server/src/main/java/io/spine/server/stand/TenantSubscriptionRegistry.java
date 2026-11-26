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

    /**
     * The records of the subscriptions, by the IDs of the subscriptions.
     *
     * <p>The records are found by the ID only, as the framework has no control over
     * the {@code Subscription} objects. They may arrive from client-side or other calling sites
     * with some attributes modified (such as timestamps).
     */
    private final Map<SubscriptionId, SubscriptionRecord> records = new ConcurrentHashMap<>();
    private final Lock lock = new ReentrantLock();

    @Override
    public void activate(Subscription subscription, SubscriptionCallback callback) {
        lockAndRun(() -> {
            var record = records.get(subscription.getId());
            requireNonNull(record,
                           () -> format(
                                   "Cannot find the subscription with ID `%s` in the registry.",
                                   subscription.getId()));
            record.activate(callback);
        });
    }

    @Override
    public void add(Subscription subscription, Set<Integer> targets) {
        var record = SubscriptionRecord.of(subscription, targets);
        var id = subscription.getId();
        lockAndRun(() -> {
            checkArgument(!records.containsKey(id),
                          "The subscription with ID `%s` is already in the registry.",
                          id.getValue());
            for (var type : record.eventTypes()) {
                typeToRecord.put(type, record);
            }
            records.put(id, record);
        });
    }

    @Override
    public void remove(Subscription subscription) {
        lockAndRun(() -> {
            var record = records.remove(subscription.getId());
            if (record == null) {
                return;
            }
            for (var type : record.eventTypes()) {
                typeToRecord.remove(type, record);
            }
        });
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
        return records.containsKey(subscriptionId);
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
