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

package io.spine.server.delivery.given;

import io.spine.server.command.Assign;
import io.spine.server.procman.ProcessManager;
import io.spine.server.tuple.Pair;
import io.spine.test.delivery.ConsecutiveNumber;
import io.spine.test.delivery.EmitNextNumber;
import io.spine.test.delivery.NegativeNumberEmitted;
import io.spine.test.delivery.PositiveNumberEmitted;

/**
 * A process manager that emits the consecutive positive and negative numbers upon
 * each next {@code EmitNextNumber} command.
 */
public final class ConsecutiveNumberProcess
        extends ProcessManager<String, ConsecutiveNumber, ConsecutiveNumber.Builder> {

    @Assign
    Pair<PositiveNumberEmitted, NegativeNumberEmitted> handle(EmitNextNumber cmd) {
        var id = cmd.getId();
        builder().setId(id);
        var iteration = state().getIteration();
        var nextValue = iteration + 1;
        builder().setIteration(nextValue);
        var positiveNumberEmitted = PositiveNumberEmitted.newBuilder()
                .setId(id)
                .setValue(nextValue)
                .build();

        var negativeNumberEmitted = NegativeNumberEmitted.newBuilder()
                .setId(id)
                .setValue(-1 * nextValue)
                .build();
        return Pair.of(positiveNumberEmitted, negativeNumberEmitted);
    }
}
