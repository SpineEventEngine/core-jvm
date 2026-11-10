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

import io.spine.annotation.Internal;
import io.spine.base.Time;
import io.spine.core.TenantId;
import io.spine.server.ContextSpec;
import io.spine.server.storage.RecordSpec;
import io.spine.server.storage.StorageFactory;

/**
 * Default implementation of {@code TenantStorage} that stores timestamps of tenant ID registration.
 *
 * <p>This storage is not multi-tenant, as it stores the data across all tenants.
 *
 * <p>{@link io.spine.server.Application Application} uses it as the default index
 * of the tenants of multitenant contexts.
 */
@Internal
public final class DefaultTenantStorage extends TenantStorage<Tenant> {

    /**
     * A name of a Bounded Context in scope of which the tenant data is stored.
     */
    @SuppressWarnings("TestOnlyProblems")   // The called API is not test-only.
    private static final ContextSpec contextSpec = ContextSpec.singleTenant("__SYSTEM_TENANTS__");

    /**
     * Creates the storage with the given factory.
     */
    public DefaultTenantStorage(StorageFactory factory) {
        super(contextSpec, factory.createRecordStorage(contextSpec, spec()));
    }

    private static RecordSpec<TenantId, Tenant> spec() {
        return new RecordSpec<>(TenantId.class, Tenant.class, Tenant::getId);
    }

    @Override
    protected Tenant create(TenantId id) {
        return Tenant.newBuilder()
                     .setId(id)
                     .setWhenCreated(Time.currentTime())
                     .build();
    }
}
