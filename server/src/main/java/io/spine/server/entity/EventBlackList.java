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
import io.spine.base.EventMessage;
import io.spine.server.type.EventClass;

import java.util.Optional;

/**
 * An {@link EventFilter} that allows any events except for the events of given types.
 *
 * @see EventWhiteList
 */
public final class EventBlackList implements EventFilter {
    
    private final ImmutableSet<EventClass> forbiddenEvents;

    private EventBlackList(ImmutableSet<EventClass> forbiddenEvents) {
        this.forbiddenEvents = forbiddenEvents;
    }

    /**
     * Creates a new instance of {@code EventBlackList} discarding events of the given types.
     *
     * @param eventClasses
     *         the list of event classes which cannot be used
     * @return new instance of the black-list filter
     */
    @SafeVarargs
    public static EventBlackList discardEvents(Class<? extends EventMessage>... eventClasses) {
        var classes = EventClass.setOf(eventClasses);
        return new EventBlackList(classes);
    }

    @Override
    public Optional<? extends EventMessage> filter(EventMessage event) {
        var type = EventClass.of(event);
        return forbiddenEvents.contains(type)
               ? Optional.empty()
               : Optional.of(event);
    }
}
