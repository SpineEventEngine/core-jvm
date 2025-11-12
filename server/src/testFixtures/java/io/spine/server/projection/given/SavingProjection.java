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

package io.spine.server.projection.given;

import io.spine.core.Subscribe;
import io.spine.server.projection.Projection;
import io.spine.test.projection.event.Int32Imported;
import io.spine.test.projection.event.StringImported;

/**
 * This projection appends data from the passed events to its state,
 * separating new data with new lines.
 */
public final class SavingProjection
        extends Projection<String, SavedString, SavedString.Builder> {

    private SavingProjection(String id) {
        super(id);
    }

    @Subscribe
    void on(StringImported event) {
        var newState = createNewState("stringState", event.getValue());
        builder().mergeFrom(newState);
    }

    @Subscribe
    void on(Int32Imported event) {
        var newState = createNewState("integerState",
                                      String.valueOf(event.getValue()));
        builder().mergeFrom(newState);
    }

    private SavedString createNewState(String type, String value) {
        // Get the current state within the transaction.
        var currentState = builder().buildPartial()
                                    .getValue();
        var result = currentState + (currentState.length() > 0 ? " + " : "") +
                type + '(' + value + ')' + System.lineSeparator();
        return SavedString.newBuilder()
                .setValue(result)
                .build();
    }
}
