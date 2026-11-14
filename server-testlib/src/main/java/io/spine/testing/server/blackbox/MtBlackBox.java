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

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.spine.core.Command;
import io.spine.core.Event;
import io.spine.core.TenantId;
import io.spine.server.Application;
import io.spine.server.tenant.TenantAwareRunner;
import io.spine.testing.client.TestActorRequestFactory;
import io.spine.testing.server.blackbox.probe.CommandCollector;
import io.spine.testing.server.blackbox.probe.EventCollector;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * Test fixture for multi-tenant Bounded Contexts.
 */
final class MtBlackBox extends BlackBox {

    private @MonotonicNonNull TenantId tenantId;

    /**
     * Creates a new multi-tenant instance over the only context of the given application.
     */
    MtBlackBox(Application application) {
        super(application);
    }

    /**
     * Switches the bounded context to operate on behalf of the specified tenant.
     *
     * <p>Registers the tenant in the {@linkplain Application#tenantIndex() tenant index} of
     * the application, so that the {@linkplain #clients() clients} of the black box may send
     * requests on behalf of the tenant.
     *
     * @param tenant
     *         new tenant ID
     * @return current instance
     */
    @Override
    @CanIgnoreReturnValue
    public MtBlackBox withTenant(TenantId tenant) {
        this.tenantId = checkNotNull(tenant);
        application().tenantIndex()
                     .keep(tenant);
        return this;
    }

    @Override
    @SuppressWarnings("TestOnlyProblems")   /* `TestActorRequestFactory` is not test-only. */
    TestActorRequestFactory requestFactory() {
        return actor().requestsFor(tenantId());
    }

    @Override
    ImmutableList<Command> select(CommandCollector collector) {
        return collector.ofTenant(tenantId());
    }

    @Override
    ImmutableList<Event> select(EventCollector collector) {
        return collector.ofTenant(tenantId());
    }

    @Override
    protected <D extends @Nullable Object> D readOperation(Supplier<D> supplier) {
        var tenantAwareRunner = TenantAwareRunner.with(tenantId());
        var result = tenantAwareRunner.evaluate(() -> super.readOperation(supplier));
        return result;
    }

    private TenantId tenantId() {
        checkState(tenantId != null,
                   "Set a tenant ID before calling receive and assert methods");
        return tenantId;
    }
}
