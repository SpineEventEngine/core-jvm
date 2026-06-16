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

import com.google.common.collect.ImmutableCollection;
import io.spine.annotation.SPI;
import io.spine.base.EventMessage;
import io.spine.core.Event;

import java.util.Collection;
import java.util.Optional;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.spine.protobuf.AnyPacker.pack;

/**
 * A filter accepting {@link Event}s posted by a {@link Repository}.
 *
 * <p>A filter may {@linkplain #allowAll() allow any event}, reject certain types of events, or
 * change the event content.
 *
 * @apiNote This type is a {@link FunctionalInterface}, so that an event filter may be
 *         defined with a lambda expression.
 */
@SPI
@FunctionalInterface
public interface EventFilter {

    /**
     * Obtains an {@code EventFilter} that always returns the input event without any change.
     *
     * <p>The method acts as if
     * <pre>
     *     {@code
     *     static EventFilter allowAll() {
     *         return Optional::of;
     *     }
     *     }
     * </pre>
     *
     * @return a filter that allows any event to be published
     */
    static EventFilter allowAll() {
        return NoOpEventFilter.INSTANCE;
    }

    /**
     * Applies this filter to the given {@linkplain io.spine.base.EventMessage event}.
     *
     * @param event
     *         the event to apply the filter to
     * @return processed event or {@link Optional#empty()} if the event should not be posted
     * @apiNote This method may never return a present value or return a value not derived
     *         from the input event. See the implementations for the details for each case.
     */
    Optional<? extends EventMessage> filter(EventMessage event);

    /**
     * Applies this filter to the given {@link Event}s in bulk.
     *
     * @param events
     *         the events to apply the filter to
     * @return non-empty filtering results for the given events
     * @apiNote This method should have the same behaviour in any descendant.
     *         Override this method <b>only</b> for performance improvement.
     */
    default ImmutableCollection<Event> filter(Collection<Event> events) {
        ImmutableCollection<Event> filteredEvents = events
                .stream()
                .map(event -> {
                    var eventMessage = event.enclosedMessage();
                    var filtered = filter(eventMessage);
                    var result = filtered.map(message -> event.toBuilder()
                            .setMessage(pack(message))
                            .build());
                    return result;
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(toImmutableList());
        return filteredEvents;
    }
}
