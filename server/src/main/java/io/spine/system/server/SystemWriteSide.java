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

package io.spine.system.server;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.spine.annotation.Internal;
import io.spine.base.EventMessage;
import io.spine.core.Event;
import io.spine.core.Origin;
import io.spine.server.tenant.TenantAwareOperation;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The write side of a system bounded context.
 *
 * <p>A domain context posts messages to its system counterpart via a {@code SystemWriteSide}.
 */
@Internal
public interface SystemWriteSide {

    /**
     * Posts a system event with the given origin.
     *
     * <p>If the associated bounded context is
     * {@linkplain io.spine.server.BoundedContext#isMultitenant() multitenant}, the event is
     * posted for the {@linkplain io.spine.server.tenant.TenantAwareOperation current tenant}.
     *
     * @param systemEvent
     *         event to post
     * @param origin
     *         the origin of the event
     * @return a posted {@code Event} instance
     */
    @CanIgnoreReturnValue
    Event postEvent(EventMessage systemEvent, Origin origin);

    /**
     * Posts a system event.
     *
     * <p>If the associated bounded context is
     * {@linkplain io.spine.server.BoundedContext#isMultitenant() multitenant}, the event is
     * posted for the {@linkplain TenantAwareOperation current tenant}.
     *
     * @param systemEvent
     *         event to post
     * @return a posted {@code Event} instance
     *
     * @see #postEvent(EventMessage, Origin)
     */
    @CanIgnoreReturnValue
    default Event postEvent(EventMessage systemEvent) {
        return postEvent(systemEvent, Origin.getDefaultInstance());
    }

    /**
     * Creates new instance of the {@code SystemWriteSide} which serves the passed system context.
     */
    static SystemWriteSide newInstance(SystemContext system) {
        checkNotNull(system);
        return new DefaultSystemWriteSide(system);
    }

    /**
     * Returns the original set of features enabled for the parent {@code SystemContext}.
     */
    SystemFeatures features();
}
