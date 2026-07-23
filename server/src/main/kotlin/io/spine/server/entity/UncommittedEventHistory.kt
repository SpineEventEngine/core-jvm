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

package io.spine.server.entity

import com.google.common.collect.ImmutableList
import io.spine.core.Event

/**
 * The events produced by an entity during the current dispatch that are not yet stored.
 *
 * Since the event-sourcing cutover, an entity no longer replays events to rebuild its
 * state, so this class no longer segments the events by snapshots — it is a plain, ordered
 * list of the events emitted by the current command or reaction. The framework records the
 * produced events here on the [SignalDispatchingEntity] after a successful dispatch, stores
 * them into the append-only journal alongside the latest state record, and then
 * [commits][commit].
 */
internal class UncommittedEventHistory {

    private val events = mutableListOf<Event>()

    /**
     * Records the events produced during the current dispatch.
     *
     * Rejection events are not journaled and are ignored.
     *
     * @param produced The events emitted by the current command handler or reactor.
     * @return The events kept for journaling by this call, in the order of emission.
     */
    fun record(produced: Iterable<Event>): List<Event> {
        val kept = ImmutableList.builder<Event>()
        for (event in produced) {
            if (!event.isRejection) {
                events.add(event)
                kept.add(event)
            }
        }
        return kept.build()
    }

    /**
     * Obtains the uncommitted events as an immutable list.
     *
     * The returned list is empty when there are no uncommitted events.
     */
    fun get(): List<Event> = ImmutableList.copyOf(events)

    /**
     * Returns all uncommitted events.
     */
    fun events(): UncommittedEvents =
        UncommittedEvents.ofNone()
            .append(events)

    /**
     * Tells if this history contains any uncommitted events.
     */
    fun hasEvents(): Boolean = events.isNotEmpty()

    /**
     * Marks the recorded events as stored and no longer uncommitted.
     */
    fun commit() {
        events.clear()
    }
}
