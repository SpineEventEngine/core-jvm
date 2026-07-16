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

package io.spine.server.entity;

import com.google.common.collect.ImmutableSet;
import io.spine.base.RejectionThrowable;
import io.spine.core.Event;
import io.spine.server.event.EventBus;
import io.spine.server.type.CommandEnvelope;
import io.spine.server.type.EventClass;
import io.spine.server.type.SignalEnvelope;

import java.util.Collection;

import static io.spine.server.event.RejectionFactory.reject;

/**
 * Operations common for repositories that can post to {@link #eventBus() EventBus}.
 */
public interface EventProducingRepository {

    /**
     * Obtains classes of the events produced by entities of this repository.
     */
    ImmutableSet<EventClass> outgoingEvents();

    /**
     * Obtains the {@code EventBus} to which the repository posts.
     */
    EventBus eventBus();

    /**
     * Declared for mixing-in with {@link Repository#eventFilter()}.
     */
    EventFilter eventFilter();

    /**
     * Filters passed events using the {@linkplain #eventFilter()} filter} of this repository.
     */
    default Iterable<Event> filter(Collection<Event> events) {
        Iterable<Event> filtered = eventFilter().filter(events);
        return filtered;
    }

    /**
     * Filters the passed events and posts the result to the EventBus.
     */
    @SuppressWarnings("resource") // Accessing `Closeable` `EventBus`.
    default void postEvents(Collection<Event> events) {
        var filtered = filter(events);
        eventBus().post(filtered);
    }

    /**
     * If the passed signal is a command and the thrown cause is a rejection,
     * posts the rejection to the associated {@link #eventBus() EventBus}.
     *
     * <p>Otherwise, does nothing.
     */
    default void postIfCommandRejected(SignalEnvelope<?, ?, ?> signal, Throwable cause) {
        if (signal instanceof CommandEnvelope ce && cause instanceof RejectionThrowable) {
            var command = ce.outerObject();
            var rejection = reject(command, cause);
            postEvents(rejection.toSet());
        }
    }
}
