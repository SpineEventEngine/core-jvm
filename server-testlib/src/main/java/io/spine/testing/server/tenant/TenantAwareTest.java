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

package io.spine.testing.server.tenant;

import io.spine.annotation.Internal;
import io.spine.annotation.VisibleForTesting;
import io.spine.core.TenantId;
import io.spine.environment.Environment;
import io.spine.environment.Tests;
import io.spine.server.Application;
import io.spine.server.tenant.TenantAwareTestSupport;
import io.spine.server.tenant.TenantFunction;
import io.spine.server.tenant.TenantIndex;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

/**
 * Abstract base for test suites that test tenant-aware functionality.
 *
 * <p>This class must be used only from {@linkplain io.spine.environment.Tests
 * test execution environment}.
 */
@Internal
@VisibleForTesting
public abstract class TenantAwareTest {

    /**
     * Creates a default implementation of {@link TenantIndex} for this test suite.
     *
     * @param multitenant
     *  set to {@code true} when a suite tests a multi-tenant context, {@code false} otherwise
     * @return a new tenant index
     */
    public static TenantIndex createTenantIndex(boolean multitenant) {
        return multitenant
               ? Application.instance().defaultMultitenantIndex()
               : TenantIndex.singleTenant();
    }

    /**
     * Sets the current tenant ID to the passed value.
     *
     * <p>Call this method in a set-up method of the derived test suite class.
     */
    protected void setCurrentTenant(TenantId tenantId) {
        checkNotNull(tenantId);
        checkInTests();
        TenantAwareTestSupport.inject(tenantId);
    }

    /**
     * Clears the current tenant ID.
     *
     * <p>Call this method in a clean-up method of the derived test suite class.
     */
    protected void clearCurrentTenant() {
        checkInTests();
        TenantAwareTestSupport.clear();
    }

    /**
     * Obtains current tenant ID.
     *
     * @throws IllegalStateException
     *         if the current tenant ID was {@linkplain #setCurrentTenant(TenantId) not set}, or
     *         already {@linkplain #clearCurrentTenant() cleared}
     */
    protected TenantId tenantId() {
        return currentTenant();
    }

    private static void checkInTests() {
        checkState(Environment.instance()
                              .is(Tests.class));
    }

    private static TenantId currentTenant() {
        var fn = new TenantFunction<TenantId>(true) {
            @Override
            public TenantId apply(TenantId id) {
                return id;
            }
        };
        var result = fn.execute();
        return requireNonNull(result);
    }
}
