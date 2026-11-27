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
import io.spine.core.TenantId;
import io.spine.server.tenant.TenantFunction;
import io.spine.type.TypeUrl;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Objects.requireNonNull;

/**
 * Registry for subscription management in a multi-tenant context.
 */
final class MultiTenantSubscriptionRegistry implements SubscriptionRegistry {

    /** The map from {@code TenantId} to its slice of data. */
    private final Map<TenantId, SubscriptionRegistry> tenantSlices = new ConcurrentHashMap<>();

    private final boolean multiTenant;

    private MultiTenantSubscriptionRegistry(boolean multiTenant) {
        this.multiTenant = multiTenant;
    }

    static MultiTenantSubscriptionRegistry newInstance(boolean multiTenant) {
        return new MultiTenantSubscriptionRegistry(multiTenant);
    }

    @Override
    public boolean activate(Subscription subscription, SubscriptionCallback callback) {
        return registrySlice().activate(subscription, callback);
    }

    @Override
    public void add(Subscription subscription, Set<Integer> targets) {
        registrySlice().add(subscription, targets);
    }

    @Override
    public Optional<SubscriptionRecord> remove(Subscription subscription) {
        return registrySlice().remove(subscription);
    }

    @Override
    public Optional<SubscriptionRecord> removeIfActivatedWith(Subscription subscription,
                                                              SubscriptionCallback callback) {
        return registrySlice().removeIfActivatedWith(subscription, callback);
    }

    @Override
    public Set<SubscriptionRecord> byType(TypeUrl type) {
        return registrySlice().byType(type);
    }

    @Override
    public boolean containsId(SubscriptionId subscriptionId) {
        return registrySlice().containsId(subscriptionId);
    }

    @Override
    public boolean hasType(TypeUrl type) {
        return registrySlice().hasType(type);
    }

    private boolean isMultiTenant() {
        return multiTenant;
    }

    private SubscriptionRegistry registrySlice() {
        var func = new TenantFunction<SubscriptionRegistry>(isMultiTenant()) {
            @Override
            public SubscriptionRegistry apply(TenantId tenantId) {
                checkNotNull(tenantId);
                var slice = tenantSlices.computeIfAbsent(
                        tenantId,
                        id -> new TenantSubscriptionRegistry()
                );
                return slice;
            }
        };
        var result = func.execute();
        return requireNonNull(result);
    }
}
