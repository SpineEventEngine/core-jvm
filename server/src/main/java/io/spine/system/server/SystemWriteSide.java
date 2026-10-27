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
     * Posts a system event before returning, even if the system context
     * {@linkplain SystemFeatures#postEventsInParallel() posts its events in parallel}.
     *
     * <p>Use it for an event whose outcome the caller relies on right after the call.
     *
     * <p>By default, posts the event as {@link #postEvent(EventMessage)} does.
     *
     * @param systemEvent
     *         event to post
     * @return a posted {@code Event} instance
     */
    @CanIgnoreReturnValue
    default Event postEventNow(EventMessage systemEvent) {
        return postEvent(systemEvent);
    }

    /**
     * Creates new instance of the {@code SystemWriteSide} that serves the passed system context.
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
