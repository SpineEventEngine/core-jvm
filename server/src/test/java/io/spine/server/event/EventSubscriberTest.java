/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package io.spine.server.event;

import io.spine.base.Identifier;
import io.spine.server.BoundedContextBuilder;
import io.spine.server.event.given.EventSubscriberTestEnv.FailingSubscriber;
import io.spine.server.type.EventEnvelope;
import io.spine.system.server.DiagnosticMonitor;
import io.spine.test.event.FailRequested;
import io.spine.testing.logging.mute.MuteLogging;
import io.spine.testing.server.TestEventFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("`EventSubscriber` should")
class EventSubscriberTest {

    private final TestEventFactory factory = TestEventFactory.newInstance(getClass());

    private FailingSubscriber subscriber;

    @BeforeEach
    void setUp() {
        subscriber = new FailingSubscriber();
    }

    @Test
    @DisplayName("catch exceptions caused by methods")
    @MuteLogging
    void catchMethodExceptions() {
        // Create event that should fail.
        var eventEnvelope = createEvent(false);

        var monitor = new DiagnosticMonitor();
        try (var ignored = BoundedContextBuilder.assumingTests()
                .addEventDispatcher(subscriber)
                .addEventDispatcher(monitor)
                .build()) {
            subscriber.dispatch(eventEnvelope);
        }

        assertTrue(subscriber.isMethodCalled());
        var systemEvents = monitor.handlerFailureEvents();
        assertThat(systemEvents)
                .hasSize(1);
        var systemEvent = systemEvents.get(0);
        assertThat(systemEvent.getHandledSignal())
                .isEqualTo(eventEnvelope.messageId());
        assertThat(systemEvent.getError()).isNotEqualToDefaultInstance();
        var expectedId = Identifier.pack(subscriber.getClass()
                                                   .getName());
        assertThat(systemEvent.getEntity().getId())
                .isEqualTo(expectedId);
    }

    @Test
    @DisplayName("dispatch event")
    void dispatchEvent() {
        var eventEnvelope = createEvent(true);

        subscriber.dispatch(eventEnvelope);
        assertTrue(subscriber.isMethodCalled());
    }

    @Test
    @DisplayName("expose handled message classes")
    void exposeMessageClasses() {
        assertEquals(4, subscriber.messageClasses()
                                  .size());
    }

    @Test
    @DisplayName("expose handled external message classes")
    void exposeExternalClasses() {
        assertEquals(1, subscriber.externalEventClasses()
                                  .size());
    }

    private EventEnvelope createEvent(boolean shouldFail) {
        var event = factory.createEvent(FailRequested.newBuilder()
                                                .setShouldFail(shouldFail)
                                                .build());
        return EventEnvelope.of(event);
    }
}
