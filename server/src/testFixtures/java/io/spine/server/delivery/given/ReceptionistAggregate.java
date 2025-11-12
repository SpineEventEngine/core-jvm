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

import io.spine.server.aggregate.Aggregate;
import io.spine.server.aggregate.Apply;
import io.spine.server.command.Assign;
import io.spine.test.delivery.Receptionist;
import io.spine.test.delivery.command.TurnConditionerOff;
import io.spine.test.delivery.command.TurnConditionerOn;
import io.spine.test.delivery.event.ConditionerTurnedOff;
import io.spine.test.delivery.event.ConditionerTurnedOn;

import static io.spine.util.Exceptions.newIllegalStateException;

public final class ReceptionistAggregate
        extends Aggregate<String, Receptionist, Receptionist.Builder> {

    public static final String FAILURE_MESSAGE = "Receptionist failed to apply an event.";

    private static boolean failInAppliers = false;

    @Assign
    ConditionerTurnedOn handler(TurnConditionerOn cmd) {
        return ConditionerTurnedOn.newBuilder()
                .setReceptionist(id())
                .build();
    }

    @Apply
    @SuppressWarnings("ResultOfMethodCallIgnored" /* Using Proto builder. */)
    private void apply(ConditionerTurnedOn event) {
        maybeFail();
        var newValue = builder().getHowManyCmdsHandled() + 1;
        builder().setId(event.getReceptionist())
                 .setHowManyCmdsHandled(newValue);
    }

    @Assign
    ConditionerTurnedOff handler(TurnConditionerOff cmd) {
        return ConditionerTurnedOff.newBuilder()
                .setReceptionist(id())
                .build();
    }

    @Apply
    @SuppressWarnings("ResultOfMethodCallIgnored" /* Using Proto builder. */)
    private void apply(ConditionerTurnedOff event) {
        maybeFail();
        var newValue = builder().getHowManyCmdsHandled() + 1;
        builder().setId(event.getReceptionist())
                 .setHowManyCmdsHandled(newValue);
    }

    private static void maybeFail() throws IllegalStateException {
        if (failInAppliers) {
            throw newIllegalStateException(FAILURE_MESSAGE);
        }
    }

    public static void makeApplierFail() {
        failInAppliers = true;
    }

    public static void makeApplierPass() {
        failInAppliers = false;
    }
}
