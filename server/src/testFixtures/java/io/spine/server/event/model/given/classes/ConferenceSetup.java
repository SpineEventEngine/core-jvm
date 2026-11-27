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

package io.spine.server.event.model.given.classes;

import com.google.protobuf.Any;
import io.spine.base.Identifier;
import io.spine.core.EventContext;
import io.spine.core.External;
import io.spine.core.Version;
import io.spine.core.Versions;
import io.spine.server.event.EventReactor;
import io.spine.server.event.React;
import io.spine.test.event.model.ConferenceAnnounced;
import io.spine.test.event.model.SpeakerJoined;
import io.spine.test.event.model.SpeakersInvited;
import io.spine.test.event.model.TalkSubmissionRequested;
import io.spine.time.LocalDates;

/**
 * A test environment {@code EventReactor} class.
 *
 * <p>Normally, what this class does should be done using a Process Manager.
 * We do it this way here to be able to gather classes of events for the tests.
 *
 * @see io.spine.server.event.model.EventReactorClassTest
 */
public class ConferenceSetup implements EventReactor {

    private static final Any id = Identifier.pack(ConferenceSetup.class.getName());

    @React // Just pretend that the event is external.
    SpeakersInvited invitationPolicy(@External ConferenceAnnounced event) {
        var speakerSubmissionDeadline = event.getDate().toJavaTime().plusWeeks(3);
        return SpeakersInvited.newBuilder()
                .setConference(event.getConference())
                .setDeadline(LocalDates.of(speakerSubmissionDeadline))
                .build();
    }

    @React
    TalkSubmissionRequested talkSubmissionPolicy(SpeakerJoined event, EventContext context) {
        var eventDate = context.localDate();
        var talkSubmissionDeadline = eventDate.plusWeeks(1);
        return TalkSubmissionRequested.newBuilder()
                .setConference(event.getConference())
                .setSpeaker(event.getSpeaker())
                .setDeadline(LocalDates.of(talkSubmissionDeadline))
                .build();
    }

    @Override
    public Any producerId() {
        return id;
    }

    @Override
    public Version version() {
        return Versions.zero();
    }
}
