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

        var context = BoundedContextBuilder.assumingTests().build();
        var contextAccess = context.internalAccess();
        contextAccess.registerEventDispatcher(subscriber);
        var monitor = new DiagnosticMonitor();
        contextAccess.registerEventDispatcher(monitor);
        subscriber.dispatch(eventEnvelope);

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
