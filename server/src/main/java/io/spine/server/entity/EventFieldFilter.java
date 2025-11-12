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
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.FieldMask;
import io.spine.base.EventMessage;
import io.spine.core.Event;
import io.spine.server.type.EventClass;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static io.spine.protobuf.AnyPacker.pack;
import static io.spine.protobuf.Messages.isDefault;
import static io.spine.server.entity.FieldMasks.applyMask;

/**
 * An {@link EventFilter} which allows all the events but trims some of their message fields.
 *
 * <p>By default, the input event is returned unchanged. Specify a {@link FieldMask} for an event
 * type to trim certain fields off the event messages. The fields in the mask are
 * retained, all the others are cleared.
 *
 * <p>Note that the mask should contain all the {@code (required) = true} fields. Otherwise,
 * the event will not be acknowledged by the bus.
 */
public final class EventFieldFilter implements EventFilter {

    private final ImmutableMap<EventClass, FieldMask> fieldMasks;

    private EventFieldFilter(Builder builder) {
        this.fieldMasks = ImmutableMap.copyOf(builder.masks);
    }

    @Override
    public Optional<? extends EventMessage> filter(EventMessage event) {
        var masked = mask(event);
        return Optional.of(masked);
    }

    @Override
    public ImmutableCollection<Event> filter(Collection<Event> events) {
        return events.stream()
                .map(this::maskEvent)
                .collect(toImmutableList());
    }

    private Event maskEvent(Event event) {
        var message = event.enclosedMessage();
        var masked = mask(message);
        return event.toBuilder()
                .setMessage(pack(masked))
                .build();
    }

    private EventMessage mask(EventMessage event) {
        var eventClass = EventClass.of(event);
        var mask = fieldMasks.get(eventClass);
        if (mask == null || isDefault(mask)) {
            return event;
        } else {
            var maskedEvent = applyMask(mask, event);
            return maskedEvent;
        }
    }

    /**
     * Creates a new instance of {@code Builder} for {@code EventFieldFilter} instances.
     *
     * @return new instance of {@code Builder}
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * A builder for the {@code EventFieldFilter} instances.
     */
    public static final class Builder {

        private final Map<EventClass, FieldMask> masks = new HashMap<>();

        /**
         * Prevents direct instantiation.
         */
        private Builder() {
        }

        /**
         * Specifies the {@link FieldMask} for the given event type.
         *
         * @param eventClass
         *         the type of the event to mask
         * @param mask
         *         the fields to <b>retain</b> in the event message
         * @return self for method chaining
         */
        @CanIgnoreReturnValue
        public Builder putMask(Class<? extends EventMessage> eventClass, FieldMask mask) {
            checkNotNull(eventClass);
            checkNotNull(mask);
            var eventType = EventClass.from(eventClass);
            masks.put(eventType, mask);
            return this;
        }

        /**
         * Creates a new instance of {@code EventFieldFilter}.
         *
         * @return new instance of {@code EventFieldFilter}
         */
        public EventFieldFilter build() {
            return new EventFieldFilter(this);
        }
    }
}
