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

package com.example;

import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.spine.server.BoundedContext;
import io.spine.server.BoundedContextBuilder;
import io.spine.server.DefaultRepository;
import io.spine.server.bc.given.ProjectAggregate;
import io.spine.server.commandbus.CommandDispatcher;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.event.EventDispatcher;
import io.spine.server.type.CommandClass;
import io.spine.server.type.CommandEnvelope;
import io.spine.server.type.EventClass;
import io.spine.server.type.EventEnvelope;

import static io.spine.server.dispatch.DispatchOutcomes.successfulOutcome;

/**
 * Test environment class for testing {@code BoundedContext} configuration from
 * outside the framework.
 *
 * @see io.spine.server.BoundedContextSpec.RestrictRegistrationCalls
 */
public final class ForeignContextConfig {

    /** Prevents instantiation of this utility class. */
    private ForeignContextConfig() {
    }

    private static BoundedContext.InternalAccess contextAccess() {
        return context().internalAccess();
    }

    public static void repositoryRegistration() {
        contextAccess().register(DefaultRepository.of(ProjectAggregate.class));
    }

    public static void commandDispatcherRegistration() {
        contextAccess().registerCommandDispatcher(newCommandDispatcher());
    }

    public static void eventDispatcherRegistration() {
        contextAccess().registerEventDispatcher(EmptyEventDispatcher.newInstance());
    }

    private static BoundedContext context() {
        return BoundedContextBuilder.assumingTests().build();
    }

    private static CommandDispatcher newCommandDispatcher() {
        return new CommandDispatcher() {
            @Override
            public ImmutableSet<CommandClass> messageClasses() {
                return ImmutableSet.of();
            }

            @CanIgnoreReturnValue
            @Override
            public DispatchOutcome dispatch(CommandEnvelope envelope) {
                return successfulOutcome(envelope);
            }
        };
    }

    private static class EmptyEventDispatcher implements EventDispatcher {

        private static EventDispatcher newInstance() {
            return new EmptyEventDispatcher();
        }

        @Override
        public ImmutableSet<EventClass> externalEventClasses() {
            return EventClass.emptySet();
        }

        @Override
        public ImmutableSet<EventClass> domesticEventClasses() {
            return eventClasses();
        }

        @Override
        public ImmutableSet<EventClass> messageClasses() {
            return EventClass.emptySet();
        }

        @Override
        @CanIgnoreReturnValue
        public DispatchOutcome dispatch(EventEnvelope envelope) {
            return successfulOutcome(envelope);
        }
    }
}
