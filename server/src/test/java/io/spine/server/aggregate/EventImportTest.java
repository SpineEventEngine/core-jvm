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

package io.spine.server.aggregate;

import io.spine.base.EventMessage;
import io.spine.server.aggregate.given.klasse.EngineAggregate;
import io.spine.server.aggregate.given.klasse.EngineId;
import io.spine.server.aggregate.given.klasse.EngineRepository;
import io.spine.server.aggregate.given.klasse.event.EngineStopped;
import io.spine.server.aggregate.given.klasse.event.SettingsAdjusted;
import io.spine.server.aggregate.given.klasse.event.UnsupportedEngineEvent;
import io.spine.server.type.EventClass;
import io.spine.server.type.EventEnvelope;
import io.spine.testing.server.TestEventFactory;
import io.spine.testing.server.blackbox.BlackBox;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.google.common.truth.Truth.assertThat;

/**
 * Test support of event import in {@link AggregateRepository}.
 */
@DisplayName("For event import, `AggregateRepository` should")
class EventImportTest {

    private EngineRepository repository;
    private BlackBox context;

    void createRepository(boolean routeByFirstMessageField) {
        repository = new EngineRepository(routeByFirstMessageField);
        context = BlackBox.singleTenantWith(repository);
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    static EngineId engineId(String value) {
        return EngineId
                .newBuilder()
                .setValue(value)
                .build();
    }

    protected final EngineRepository repository() {
        return this.repository;
    }

    protected final BlackBox context() {
        return this.context;
    }

    /**
     * Creates a new event for the passed message.
     *
     * @param eventMessage
     *        the event message
     * @param producerId
     *        the producer for the event. If {@code null}, a test class name will be the ID
     *        of the producer.
     * @return generated event wrapped into the envelope
     */
    EventEnvelope createEvent(EventMessage eventMessage, @Nullable EngineId producerId) {
        var eventFactory = producerId == null
                           ? TestEventFactory.newInstance(getClass())
                           : TestEventFactory.newInstance(producerId, getClass());
        var result = EventEnvelope.of(eventFactory.createEvent(eventMessage));
        return result;
    }

    @Test
    @DisplayName("Obtain importable event classes")
    void importableEventClasses() {
        createRepository(false);
        Set<EventClass> importableEventClasses = repository().importableEvents();
        Set<EventClass> exposedByAggregateClass = repository().aggregateClass()
                                                              .importableEvents();
        assertThat(importableEventClasses)
                .isEqualTo(exposedByAggregateClass);
    }

    @Nested
    @DisplayName("dispatch imported events")
    class Dispatching {

        private final EngineId engineId = engineId("AEL");
        private final EngineStopped eventMessage = EngineStopped
                .newBuilder()
                .setId(engineId)
                .build();


        @Test
        @DisplayName("by producer ID")
        void producerId() {
            createRepository(false);

            // Create event with producer ID, which is the target aggregate ID.
            var event = createEvent(eventMessage, engineId);

            assertImports(event);
        }

        @Test
        @DisplayName("by first message field")
        void firstMessageField() {
            createRepository(true);

            // Create event with producer ID, which is NOT the target aggregate ID.
            var event = createEvent(eventMessage, null);

            assertImports(event);
        }

        /**
         * Checks that the given event is successfully imported.
         *
         * <p>An imported event can be verified by the {@code BlackBox} since it is posted
         * into the event bus after being applied to an Aggregate. Also, the event posted
         * into the event bus differs from the one posted into the import bus in the {@code version}
         * field.
         */
        private void assertImports(EventEnvelope event) {
            context().importsEvent(event.outerObject())
                     .assertEvents()
                     .withType(event.messageClass().value())
                     .hasSize(1);
        }
    }

    @Nested
    @DisplayName("route imported events by")
    class Routing {
        private final EngineId engineId = engineId("AFB");
        private final SettingsAdjusted eventMessage = SettingsAdjusted
                .newBuilder()
                .setId(engineId)
                .build();

        @Test
        @DisplayName("producer ID by default")
        void routeById() {
            createRepository(false);

            // Create event with the producer ID of the target aggregate.
            var event = createEvent(eventMessage, engineId);

            // Apply routing to the generated event.
            assertRouted(event);
        }

        @Test
        @DisplayName("first message field, if configured")
        void routeByFirstMessageField() {
            createRepository(true);

            // Create event with the producer ID, which is NOT the target aggregate ID.
            var event = createEvent(eventMessage, null);

            assertRouted(event);
        }

        /**
         * Asserts that the import routing resulted in {@link #engineId}.
         */
        private void assertRouted(EventEnvelope event) {
            var assertEntity =
                    context().importsEvent(event.outerObject())
                             .assertEntity(engineId, EngineAggregate.class);
            assertEntity.exists();
        }
    }

    @Test
    @DisplayName("fail with exception when importing unsupported event")
    void importUnsupported() {
        createRepository(false);

        var id = engineId("AGR");
        var eventMessage = UnsupportedEngineEvent.newBuilder()
                .setId(id)
                .build();
        var unsupported = createEvent(eventMessage, id);

        context().importsEvent(unsupported.outerObject())
                 .assertEvents()
                 .isEmpty();
    }
}
