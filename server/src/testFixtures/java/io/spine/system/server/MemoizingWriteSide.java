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

import io.spine.base.EventMessage;
import io.spine.core.Event;
import io.spine.core.Origin;
import io.spine.core.TenantId;
import io.spine.server.tenant.TenantFunction;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.util.Exceptions.newIllegalStateException;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A {@link SystemWriteSide} that memoizes the posted system commands.
 *
 * <p>This class is a test-only facility, used in order to avoid mocking {@link SystemWriteSide}
 * instances.
 */
public final class MemoizingWriteSide implements SystemWriteSide {

    private @MonotonicNonNull MemoizedSystemMessage lastSeenEvent;

    private final boolean multitenant;

    private MemoizingWriteSide(boolean multitenant) {
        this.multitenant = multitenant;
    }

    /**
     * Creates a new instance of {@code MemoizingWriteSide} for a single-tenant execution
     * environment.
     *
     * @return new {@code MemoizingWriteSide}
     */
    public static MemoizingWriteSide singleTenant() {
        return new MemoizingWriteSide(false);
    }

    /**
     * Creates a new instance of {@code MemoizingWriteSide} for a multitenant execution environment.
     *
     * @return new {@code MemoizingWriteSide}
     */
    public static MemoizingWriteSide multitenant() {
        return new MemoizingWriteSide(true);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Memoizes the given event message and the {@link TenantId} that it was posted for.
     *
     * @see #lastSeenEvent()
     */
    @Override
    public Event postEvent(EventMessage systemEvent, Origin origin) {
        var tenantId = currentTenant();
        lastSeenEvent = new MemoizedSystemMessage(systemEvent, tenantId);
        return Event.getDefaultInstance();
    }

    /** Obtains the ID of the current tenant. */
    private TenantId currentTenant() {
        var result = new TenantFunction<TenantId>(multitenant) {
            @Override
            public TenantId apply(TenantId id) {
                return id;
            }
        }.execute();
        return checkNotNull(result);
    }

    /**
     * Obtains the last event message posted to {@link SystemWriteSide}.
     *
     * <p>Fails if no events were posted yet.
     */
    public MemoizedSystemMessage lastSeenEvent() {
        assertNotNull(lastSeenEvent);
        return lastSeenEvent;
    }

    /**
     * Tells if any event was posted to {@link SystemWriteSide}.
     */
    public boolean hasSeenEvents() {
        return lastSeenEvent != null;
    }

    @Override
    public SystemFeatures features() {
        throw newIllegalStateException(
                "This implementation of `SystemWriteSide` does not provide `features()`."
        );
    }
}
