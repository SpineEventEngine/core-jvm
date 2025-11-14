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
     * a single-tenant mode, {@linkplain SingleTenantIndex#tenantId() singleTenant()} value will be
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
     * @param multitenantContext {@code true} if execution context is multi-tenant
     * @return current tenant ID or {@link SingleTenantIndex#tenantId() singleTenant()}
     * @throws IllegalStateException if there is no current tenant set in a multi-tenant context
     */
    static TenantId currentTenant(boolean multitenantContext) {
        if (!multitenantContext) {
            return SingleTenantIndex.tenantId();
        }
        return CurrentTenant.ensure();
    }

    final TenantId tenantId() {
        return tenantId;
    }
}
