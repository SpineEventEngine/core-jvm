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
 * The list of uncommitted events of a [SignalDispatchingEntity].
 */
internal class UncommittedEvents
private constructor(private val events: ImmutableList<Event>) {

    /**
     * Tells whether or not this `UncommittedEvents` list is empty.
     *
     * @return `true` if the list is not empty, `false` otherwise.
     */
    fun nonEmpty(): Boolean = events.isNotEmpty()

    /**
     * Obtains the list of events.
     *
     * @return The list of uncommitted events.
     */
    fun list(): ImmutableList<Event> = events

    /**
     * Creates a new instance of `UncommittedEvents` with this [list] appended
     * with the given `events`.
     *
     * If this `UncommittedEvents` list is [nonEmpty] or the given
     * `Iterable` is not empty, then the resulting instance is [nonEmpty] as well.
     *
     * @param newEvents The events to append.
     * @return New `UncommittedEvents` instance.
     */
    fun append(newEvents: Iterable<Event>): UncommittedEvents {
        val newList = ImmutableList.builder<Event>()
            .addAll(events)
            .addAll(newEvents)
            .build()
        return UncommittedEvents(newList)
    }

    companion object {

        private val EMPTY = UncommittedEvents(ImmutableList.of())

        /**
         * Returns an empty list of events.
         */
        fun ofNone(): UncommittedEvents = EMPTY
    }
}
