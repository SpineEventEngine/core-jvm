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

package io.spine.server.tenant;

import io.spine.core.TenantId;
import io.spine.server.Closeable;
import io.spine.server.ServerEnvironment;

import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The index of tenant IDs in a multi-tenant application.
 */
public interface TenantIndex extends Closeable {

    /**
     * Stores the passed tenant ID in the index.
     */
    void keep(TenantId id);

    /**
     * Tells whether the index has the tenant with the given ID.
     *
     * <p>Implementations must not store the tenant as a side effect. They must not cache
     * a negative answer either, because another server node may store the tenant
     * at any moment.
     *
     * <p>If the index cannot answer, for example, because the storage behind it is not
     * available, the implementation throws instead of returning {@code false}.
     *
     * <p>The default implementation looks the tenant up in {@link #all()}. Implementations
     * backed by a remote storage should override it.
     *
     * @param id
     *         the ID of the tenant
     * @return {@code true} if the index has the tenant, {@code false} otherwise
     */
    default boolean contains(TenantId id) {
        checkNotNull(id);
        return all().contains(id);
    }

    /**
     * Obtains the set of all stored tenant IDs.
     */
    Set<TenantId> all();

    /**
     * Closes the index for further read or write operations.
     *
     * <p>Implementations may throw specific exceptions.
     */
    @Override
    void close();

    /**
     * Creates a default implementation of {@code TenantIndex} for a multi-tenant context.
     *
     * <p>The created index keeps the tenants with the storage factory
     * of {@link ServerEnvironment}.
     */
    static TenantIndex defaultMultitenant() {
        var factory = ServerEnvironment.instance()
                                       .storageFactory();
        @SuppressWarnings("ClassReferencesSubclass") // OK for this default impl.
        var tenantRepo = new DefaultTenantStorage(factory);
        return tenantRepo;
    }

    /**
     * Obtains a {@code TenantIndex} to be used in a single-tenant context.
     *
     * <p>This rudimentary implementation always returns a pre-defined constant {@code TenantId}
     * value, which a single-tenant application does not need to use.
     */
    static TenantIndex singleTenant() {
        return SingleTenantIndex.INSTANCE;
    }
}
