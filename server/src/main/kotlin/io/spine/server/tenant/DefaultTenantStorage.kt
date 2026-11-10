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

package io.spine.server.tenant

import io.spine.base.Time
import io.spine.core.TenantId
import io.spine.server.ContextSpec
import io.spine.server.storage.RecordSpec
import io.spine.server.storage.StorageFactory

/**
 * Default implementation of `TenantStorage` that stores timestamps of tenant ID registration.
 *
 * This storage is not multi-tenant, as it stores the data across all tenants.
 *
 * [Application][io.spine.server.Application] uses it as the default index
 * of the tenants of multitenant contexts.
 *
 * @constructor Creates the storage with the given factory.
 */
internal class DefaultTenantStorage(factory: StorageFactory) :
    TenantStorage<Tenant>(contextSpec, factory.createRecordStorage(contextSpec, spec())) {

    override fun create(id: TenantId): Tenant =
        Tenant.newBuilder()
            .setId(id)
            .setWhenCreated(Time.currentTime())
            .build()

    private companion object {

        /**
         * A name of a Bounded Context in scope of which the tenant data is stored.
         */
        val contextSpec: ContextSpec = ContextSpec.singleTenant("__SYSTEM_TENANTS__")

        fun spec(): RecordSpec<TenantId, Tenant> =
            RecordSpec(TenantId::class.java, Tenant::class.java) { it.id }
    }
}
