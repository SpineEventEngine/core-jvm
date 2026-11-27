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

package io.spine.server.command.model;

import io.spine.server.command.Assignee;
import io.spine.server.dispatch.Success;
import io.spine.server.model.EventEmitter;
import io.spine.server.model.IllegalOutcomeException;
import io.spine.server.model.ParameterSpec;
import io.spine.server.type.CommandClass;
import io.spine.server.type.CommandEnvelope;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Method;

import static java.lang.String.format;

/**
 * The wrapper for a method assigned to handle commands.
 */
public final class AssigneeReceptor
        extends CommandReceptor<Assignee>
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
