/*
 * Copyright 2025, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
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

package io.spine.server.model.handler.given;

import com.google.common.collect.ImmutableList;
import io.spine.server.aggregate.Aggregate;
import io.spine.server.aggregate.Apply;
import io.spine.server.command.Assign;
import io.spine.server.model.handler.given.command.Start;
import io.spine.server.model.handler.given.event.MovedEast;
import io.spine.server.model.handler.given.event.MovedNorth;
import io.spine.server.model.handler.given.event.MovedSouth;
import io.spine.server.model.handler.given.event.MovedWest;
import io.spine.server.model.handler.given.event.MovingEvent;
import io.spine.testing.TestValues;

import java.util.List;

import static io.spine.util.Exceptions.newIllegalStateException;

/**
 * A simple aggregate that generates several moves after
 * the {@link io.spine.server.model.handler.given.command.Start} command.
 */
public class RoverBot extends Aggregate<Integer, Position, Position.Builder> {

    @Assign
    List<MovingEvent> handle(Start command) {
        ImmutableList.Builder<MovingEvent> events = ImmutableList.builder();
        var numberOfMoves = command.getNumberOfMoves();
        for (var i = 0; i < numberOfMoves; i++) {
            events.add(nextMove());
        }
        return events.build();
    }

    /** Generates a random move event. */
    private MovingEvent nextMove() {
        var nextMove = TestValues.random(4);
        var id = id();
        switch (nextMove) {
            case 0:
                return MovedNorth.newBuilder()
                        .setBotId(id)
                        .build();
            case 1:
                return MovedEast.newBuilder()
                        .setBotId(id)
                        .build();
            case 2:
                return MovedSouth.newBuilder()
                        .setBotId(id)
                        .build();
            case 3:
                return MovedWest.newBuilder()
                        .setBotId(id)
                        .build();
            default:
                throw newIllegalStateException("Unable to create a move event for %d.", nextMove);
        }
    }

    private int currentX() {
        return builder().getX();
    }

    private int currentY() {
        return builder().getY();
    }

    @Apply
    private void on(MovedNorth e) {
        builder().setId(e.getBotId())
                 .setY(currentY() + 1);
    }

    @Apply
    private void on(MovedEast e) {
        builder().setId(e.getBotId())
                 .setX(currentX() + 1);
    }

    @Apply
    private void on(MovedSouth e) {
        builder().setId(e.getBotId())
                 .setY(currentY() - 1);
    }

    @Apply
    private void on(MovedWest e) {
        builder().setId(e.getBotId())
                 .setX(currentX() - 1);
    }
}
