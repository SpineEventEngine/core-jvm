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

package io.spine.system.server;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.spine.base.EventMessage;
import io.spine.core.Event;
import io.spine.core.Origin;
import io.spine.core.TenantId;
import io.spine.server.tenant.TenantAwareOperation;
import io.spine.server.tenant.TenantAwareRunner;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A {@link SystemWriteSide} that works for a specified tenant.
 *
 * <p>A system event is {@linkplain SystemWriteSide#postEvent(EventMessage, Origin) posted} within
 * a {@link TenantAwareOperation} with the given tenant set.
 *
 * <p>Any call to this {@code TenantAwareSystemWriteSide} is delegated to another instance
 * of {@link SystemWriteSide} passed on construction.
 *
 * @see TenantAwareOperation
 */
final class TenantAwareSystemWriteSide implements SystemWriteSide {

    private final SystemWriteSide delegate;
    private final TenantAwareRunner runner;

    private TenantAwareSystemWriteSide(TenantId tenantId, SystemWriteSide delegate) {
        this.runner = TenantAwareRunner.with(checkNotNull(tenantId));
        this.delegate = checkNotNull(delegate);
    }

    static SystemWriteSide forTenant(TenantId tenantId, SystemWriteSide systemWriteSide) {
        checkNotNull(tenantId);
        checkNotNull(systemWriteSide);
        SystemWriteSide result = new TenantAwareSystemWriteSide(tenantId, systemWriteSide);
        return result;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Posts the given system event under the context of the specified tenant.
     */
    @CanIgnoreReturnValue
    @Override
    public Event postEvent(EventMessage systemEvent, Origin origin) {
        var event = runner.evaluate(() -> delegate.postEvent(systemEvent, origin));
        return event;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Posts the given system event under the context of the specified tenant.
     */
    @CanIgnoreReturnValue
    @Override
    public Event postEventNow(EventMessage systemEvent) {
        var event = runner.evaluate(() -> delegate.postEventNow(systemEvent));
        return event;
    }

    @Override
    public SystemFeatures features() {
        return delegate.features();
    }
}
