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

package io.spine.server.aggregate.given.fibonacci;

import io.spine.server.aggregate.Aggregate;
import io.spine.server.aggregate.Apply;
import io.spine.server.aggregate.given.fibonacci.command.MoveSequence;
import io.spine.server.aggregate.given.fibonacci.command.SetStartingNumbers;
import io.spine.server.aggregate.given.fibonacci.event.SequenceMoved;
import io.spine.server.aggregate.given.fibonacci.event.StartingNumbersSet;
import io.spine.server.command.Assign;

@SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod")
// For convenience, to avoid other excessively verbose solutions.
public final class FibonacciAggregate extends Aggregate<SequenceId, Sequence, Sequence.Builder> {

    private static int lastNumberOne = -1;
    private static int lastNumberTwo = -1;

    @Assign
    StartingNumbersSet handle(SetStartingNumbers cmd) {
        var event = StartingNumbersSet.newBuilder()
                .setId(cmd.getId())
                .setNumberOne(cmd.getNumberOne())
                .setNumberTwo(cmd.getNumberTwo())
                .build();
        return event;
    }

    @Assign
    SequenceMoved handle(MoveSequence cmd) {
        var event = SequenceMoved.newBuilder()
                .setId(cmd.getId())
                .build();
        return event;
    }

    @Apply
    private void on(StartingNumbersSet event) {
        var numberOne = event.getNumberOne();
        var numberTwo = event.getNumberTwo();
        builder().setId(event.getId())
                 .setCurrentNumberOne(numberOne)
                 .setCurrentNumberTwo(numberTwo);
        lastNumberOne = numberOne;
        lastNumberTwo = numberTwo;
    }

    @Apply
    private void on(SequenceMoved event) {
        var numberOne = builder().getCurrentNumberOne();
        var numberTwo = builder().getCurrentNumberTwo();
        var sum = numberOne + numberTwo;
        builder().setId(event.getId())
                 .setCurrentNumberOne(numberTwo)
                 .setCurrentNumberTwo(sum);
        lastNumberOne = numberTwo;
        lastNumberTwo = sum;
    }

    public static int lastNumberOne() {
        return lastNumberOne;
    }

    public static int lastNumberTwo() {
        return lastNumberTwo;
    }
}
