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

package io.spine.server.event;

import com.google.protobuf.util.Timestamps;
import io.spine.annotation.Internal;
import io.spine.core.Event;
import io.spine.core.EventContext;

import java.io.Serial;
import java.io.Serializable;
import java.util.Comparator;

/**
 * An abstract base for the comparators working with {@link Event}s.
 */
@Internal
public abstract class EventComparator implements Comparator<Event>, Serializable {

    @Serial
    private static final long serialVersionUID = 0L;
    private static final EventComparator chronologically = new Chronological();

    private EventComparator() {
    }

    /**
     * Returns a comparator which compares events by their timestamp in chronological order.
     *
     * <p>In case the timestamps are the same, the {@linkplain EventContext#getVersion()
     * event versions} are compared.
     *
     * <p>If the versions are the same, the values of the {@linkplain Event#getId()
     * event identifiers} are compared lexicographically.
     */
    public static EventComparator chronological() {
        return chronologically;
    }

    /**
     * A comparator which compares events by their timestamp in chronological order.
     *
     * @see #chronological() for a public API
     */
    private static final class Chronological extends EventComparator {

        @Serial
        private static final long serialVersionUID = 0L;

        @Override
        public int compare(Event e1, Event e2) {
            var result = Comparator
                    .comparing(Event::timestamp, Timestamps.comparator())
                    .thenComparing((e) -> e.getContext()
                                           .getVersion()
                                           .getNumber())
                    .thenComparing((e) -> e.getId()
                                           .getValue())
                    .compare(e1, e2);
            return result;
        }
    }
}
