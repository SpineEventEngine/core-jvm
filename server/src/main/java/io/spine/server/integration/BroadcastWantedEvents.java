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

package io.spine.server.integration;

import com.google.common.collect.ImmutableSet;
import io.spine.core.BoundedContextName;
import io.spine.server.transport.Publisher;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Notifies other Bounded Contexts that this Bounded Context now requests some set of external
 * domain events for consumption.
 */
final class BroadcastWantedEvents {

    private final BoundedContextName context;
    private final Publisher publisher;

    private ImmutableSet<ExternalEventType> wantedEvents = ImmutableSet.of();

    /**
     * Creates a new instance of this broadcast.
     *
     * @param context
     *         the name of the Bounded Context from which the broadcast is performed
     * @param channel
     *         the channel for broadcasting
     */
    BroadcastWantedEvents(BoundedContextName context, Publisher channel) {
        this.context = checkNotNull(context);
        this.publisher = checkNotNull(channel);
    }

    /**
     * Notifies other Bounded Contexts about a change in the types of wanted events.
     *
     * <p>If the given {@code newTypes} are the same as those known to this instance previously,
     * the notification is not sent.
     *
     * @param newTypes
     *         types of external events that are consumed by the bounded context
     */
    synchronized void onEventsChanged(ImmutableSet<ExternalEventType> newTypes) {
        checkNotNull(newTypes);
        if (wantedEvents.equals(newTypes)) {
            return;
        }
        wantedEvents = newTypes;
        send();
    }

    /**
     * Notifies other Bounded Contexts about the domain events for which
     * it has {@code external} subscribers.
     */
    synchronized void send() {
        var request = ExternalEventsWanted.newBuilder()
                .addAllType(wantedEvents)
                .build();
        var wrapped = ExternalMessages.of(request, context);
        publisher.publish(wrapped.getId(), wrapped);
    }
}
