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
    static ActorRequestFactory instance(boolean multiTenant) {
        return multiTenant
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
