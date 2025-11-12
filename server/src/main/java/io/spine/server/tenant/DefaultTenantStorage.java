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

import io.spine.base.Time;
import io.spine.core.TenantId;
import io.spine.server.ContextSpec;
import io.spine.server.storage.RecordSpec;
import io.spine.server.storage.StorageFactory;

/**
 * Default implementation of {@code TenantStorage} that stores timestamps of tenant ID registration.
 *
 * <p>This storage is not multi-tenant, as it stores the data across all tenants.
 */
final class DefaultTenantStorage extends TenantStorage<Tenant> {

    /**
     * A name of a Bounded Context in scope of which the tenant data is stored.
     */
    @SuppressWarnings("TestOnlyProblems")   // The called API is not test-only.
    private static final ContextSpec contextSpec = ContextSpec.singleTenant("__SYSTEM_TENANTS__");

    DefaultTenantStorage(StorageFactory factory) {
        super(contextSpec, factory.createRecordStorage(contextSpec, spec()));
    }

    @SuppressWarnings("ConstantConditions")     // Protobuf getters do not return {@code null}s.
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
