/*
 * Copyright 2026, TeamDev. All rights reserved.
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

package io.spine.server.entity;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import io.spine.annotation.Internal;
import io.spine.core.Event;

/**
 * The list of uncommitted events of a {@link SignalDispatchingEntity}.
 */
@Internal
@Immutable
public final class UncommittedEvents {

    private static final UncommittedEvents EMPTY = new UncommittedEvents(ImmutableList.of());

    private final ImmutableList<Event> events;

    private UncommittedEvents(ImmutableList<Event> events) {
        this.events = events;
    }

    /**
     * Returns an empty list of events.
     */
    static UncommittedEvents ofNone() {
        return EMPTY;
    }

    /**
     * Tells whether or not this {@code UncommittedEvents} list is empty.
     *
     * @return {@code true} if the list is not empty, {@code false} otherwise
     */
    public boolean nonEmpty() {
        return !events.isEmpty();
    }

    /**
     * Obtains the list of events.
     *
     * @return the list of uncommitted events
     */
    public ImmutableList<Event> list() {
        return events;
    }

    /**
     * Creates a new instance of {@code UncommittedEvents} with this {@link #list() list} appended
     * with the given {@code events}.
     *
     * <p>If this {@code UncommittedEvents} list is {@link #nonEmpty()} or the given
     * {@code Iterable} is not empty, then the resulting instance is {@link #nonEmpty()} as well.
     *
     * @param newEvents the events to append
     * @return new {@code UncommittedEvents} instance
     */
    UncommittedEvents append(Iterable<Event> newEvents) {
        var newList = ImmutableList.<Event>builder()
                .addAll(events)
                .addAll(newEvents)
                .build();
        return new UncommittedEvents(newList);
    }
}
