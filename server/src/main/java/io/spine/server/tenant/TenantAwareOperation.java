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

import io.spine.annotation.SPI;
import io.spine.core.TenantId;

/**
 * An abstract base for operations on a tenant data.
 *
 * @see #execute()
 */
@SPI
public abstract class TenantAwareOperation extends TenantAware implements Runnable {

    /**
     * Creates an instance of an operation that uses the {@code TenantId}
     * set in the execution context.
     *
     * @throws IllegalStateException if there is no current {@code TenantId} set
     */
    protected TenantAwareOperation() throws IllegalStateException {
        super();
    }

    /**
     * Creates an instance of an operation for the tenant specified by the passed ID.
     *
     * <p>If a default instance of {@link TenantId} is passed (because the application works in
     * a single-tenant mode), {@linkplain SingleTenantIndex#tenantId() singleTenant()} value will be
     * substituted.
     *
     * @param tenantId the tenant ID or {@linkplain TenantId#getDefaultInstance() default value}
     */
    protected TenantAwareOperation(TenantId tenantId) {
        super(tenantId);
    }

    /**
     * Executes the operation.
     *
     * <p>The execution goes through the following steps:
     * <ol>
     *     <li>The current tenant ID is obtained and remembered.
     *     <li>The tenant ID passed to the constructor is set as current.
     *     <li>The {@link #run()} method is called.
     *     <li>The previous tenant ID is set as current.
     * </ol>
     */
    public void execute() {
        var remembered = CurrentTenant.get();
        try {
            CurrentTenant.set(tenantId());
            run();
        } finally {
            if (remembered.isPresent()) {
                CurrentTenant.set(remembered.get());
            } else {
                CurrentTenant.clear();
            }
        }
    }
}
