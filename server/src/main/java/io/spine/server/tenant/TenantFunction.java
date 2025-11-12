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

import io.spine.annotation.Internal;
import io.spine.core.TenantId;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;

/**
 * A function which can work in single-tenant and multi-tenant context and return a
 * value depending on the current tenant set.
 *
 * @param <T> the type of the result returned by the function
 */
@Internal
public abstract class TenantFunction<T> extends TenantAware implements Function<TenantId, T> {

    /**
     * Creates a new instance of the function.
     *
     * @param multitenant if {@code true} the function is executed in the multi-tenant context,
     *                    {@code false} for single-tenant context
     */
    protected TenantFunction(boolean multitenant) {
        super(currentTenant(multitenant));
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
