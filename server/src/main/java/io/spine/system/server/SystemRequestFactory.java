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

package io.spine.system.server;

import io.spine.client.ActorRequestFactory;
import io.spine.core.TenantId;
import io.spine.server.tenant.TenantFunction;
import org.jspecify.annotations.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.system.server.DefaultSystemWriteSide.SYSTEM_USER;
import static java.util.Objects.requireNonNull;

/**
 * Creates an actor request factory for producing requests under the context of specified tenant.
 */
final class SystemRequestFactory {

    private static final ActorRequestFactory SINGLE_TENANT = newFactoryFor(null);

    /** Prevents instantiation of this utility class. */
    private SystemRequestFactory() {
    }

    /**
     * Obtains the instance depending on the multitenancy context.
     *
     * <p>In single-tenant context the same instance is returned.
     * In multitenant context, a new request factory is created with
     * the ID of the current tenant.
     */
    static ActorRequestFactory instance(boolean multitenant) {
        return multitenant
               ? newForCurrentTenant()
               : SINGLE_TENANT;
    }

    private static ActorRequestFactory newForCurrentTenant() {
        var createFactory = new TenantFunction<ActorRequestFactory>(true) {
            @Override
            public ActorRequestFactory apply(TenantId tenantId) {
                checkNotNull(tenantId);
                return newFactoryFor(tenantId);
            }
        };
        var result = createFactory.execute();
        requireNonNull(result);
        return result;
    }

    private static ActorRequestFactory newFactoryFor(@Nullable TenantId tenantId) {
        return ActorRequestFactory.newBuilder()
                .setActor(SYSTEM_USER)
                .setTenantId(tenantId)
                .build();
    }
}
