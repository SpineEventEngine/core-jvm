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

package io.spine.server.bc.given;

import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.spine.server.commandbus.CommandDispatcher;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.event.EventDispatcher;
import io.spine.server.type.CommandClass;
import io.spine.server.type.CommandEnvelope;
import io.spine.server.type.EventClass;
import io.spine.server.type.EventEnvelope;
import io.spine.test.bc.ProjectId;
import io.spine.test.bc.command.BcCreateProject;
import io.spine.test.bc.event.BcProjectCreated;
import io.spine.test.bc.event.BcProjectStarted;
import io.spine.test.bc.event.BcTaskAdded;

import static com.google.common.collect.Sets.union;
import static io.spine.server.dispatch.DispatchOutcomes.successfulOutcome;

public class Given {

    private Given() {
    }

    public static class EventMessage {

        private EventMessage() {
        }

        public static BcProjectCreated projectCreated(ProjectId id) {
            return BcProjectCreated.newBuilder()
                                   .setProjectId(id)
                                   .build();
        }

        public static BcTaskAdded taskAdded(ProjectId id) {
            return BcTaskAdded.newBuilder()
                              .setProjectId(id)
                              .build();
        }

        public static BcProjectStarted projectStarted(ProjectId id) {
            return BcProjectStarted.newBuilder()
                                   .setProjectId(id)
                                   .build();
        }
    }

    public static class NoOpCommandDispatcher implements CommandDispatcher {

        private final CommandClass commandClass = CommandClass.from(BcCreateProject.class);

        @Override
        public ImmutableSet<CommandClass> messageClasses() {
            return ImmutableSet.of(commandClass);
        }

        @Override
        @CanIgnoreReturnValue
        public DispatchOutcome dispatch(CommandEnvelope envelope) {
            return successfulOutcome(envelope);
        }
    }

    public static class NoOpEventDispatcher implements EventDispatcher {

        private final EventClass eventClass = EventClass.from(BcProjectCreated.class);

        @Override
        public ImmutableSet<EventClass> externalEventClasses() {
            return ImmutableSet.of(eventClass);
        }

        @Override
        public ImmutableSet<EventClass> domesticEventClasses() {
            return EventClass.emptySet();
        }

        @Override
        public ImmutableSet<EventClass> messageClasses() {
            return union(externalEventClasses(), domesticEventClasses()).immutableCopy();
        }

        @Override
        @CanIgnoreReturnValue
        public DispatchOutcome dispatch(EventEnvelope envelope) {
            return successfulOutcome(envelope);
        }
    }
}
