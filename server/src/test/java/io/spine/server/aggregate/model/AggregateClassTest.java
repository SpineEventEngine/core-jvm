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

package io.spine.server.aggregate.model;

import io.spine.server.aggregate.given.klasse.EngineAggregate;
import io.spine.server.aggregate.given.klasse.command.StartEngine;
import io.spine.server.aggregate.given.klasse.command.StopEngine;
import io.spine.server.aggregate.given.klasse.event.EmissionTestStarted;
import io.spine.server.aggregate.given.klasse.event.EmissionTestStopped;
import io.spine.server.aggregate.given.klasse.event.EngineStarted;
import io.spine.server.aggregate.given.klasse.event.EngineStopped;
import io.spine.server.aggregate.given.klasse.event.TankEmpty;
import io.spine.server.aggregate.given.klasse.rejection.Rejections;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static io.spine.server.aggregate.model.AggregateClass.asAggregateClass;
import static io.spine.testing.server.Assertions.assertCommandClassesExactly;
import static io.spine.testing.server.Assertions.assertEventClassesExactly;

/**
 * Test obtaining dispatched message classes from {@link AggregateClass}.
 */
@DisplayName("`AggregateClass` should")
class AggregateClassTest {

    private final AggregateClass<?> aggregateClass = asAggregateClass(EngineAggregate.class);

    @Nested
    @DisplayName("provide classes of")
    class MessageClasses {

        @Test
        @DisplayName("commands handled by the aggregate")
        void commandClasses() {
            assertCommandClassesExactly(aggregateClass.commands(),
                                        StartEngine.class, StopEngine.class);
        }

        @Test
        @DisplayName("events (including rejections) on which the aggregate reacts")
        void eventClasses() {
            assertEventClassesExactly(aggregateClass.events(),
                                      TankEmpty.class,
                                      Rejections.EngineAlreadyStopped.class,
                                      Rejections.EngineAlreadyStarted.class,
                                      EmissionTestStarted.class,
                                      EmissionTestStopped.class,
                                      Rejections.CannotStartEmissionTest.class);
        }

        @Test
        @DisplayName("external events (including rejections) on which the aggregate reacts")
        void externalEventClasses() {
            assertEventClassesExactly(aggregateClass.externalEvents(),
                                      EmissionTestStarted.class,
                                      EmissionTestStopped.class,
                                      Rejections.CannotStartEmissionTest.class);
        }

        @Test
        @DisplayName("events (including rejections) produced by the aggregate")
        void producedEvents() {
            assertEventClassesExactly(aggregateClass.outgoingEvents(),
                                      EngineStarted.class,
                                      EngineStopped.class,
                                      Rejections.EngineAlreadyStarted.class,
                                      Rejections.EngineAlreadyStopped.class);
        }

        @Test
        @DisplayName("rejections produced by the aggregate")
        void producedRejections() {
            assertEventClassesExactly(aggregateClass.rejections(),
                                      Rejections.EngineAlreadyStarted.class,
                                      Rejections.EngineAlreadyStopped.class);
        }
    }
}
