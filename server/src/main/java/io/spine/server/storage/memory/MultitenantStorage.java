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

package io.spine.server.storage.memory;

import io.spine.core.TenantId;
import io.spine.server.tenant.TenantFunction;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static java.util.Collections.synchronizedMap;
import static java.util.Objects.requireNonNull;

/**
 * The multitenant storage.
 *
 * @param <S> the type of the storage "slice" for each tenant
 */
abstract class MultitenantStorage<S extends TenantDataStorage<?, ?>> {

    /** The lock for {@code MultitenantStorage} accessor methods. */
    private final Lock lock = new ReentrantLock();

    /** The map from {@code TenantId} to its slice of data. */
    private final Map<TenantId, S> tenantSlices = synchronizedMap(new HashMap<>());

    /** If {@code true} the storage will contain a data slice for each tenant. */
    private final boolean multitenant;

    MultitenantStorage(boolean multitenant) {
        this.multitenant = multitenant;
    }

    /**
     * Obtains the data slice for the current tenant.
     *
     * <p>If the slice has not been created for this tenant, it will be created.
     */
    final S currentSlice() {
        var func = new TenantFunction<S>(isMultitenant()) {
            @Override
            public @Nullable S apply(@Nullable TenantId tenantId) {
                requireNonNull(tenantId);
                lock.lock();
                try {
                    return tenantSlices.computeIfAbsent(tenantId, id -> createSlice());
                } finally {
                    lock.unlock();
                }
            }
        };
        var result = func.execute();
        requireNonNull(result, "Current tenant slice is `null`.");
        return result;
    }

    abstract S createSlice();

    final boolean isMultitenant() {
        return multitenant;
    }
}
