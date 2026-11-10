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
