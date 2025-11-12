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
     * The ID of the user which is used for generating system commands and events.
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
