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

package io.spine.server.command.model;

import io.spine.server.command.Assignee;
import io.spine.server.dispatch.Success;
import io.spine.server.model.EventEmitter;
import io.spine.server.model.IllegalOutcomeException;
import io.spine.server.model.ParameterSpec;
import io.spine.server.type.CommandClass;
import io.spine.server.type.CommandEnvelope;
import io.spine.server.type.EventClass;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Method;

import static java.lang.String.format;

/**
 * The wrapper for a method assigned to handle commands.
 */
public final class AssigneeReceptor
        extends CommandReceptor<Assignee, EventClass>
        implements EventEmitter<Assignee, CommandClass, CommandEnvelope> {

    /**
     * Creates a new instance to wrap {@code method} on {@code target}.
     *
     * @param method
     *         method assigned to handle commands
     */
    AssigneeReceptor(Method method, ParameterSpec<CommandEnvelope> params) {
        super(method, params);
    }

    @Override
    public Success toSuccessfulOutcome(@Nullable Object rawResult,
                                       Assignee target,
                                       CommandEnvelope handledSignal) {
        var outcome =
                EventEmitter.super.toSuccessfulOutcome(rawResult, target, handledSignal);
        if (outcome.getProducedEvents().getEventCount() == 0) {
            var errorMessage = format(
                    "Command-handling method %s did not produce any events when processing command %s",
                    this,
                    handledSignal.id()
            );
            throw new IllegalOutcomeException(errorMessage);
        } else {
            return outcome;
        }
    }
}
