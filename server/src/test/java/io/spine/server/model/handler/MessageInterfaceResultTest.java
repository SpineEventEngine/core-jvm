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

package io.spine.server.model.handler;

import io.spine.server.aggregate.model.AggregateClass;
import io.spine.server.model.handler.given.RoverBot;
import io.spine.server.model.handler.given.command.Start;
import io.spine.server.model.handler.given.event.MovedEast;
import io.spine.server.model.handler.given.event.MovedNorth;
import io.spine.server.model.handler.given.event.MovedSouth;
import io.spine.server.model.handler.given.event.MovedWest;
import io.spine.testing.server.blackbox.BlackBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.spine.server.aggregate.model.AggregateClass.asAggregateClass;
import static io.spine.testing.TestValues.random;
import static io.spine.testing.server.Assertions.assertEventClassesExactly;

@DisplayName("An aggregate class with a handler returning events via common interface should")
class MessageInterfaceResultTest {

    private final AggregateClass<?> aggregateClass = asAggregateClass(RoverBot.class);
    private BlackBox context;

    @BeforeEach
    void createContext() {
        context = BlackBox.singleTenantWith(RoverBot.class);
    }

    @AfterEach
    void closeContext() {
        context.close();
    }

    @Test
    @DisplayName("provide state events")
    void generatedEvents() {
        assertEventClassesExactly(
                aggregateClass.stateEvents(),
                MovedNorth.class, MovedEast.class, MovedSouth.class, MovedWest.class
        );
    }

    @Test
    @DisplayName("dispatch the command")
    void dispatching() {
        var numberOfMoves = random(1, 5);
        var command = Start.newBuilder()
                .setBotId(random(1, 7))
                .setNumberOfMoves(numberOfMoves)
                .build();
        context.receivesCommand(command);
        context.assertEvents()
               .hasSize(numberOfMoves);
    }
}
