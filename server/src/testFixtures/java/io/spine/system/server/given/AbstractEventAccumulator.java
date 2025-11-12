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

package io.spine.system.server.given;

import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.Message;
import io.spine.base.EventMessage;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.event.EventDispatcher;
import io.spine.server.type.EventClass;
import io.spine.server.type.EventEnvelope;

import java.util.List;

import static com.google.common.collect.Lists.newArrayList;
import static io.spine.server.dispatch.DispatchOutcomes.successfulOutcome;
import static java.lang.String.format;
import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * An {@link EventDispatcher} which can {@linkplain #remember remember} received events.
 */
public abstract class AbstractEventAccumulator implements EventDispatcher {

    private final List<EventMessage> events = newArrayList();
    private final List<EventMessage> nonCheckedEvents = newArrayList();

    /**
     * {@inheritDoc}
     *
     * <p>Remembers the dispatched event.
     *
     * <p>The returned ID set is always a single-item set. The item is the fully-qualified name of
     * the class of this dispatcher.
     */
    @CanIgnoreReturnValue
    @Override
    public final DispatchOutcome dispatch(EventEnvelope event) {
        var msg = event.message();
        remember(msg);
        return successfulOutcome(event);
    }

    @Override
    public final ImmutableSet<EventClass> messageClasses() {
        return eventClasses();
    }

    @Override
    public final ImmutableSet<EventClass> domesticEventClasses() {
        return eventClasses();
    }

    @Override
    public ImmutableSet<EventClass> externalEventClasses() {
        return EventClass.emptySet();
    }

    public void assertEventCount(int expectedCount) {
        assertEquals(expectedCount, events.size(), errorMessage());
    }

    public void forgetEvents() {
        events.clear();
        nonCheckedEvents.clear();
    }

    /**
     * Checks that an event with the given type was accumulated.
     *
     * <p>If the event is found, it is removed from the accumulated events, so that it is never
     * found twice.
     *
     * <p>Throws an assertion error if the event is not found.
     *
     * @param eventType
     *         the class of the event
     * @param <E>
     *         the type of the event
     * @return the found event
     */
    @CanIgnoreReturnValue
    public <E extends Message> E assertReceivedEvent(Class<E> eventType) {
        assertFalse(nonCheckedEvents.isEmpty(), errorMessage());
        var event = nonCheckedEvents.stream()
                .filter(eventType::isInstance)
                .findFirst()
                .orElseGet(() -> fail(errorMessage()));
        nonCheckedEvents.remove(event);
        @SuppressWarnings("unchecked")
        var result = (E) event;
        return result;
    }

    private void remember(EventMessage event) {
        events.add(event);
        nonCheckedEvents.add(event);
    }

    private String errorMessage() {
        return format("Actual events are: %s",
                      events.stream()
                              .map(Object::getClass)
                              .map(Class::getSimpleName)
                              .collect(joining(" -> ")));
    }
}
