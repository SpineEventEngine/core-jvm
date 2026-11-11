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

import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A convenience API for {@code TenantAware} operations.
 */
@Internal
public final class TenantAwareRunner {

    private final TenantId tenant;

    private TenantAwareRunner(TenantId tenant) {
        this.tenant = tenant;
    }

    /**
     * Creates a new {@code TenantAwareRunner}, which runs all the given operations for the given
     * {@code tenant}.
     *
     * @param tenant
     *         the target tenant
     * @return new instance of {@code TenantAwareRunner}
     */
    public static TenantAwareRunner with(TenantId tenant) {
        checkNotNull(tenant);
        return new TenantAwareRunner(tenant);
    }

    /**
     * Creates a new {@code TenantAwareRunner}, which runs all the given operations for the current
     * {@code tenant}.
     *
     * @param multiTenant
     *         if the current environment is multitenant
     * @return new instance of {@code TenantAwareRunner}
     */
    public static TenantAwareRunner withCurrentTenant(boolean multiTenant) {
        return with(TenantAware.currentTenant(multiTenant));
    }

    /**
     * Runs the given {@code operation} for the given tenant and returns the result of
     * the operation.
     *
     * @param operation
     *         the operation to run
     * @param <T> the type of the operation result
     * @return the result of the operation
     */
    public <T> T evaluate(Supplier<T> operation) {
        checkNotNull(operation);
        var result = new TenantAwareFunction0<T>(tenant) {
            @Override
            public T apply() {
                return operation.get();
            }
        }.execute();
        return result;
    }

    /**
     * Runs the given {@code operation} for the given tenant.
     *
     * @param operation
     *         the operation to run
     */
    public void run(Runnable operation) {
        checkNotNull(operation);
        new TenantAwareOperation(tenant) {
            @Override
            public void run() {
                operation.run();
            }
        }.execute();
    }
}
