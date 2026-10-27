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
import io.spine.core.UserId;
import io.spine.core.Versions;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.grpc.StreamObservers.noOpObserver;
import static io.spine.system.server.SystemEventFactory.forMessage;

/**
 * The default implementation of {@link SystemWriteSide}.
 */
final class DefaultSystemWriteSide implements SystemWriteSide {

    /**
     * The ID of the user that is used for generating system commands and events.
     */
    static final UserId SYSTEM_USER = UserId
            .newBuilder()
            .setValue("SYSTEM")
            .build();

    private final SystemContext system;

    DefaultSystemWriteSide(SystemContext system) {
        this.system = system;
    }

    @CanIgnoreReturnValue
    @Override
    public Event postEvent(EventMessage systemEvent, Origin origin) {
        checkNotNull(systemEvent);
        checkNotNull(origin);

        var event = event(systemEvent, origin);
        var config = system.config();

        if (config.postEventsInParallel()) {
            config.postingExecutor().execute(() -> postEvent(event));
        } else {
            postEvent(event);
        }

        return event;
    }

    @CanIgnoreReturnValue
    @Override
    public Event postEventNow(EventMessage systemEvent) {
        checkNotNull(systemEvent);
        var event = event(systemEvent, Origin.getDefaultInstance());
        postEvent(event);
        return event;
    }

    private Event event(EventMessage message, Origin origin) {
        var factory = forMessage(message, origin, system.isMultitenant());
        var event = factory.createEvent(message, Versions.zero());
        return event;
    }

    private void postEvent(Event event) {
        system.eventBus()
              .post(event, noOpObserver());
    }

    @Override
    public SystemFeatures features() {
        return system.config();
    }
}
