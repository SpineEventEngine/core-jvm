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

package io.spine.server.event.model;

import io.spine.server.event.model.given.classes.ConferenceSetup;
import io.spine.server.type.EventEnvelope;
import io.spine.test.event.model.Conference;
import io.spine.test.event.model.ConferenceAnnounced;
import io.spine.test.event.model.SpeakerJoined;
import io.spine.test.event.model.SpeakersInvited;
import io.spine.test.event.model.TalkSubmissionRequested;
import io.spine.testing.server.TestEventFactory;
import io.spine.testing.server.model.ModelTests;
import io.spine.time.LocalDates;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.server.event.model.EventReactorClass.asReactorClass;
import static io.spine.testing.server.Assertions.assertEventClassesExactly;
import static io.spine.time.Month.JULY;

@DisplayName("`EventReactorClass` should")
class EventReactorClassTest {

    private final EventReactorClass<?> reactorClass = asReactorClass(ConferenceSetup.class);

    @Nested
    @DisplayName("provide classes of")
    class MessageClasses {

        @Test
        @DisplayName("events (including external) to which instances of this class react")
        void events() {
            assertEventClassesExactly(reactorClass.events(),
                                      ConferenceAnnounced.class,
                                      SpeakerJoined.class);
        }

        @Test
        @DisplayName("domestic events to which instances of this class react")
        void domesticEvents() {
            assertEventClassesExactly(reactorClass.domesticEvents(),
                                      SpeakerJoined.class);
        }

        @Test
        @DisplayName("external events to which instances of this class react")
        void externalEvents() {
            assertEventClassesExactly(reactorClass.externalEvents(),
                                      ConferenceAnnounced.class);
        }

        @Test
        @DisplayName("events which instances of this class produce")
        void reactionOutput() {
            assertEventClassesExactly(reactorClass.reactionOutput(),
                                      SpeakersInvited.class,
                                      TalkSubmissionRequested.class);
        }
    }

    @Test
    @DisplayName("obtain the method by the event")
    void reactorOf() {
        var factory = TestEventFactory.newInstance(EventSubscriberClassTest.class);
        var event = factory.createEvent(
                ConferenceAnnounced.newBuilder()
                        .setConference(Conference.newBuilder().setName("ES Conf"))
                        .setDate(LocalDates.of(2021, JULY, 29))
                        .build());
        var method = reactorClass.reactorOf(EventEnvelope.of(event))
                                 .orElseGet(Assertions::fail);
        assertThat(method.rawMethod())
            .isEqualTo(ModelTests.getMethod(ConferenceSetup.class, "invitationPolicy"));
    }
}
