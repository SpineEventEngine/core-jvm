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

package io.spine.server.aggregate.given.importado;

import io.spine.server.aggregate.given.importado.command.Move;
import io.spine.server.aggregate.given.importado.event.Moved;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Static utilities for movement DSL.
 */
public final class MoveMessages {

    /** Prevents instantiation of this utility class. */
    private MoveMessages() {
    }

    /**
     * Creates a command to move the passed object into the given direction.
     */
    public static Move move(ObjectId object, Direction direction) {
        checkNotNull(object);
        checkNotNull(direction);
        return Move
                .newBuilder()
                .setObject(object)
                .setDirection(direction)
                .build();
    }

    /**
     * Creates an event on the fact that the object moved into the passed direction.
     */
    public static Moved moved(ObjectId object, Direction direction) {
        checkNotNull(object);
        checkNotNull(direction);
        return Moved
                .newBuilder()
                .setObject(object)
                .setDirection(direction)
                .build();
    }
}
