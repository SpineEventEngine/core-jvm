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

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import io.spine.core.Event;

import java.util.Deque;
import java.util.Iterator;
import java.util.Objects;
import java.util.stream.Stream;

import static com.google.common.collect.Queues.newArrayDeque;

/**
 * A copy of recent history of an {@linkplain TransactionalEntity
 * event-sourced entity}.
 *
 * <p>Any modifications to this object will not affect the real history of the entity.
 */
public final class RecentHistory {

    /**
     * Holds the history of all events that happened to the aggregate since the last snapshot.
     *
     * <p>Most recent event comes first.
     *
     * @see #iterator()
     */
    private final Deque<Event> history = newArrayDeque();

    /**
     * Creates a new instance.
     */
    RecentHistory() {
        super();
    }

    /**
     * Returns {@code true} if there are no events in the recent history, {@code false} otherwise.
     */
    public boolean isEmpty() {
        return history.isEmpty();
    }

    /**
     * Removes all events from the recent history.
     */
    void clear() {
        history.clear();
    }

    /**
     * Creates a new iterator over the recent history items.
     *
     * <p>The iterator returns events in the reverse chronological order. Thus the most recent
     * event will be returned first.
     *
     * @return an events iterator
     */
    public Iterator<Event> iterator() {
        var events = ImmutableList.copyOf(history);
        return events.iterator();
    }

    /**
     * Creates a new {@link Stream} of the recent history items.
     *
     * <p>The produced stream is sequential and emits items in the reverse chronological order.
     * Thus the most recent event will be returned first.
     *
     * @return a stream of the recent events
     */
    public Stream<Event> stream() {
        var events = ImmutableList.copyOf(history);
        return events.stream();
    }

    /**
     * Adds events to the history.
     *
     * @param events events in the chronological order
     */
    void addAll(Iterable<Event> events) {
        for (var event : events) {
            history.addFirst(event);
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(history);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        var other = (RecentHistory) obj;
        return Objects.equals(this.history, other.history);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                          .add("size", history.size())
                          .toString();
    }
}
