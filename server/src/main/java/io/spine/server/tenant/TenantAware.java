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
import io.spine.core.TenantId;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.protobuf.Messages.isDefault;

/**
 * An abstract base for operations performed in a tenant context.
 */
abstract class TenantAware {

    private final TenantId tenantId;

    /**
     * Creates an instance, which uses the {@code TenantId}
     * set in the current non-command handling execution context.
     *
     * @throws IllegalStateException if there is no current {@code TenantId}
     * @see CurrentTenant#ensure()
     */
    TenantAware() throws IllegalStateException {
        this(CurrentTenant.ensure());
    }

    /**
     * Creates an instance for the tenant specified by the passed ID.
     *
     * <p>If a default instance of {@link TenantId} is passed (because the application works in
     * a single-tenant mode), {@linkplain SingleTenantIndex#tenantId() singleTenant()} value will be
     * substituted.
     *
     * @param tenantId the tenant ID or {@linkplain TenantId#getDefaultInstance() default value}
     */
    TenantAware(TenantId tenantId) {
        checkNotNull(tenantId);
        this.tenantId = isDefault(tenantId)
                        ? SingleTenantIndex.tenantId()
                        : tenantId;
    }

    /**
     * Verifies whether a current tenant is set in the execution context.
     */
    @Internal
    public static boolean isTenantSet() {
        var currentTenant = CurrentTenant.get();
        return currentTenant.isPresent();
    }

    /**
     * Obtains current tenant ID.
     *
     * <p>In the multi-tenant context obtains the currently set tenant ID.
     *
     * <p>In single-tenant context, returns {@link SingleTenantIndex#tenantId() singleTenant()}
     *
     * @param multiTenantContext {@code true} if execution context is multi-tenant
     * @return current tenant ID or {@link SingleTenantIndex#tenantId() singleTenant()}
     * @throws IllegalStateException if there is no current tenant set in a multi-tenant context
     */
    static TenantId currentTenant(boolean multiTenantContext) {
        if (!multiTenantContext) {
            return SingleTenantIndex.tenantId();
        }
        return CurrentTenant.ensure();
    }

    final TenantId tenantId() {
        return tenantId;
    }
}
