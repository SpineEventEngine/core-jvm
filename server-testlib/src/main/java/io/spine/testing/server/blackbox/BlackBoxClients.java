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

package io.spine.testing.server.blackbox;

import io.spine.client.Client;
import io.spine.core.TenantId;
import org.jspecify.annotations.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Creates {@link Client}s for a particular {@link BlackBox} instance.
 */
public final class BlackBoxClients {

    private final BlackBox blackBox;
    private final ClientFactory supplier;

    /**
     * Creates a new instance of this factory.
     *
     * @param blackBox
     *         the instance to connect to
     * @param supplier
     *         supplier of {@code Client} instances
     */
    BlackBoxClients(BlackBox blackBox, ClientFactory supplier) {
        this.supplier = supplier;
        this.blackBox = blackBox;
    }

    /**
     * Creates a new {@code Client} with the specified {@code TenantId}.
     *
     * <p>This method must be called to connect only to the multi-tenant Bounded Contexts.
     *
     * @return a new instance of {@code Client}
     * @throws IllegalStateException
     *         if the method is called for a single-tenant Context
     */
    public Client create(TenantId tenant) {
        checkNotNull(tenant);
        var result = supplier.create(tenant);
        return result;
    }

    /**
     * Creates a new {@code Client} with the {@code TenantId} which corresponds to
     * the <b>current</b> setting of the target {@code BlackBox}.
     *
     * @return a new instance of {@code Client}
     */
    public Client withMatchingTenant() {
        var requests = blackBox.requestFactory();
        @Nullable TenantId tenantId = requests.tenantId();
        var result = supplier.create(tenantId);
        return result;
    }
}
