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
import org.jspecify.annotations.Nullable;

import java.util.function.Function;

/**
 * A function that can work in single-tenant and multi-tenant context and return a
 * value depending on the current tenant set.
 *
 * @param <T> the type of the result returned by the function
 */
@Internal
public abstract class TenantFunction<T> extends TenantAware implements Function<TenantId, T> {

    /**
     * Creates a new instance of the function.
     *
     * @param multiTenant if {@code true} the function is executed in the multi-tenant context,
     *                    {@code false} for single-tenant context
     */
    protected TenantFunction(boolean multiTenant) {
        super(currentTenant(multiTenant));
    }

    /**
     * Applies the function and returns the result.
     *
     * @return the result of the function
     */
    public @Nullable T execute() {
        var currentTenant = tenantId();
        var result = apply(currentTenant);
        return result;
    }
}
