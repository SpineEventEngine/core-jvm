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
package io.spine.core;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.Any;
import com.google.protobuf.Message;
import io.spine.annotation.SPI;
import io.spine.base.EventMessage;
import io.spine.string.Stringifier;
import io.spine.string.StringifierRegistry;

import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.spine.base.Identifier.newUuid;
import static io.spine.protobuf.Messages.ensureUnpacked;
import static io.spine.util.Preconditions2.checkNotDefaultArg;

/**
 * Utility class for working with {@link Event} objects.
 */
@SPI
public final class Events {

    /** The stringifier for event IDs. */
    private static final Stringifier<EventId> idStringifier = new EventIdStringifier();

    static {
        StringifierRegistry.instance()
                           .register(idStringifier(), EventId.class);
    }

    /** Prevents instantiation of this utility class. */
    private Events() {
    }

    /**
     * Creates a new {@link EventId} based on random UUID.
     *
     * @return new UUID-based event ID
     */
    public static EventId generateId() {
        var value = newUuid();
        return EventId.newBuilder()
                      .setValue(value)
                      .build();
    }

    /**
     * Extracts an event message if the passed instance is an {@link Event} object or {@link Any},
     * otherwise returns the passed message.
     */
    public static EventMessage ensureMessage(Message eventOrMessage) {
        checkNotNull(eventOrMessage);
        if (eventOrMessage instanceof Event) {
            return ((Event) eventOrMessage).enclosedMessage();
        }
        var unpacked = ensureUnpacked(eventOrMessage);
        return (EventMessage) unpacked;
    }

    /**
     * Obtains the stringifier for event IDs.
     */
    public static Stringifier<EventId> idStringifier() {
        return idStringifier;
    }

    /**
     * Ensures that the passed ID is valid.
     *
     * @param id
     *         an ID to check
     * @throws IllegalArgumentException
     *         if the ID string value is empty or blank
     * @deprecated please use {@link io.spine.util.Preconditions2#checkNotDefaultArg(Message)}
     */
    @Deprecated
    public static EventId checkValid(EventId id) {
        checkNotDefaultArg(id);
        return id;
    }

    /**
     * Creates an empty {@link Iterable} over the messages of the type {@code <M>}.
     *
     * <p>This method is useful for returning empty result from reacting methods.
     *
     * @param <M>
     *         the type of messages
     * @return empty {@link Iterable}
     */
    public static <M extends EventMessage> Iterable<M> nothing() {
        return ImmutableList.of();
    }

    /**
     * Marks all the given events as {@code external}.
     *
     * <p>The order of the events is preserved in the output list.
     */
    public static ImmutableList<Event> toExternal(List<Event> events) {
        return events
                .stream()
                .map(Events::toExternal)
                .collect(toImmutableList());
    }

    /**
     * Marks the given event as {@code external}.
     */
    public static Event toExternal(Event event) {
        var externalEvent = event.toBuilder();
        externalEvent.getContextBuilder().setExternal(true);
        return externalEvent.build();
    }

    /**
     * The stringifier of event IDs.
     */
    static class EventIdStringifier extends Stringifier<EventId> {

        @Override
        protected String toString(EventId eventId) {
            var result = eventId.getValue();
            return result;
        }

        @Override
        protected EventId fromString(String str) {
            var result = EventId.newBuilder()
                    .setValue(str)
                    .build();
            return result;
        }
    }
}
