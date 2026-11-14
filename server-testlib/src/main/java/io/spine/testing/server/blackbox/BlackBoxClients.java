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

package io.spine.testing.server.blackbox;

import io.spine.client.Client;
import io.spine.core.TenantId;

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
     * <p>The method does not register the tenant in the application of the context, so that
     * a test can check how a request of an unregistered tenant is refused. The requests of
     * the client are served once the tenant is registered, for example, with
     * {@link BlackBox#withTenant(TenantId) BlackBox.withTenant()}.
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
     * Creates a new {@code Client} with the {@code TenantId} that corresponds to
     * the <b>current</b> setting of the target {@code BlackBox}.
     *
     * @return a new instance of {@code Client}
     */
    public Client withMatchingTenant() {
        var requests = blackBox.requestFactory();
        var tenantId = requests.tenantId();
        var result = supplier.create(tenantId);
        return result;
    }
}
