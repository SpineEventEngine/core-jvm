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
import com.google.common.collect.ImmutableList;
import io.spine.base.EventMessage;
import io.spine.core.Event;
import io.spine.test.entity.event.EntProjectCreated;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Optional;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("`CompositeEventFilter` should")
class CompositeEventFilterTest {

    @Test
    @DisplayName("accept any given event if no filters are provided")
    void acceptIfEmpty() {
        var emptyFilter = CompositeEventFilter.newBuilder().build();
        var filtered = emptyFilter.filter(EntProjectCreated.getDefaultInstance());
        assertTrue(filtered.isPresent());
    }

    @Test
    @DisplayName("accept the given event if all filter accept")
    void acceptIfAllAccept() {
        var filter = CompositeEventFilter.newBuilder()
                .add(Optional::of)
                .add(Optional::of)
                .build();
        var filtered = filter.filter(EntProjectCreated.getDefaultInstance());
        assertTrue(filtered.isPresent());
    }

    @Test
    @DisplayName("not accept an event is one filter not accepts")
    void rejectIfOneRejects() {
        EventMessage eventMessage = EntProjectCreated.getDefaultInstance();

        var mockFilter = new MockFilter(eventMessage);

        var filter = CompositeEventFilter.newBuilder()
                .add(anyEvent -> Optional.empty())
                .add(mockFilter)
                .build();

        var filtered = filter.filter(eventMessage);

        assertThat(filtered)
                .isEmpty();
        assertFalse(mockFilter.called());
    }

    /**
     * Mock implementation of {@code EventFilter} which always returns the message passed on
     * constructor, and remembers if its methods were called.
     */
    private static final class MockFilter implements EventFilter {

        private boolean called;
        private final EventMessage eventMessage;

        private MockFilter(EventMessage message) {
            this.eventMessage = message;
        }

        private boolean called() {
            return called;
        }

        @Override
        public Optional<? extends EventMessage> filter(EventMessage event) {
            called = true;
            return Optional.of(eventMessage);
        }

        @Override
        public ImmutableCollection<Event> filter(Collection<Event> events) {
            called = true;
            return ImmutableList.copyOf(events);
        }
    }
}
