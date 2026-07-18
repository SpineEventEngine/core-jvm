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

package io.spine.server.tenant;

import io.spine.core.TenantId;

import java.util.Optional;

import static io.spine.util.Preconditions2.checkNotDefaultArg;

/**
 * This class allows obtaining an ID of the current tenant when handling
 * a command or a query in a multi-tenant application.
 *
 * @see <a href="http://ramblingsofraju.com/wp-content/uploads/2016/08/Multi-Tenant-Data-Architecture.pdf">Multi-Tenant Data Architecture</a>
 */
final class CurrentTenant {

    private static final ThreadLocal<TenantId> threadLocal = new ThreadLocal<>();

    /** Prevent instantiation of this utility class. */
    private CurrentTenant() {
    }

    /**
     * Obtains the ID of a tenant served in the current thread.
     *
     * @return ID of the tenant or {@linkplain Optional#empty() empty Optional} if
     *         the current thread works not in a multi-tenant context
     */
    static Optional<TenantId> get() {
        var result = threadLocal.get();
        return Optional.ofNullable(result);
    }

    /**
     * Ensures that there is {@code TenantId} set for the current execution context.
     *
     * <p>If this is not the case, {@code IllegalStateException} will be thrown.
     *
     * @return the ID of the current tenant
     * @throws IllegalStateException if there is no current tenant ID set
     */
    static TenantId ensure() throws IllegalStateException {
        var currentTenant = get();
        if (currentTenant.isEmpty()) {
            throw new IllegalStateException(
                    "No current `TenantId` set in multi-tenant execution context.");
        }
        return currentTenant.get();
    }

    /**
     * Sets the ID of the tenant served in the current thread.
     *
     * @param tenantId a non-null and non-default instance of {@code TenantId}
     */
    static void set(TenantId tenantId) {
        checkNotDefaultArg(tenantId, "A tenant ID must not be a default value.");
        threadLocal.set(tenantId);
    }

    /**
     * Clears the stored value.
     */
    static void clear() {
        threadLocal.set(null);
    }
}
