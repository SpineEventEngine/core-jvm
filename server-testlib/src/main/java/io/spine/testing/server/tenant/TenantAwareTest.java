/*
 * Copyright 2025, TeamDev. All rights reserved.
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

package io.spine.testing.server.tenant;

import io.spine.annotation.Internal;
import io.spine.annotation.VisibleForTesting;
import io.spine.core.TenantId;
import io.spine.environment.Environment;
import io.spine.environment.Tests;
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
               ? TenantIndex.defaultMultitenant()
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
