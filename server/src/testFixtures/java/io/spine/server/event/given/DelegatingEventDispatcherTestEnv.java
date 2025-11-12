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

package io.spine.server.event.given;

import com.google.common.collect.ImmutableSet;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.event.EventDispatcherDelegate;
import io.spine.server.type.EventClass;
import io.spine.server.type.EventEnvelope;
import io.spine.test.event.EvTeamCreated;

import static com.google.common.collect.Sets.union;
import static io.spine.server.dispatch.DispatchOutcomes.successfulOutcome;

public class DelegatingEventDispatcherTestEnv {

    /** Prevents instantiation of this utility class. */
    private DelegatingEventDispatcherTestEnv() {
    }

    public static final class ExternalEventDispatcherDelegate
            implements EventDispatcherDelegate {

        @Override
        public ImmutableSet<EventClass> events() {
            return union(domesticEvents(), externalEvents()).immutableCopy();
        }

        @Override
        public ImmutableSet<EventClass> domesticEvents() {
            return ImmutableSet.of();
        }

        @Override
        public ImmutableSet<EventClass> externalEvents() {
            // Return at least one event class so that we can create external delegate.
            return EventClass.setOf(EvTeamCreated.class);
        }

        @Override
        public DispatchOutcome dispatchEvent(EventEnvelope event) {
            return successfulOutcome(event);
        }
    }

    public static final class DomesticEventDispatcherDelegate
            implements EventDispatcherDelegate {

        @Override
        public ImmutableSet<EventClass> events() {
            return EventClass.setOf(EvTeamCreated.class);
        }

        @Override
        public ImmutableSet<EventClass> domesticEvents() {
            return events();
        }

        @Override
        public ImmutableSet<EventClass> externalEvents() {
            return ImmutableSet.of();
        }

        @Override
        public DispatchOutcome dispatchEvent(EventEnvelope event) {
            return successfulOutcome(event);
        }
    }
}
