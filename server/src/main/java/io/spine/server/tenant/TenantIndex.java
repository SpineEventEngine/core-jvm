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

import io.spine.core.TenantId;
import io.spine.server.ServerEnvironment;

import java.util.Set;

/**
 * The index of tenant IDs in a multi-tenant application.
 */
public interface TenantIndex extends io.spine.server.Closeable {

    /**
     * Stores the passed tenant ID in the index.
     */
    void keep(TenantId id);

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
     * Creates default implementation of {@code TenantIndex} for a multi-tenant context.
     */
    static TenantIndex defaultMultitenant() {
        var factory = ServerEnvironment.instance()
                                       .storageFactory();
        @SuppressWarnings("ClassReferencesSubclass") // OK for this default impl.
        var tenantRepo = new DefaultTenantStorage(factory);
        return tenantRepo;
    }

    /**
     * Obtains a {@code TenantIndex} to be used in single-tenant context.
     *
     * <p>This rudimentary implementation always returns pre-defined constant {@code TenantId}
     * value, which a single-tenant application does not need to use.
     */
    static TenantIndex singleTenant() {
        return SingleTenantIndex.INSTANCE;
    }
}
