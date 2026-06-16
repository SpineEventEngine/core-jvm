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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.spine.testing.TestValues.nullRef;
import static io.spine.testing.core.given.GivenTenantId.generate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("`TenantAwareOperation` should")
@SuppressWarnings("OptionalGetWithoutIsPresent") /* We set right before we `get()`. */
class TenantAwareOperationTest {

    @Test
    @DisplayName("not accept null tenant")
    void notAcceptNullTenant() {
        assertThrows(NullPointerException.class, () -> createOperation(nullRef()));
    }

    @Test
    @DisplayName("substitute single tenant for default value")
    void useSingleTenantId() {
        TenantAwareOperation op = createOperation(TenantId.getDefaultInstance());
        assertEquals(SingleTenantIndex.tenantId(), op.tenantId());
    }

    @Test
    @DisplayName("remember and restore current tenant")
    void rememberCurrentTenant() {
        var previousTenant = generate();
        CurrentTenant.set(previousTenant);

        var newTenant = generate();
        TenantAwareOperation op = createOperation(newTenant);

        // Check that the construction of the operation does not change the current tenant.
        assertEquals(previousTenant, CurrentTenant.get().get());

        op.execute();

        // Check that we got right value inside run().
        assertEquals(newTenant, getTenantFromRun(op));

        // Check that the current tenant is restored.
        assertEquals(previousTenant, CurrentTenant.get().get());
    }

    @Test
    @DisplayName("clear current tenant on restoring if no tenant was set")
    void clearCurrentTenant() {
        // Make sure there's not current tenant.
        CurrentTenant.clear();

        // Create new operation.
        var newTenant = generate();
        TenantAwareOperation op = createOperation(newTenant);

        // Check that the construction did not set the tenant.
        assertFalse(CurrentTenant.get()
                                 .isPresent());

        op.execute();

        // Check that the execution was on the correct tenant.
        assertEquals(newTenant, getTenantFromRun(op));

        // Check that the execution cleared the current tenant.
        assertFalse(CurrentTenant.get()
                                 .isPresent());
    }

    @Test
    @DisplayName("create instance for non-command execution context")
    void createForNonCommand() {
        var tenant = generate();
        CurrentTenant.set(tenant);

        TenantAwareOperation op = createOperation();

        assertEquals(tenant, op.tenantId());

        op.execute();

        assertEquals(tenant, getTenantFromRun(op));
    }

    @Test
    @DisplayName("not allow creating instance for non-command execution without current tenant")
    void rejectNonCommandIfNoCurrentTenant() {
        CurrentTenant.clear();

        assertThrows(IllegalStateException.class, TenantAwareOperationTest::createOperation);
    }

    /*
     * Test environment utilities.
     *********************************/

    private static TestOp createOperation(TenantId newTenant) {
        return new TestOp(newTenant);
    }

    private static TestOp createOperation() {
        return new TestOp();
    }

    private static TenantId getTenantFromRun(TenantAwareOperation op) {
        return ((TestOp) op).tenantInRun;
    }

    /**
     * The tenant data operation that remembers the current tenant in {@link #run()}.
     */
    private static class TestOp extends TenantAwareOperation {

        private TenantId tenantInRun;

        private TestOp(TenantId tenantId) {
            super(tenantId);
        }

        private TestOp() {
            super();
        }

        @Override
        public void run() {
            tenantInRun = CurrentTenant.get().get();
        }
    }
}
