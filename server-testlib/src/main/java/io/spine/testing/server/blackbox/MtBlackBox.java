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

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.spine.core.Command;
import io.spine.core.Event;
import io.spine.core.TenantId;
import io.spine.server.BoundedContext;
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
     * Creates a new multi-tenant instance.
     */
    MtBlackBox(BoundedContext c) {
        super(c);
    }

    /**
     * Switches the bounded context to operate on behalf of the specified tenant.
     *
     * @param tenant
     *         new tenant ID
     * @return current instance
     */
    @Override
    @CanIgnoreReturnValue
    public MtBlackBox withTenant(TenantId tenant) {
        this.tenantId = checkNotNull(tenant);
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
