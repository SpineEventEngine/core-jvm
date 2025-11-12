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

package io.spine.server.aggregate.given.employee;

import com.google.common.collect.ImmutableList;
import io.spine.core.Event;

import java.util.List;

/**
 * Result of {@code Message}s dispatching.
 *
 * <p>Consists of:
 * <ul>
 *     <li>events stored to {@code AggregateStorage};</li>
 *     <li>events posted to {@code EventBus};</li>
 *     <li>updated {@code Aggregate}'s state.</li>
 * </ul>
 *
 * <p>A healthy {@code Aggregate} usually stores and posts the same set of events within
 * dispatching. That consequently causes its state to be updated.
 */
public final class DispatchExhaust {

    private final ImmutableList<Event> stored;
    private final ImmutableList<Event> posted;
    private final Employee state;

    public DispatchExhaust(
            List<Event> storedEvents,
            List<Event> postedEvents,
            Employee state
    ) {
        this.stored = ImmutableList.copyOf(storedEvents);
        this.posted = ImmutableList.copyOf(postedEvents);
        this.state = state;
    }

    public List<Event> storedEvents() {
        return stored;
    }

    public List<Event> postedEvents() {
        return posted;
    }

    public Employee state() {
        return state;
    }
}
