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

package io.spine.server.aggregate;

import com.google.common.collect.ImmutableList;
import io.spine.core.Event;

import java.util.ArrayList;
import java.util.List;

/**
 * The events produced by an aggregate during the current dispatch that are not yet stored.
 *
 * <p>Since the event-sourcing cutover, an aggregate no longer replays events to rebuild its
 * state, so this class no longer segments the events by snapshots — it is a plain, ordered list
 * of the events emitted by the current command or reaction. The framework
 * {@linkplain Aggregate#recordEvents(List) records} the produced events here after a successful
 * dispatch, stores them into the append-only journal alongside the latest state record,
 * and then {@link #commit() commits}.
 */
final class UncommittedHistory {

    private final List<Event> events = new ArrayList<>();

    /**
     * Records the events produced during the current dispatch.
     *
     * <p>Rejection events are not journaled and are ignored.
     *
     * @param produced
     *         the events emitted by the current command handler or reactor
     * @return the events kept for journaling by this call, in the order of emission
     */
    List<Event> record(Iterable<Event> produced) {
        var kept = ImmutableList.<Event>builder();
        for (var event : produced) {
            if (!event.isRejection()) {
                events.add(event);
                kept.add(event);
            }
        }
        return kept.build();
    }

    /**
     * Obtains the uncommitted events as an immutable list.
     *
     * <p>The returned list is empty when there are no uncommitted events.
     */
    List<Event> get() {
        return ImmutableList.copyOf(events);
    }

    /**
     * Returns all uncommitted events.
     */
    UncommittedEvents events() {
        return UncommittedEvents.ofNone()
                                .append(events);
    }

    /**
     * Tells if this history contains any uncommitted events.
     */
    boolean hasEvents() {
        return !events.isEmpty();
    }

    /**
     * Marks the recorded events as stored and no longer uncommitted.
     */
    void commit() {
        events.clear();
    }
}
