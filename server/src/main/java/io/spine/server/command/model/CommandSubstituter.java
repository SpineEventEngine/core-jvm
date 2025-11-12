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

import io.spine.server.command.CommandReceiver;
import io.spine.server.dispatch.Success;
import io.spine.server.model.IllegalOutcomeException;
import io.spine.server.model.ParameterSpec;
import io.spine.server.type.CommandClass;
import io.spine.server.type.CommandEnvelope;

import java.lang.reflect.Method;

import static java.lang.String.format;

/**
 * A method that produces one or more command messages in response to an incoming command.
 */
public final class CommandSubstituter
        extends CommandReceptor<CommandReceiver, CommandClass>
        implements CommandingMethod<CommandReceiver, CommandClass, CommandEnvelope> {

    CommandSubstituter(Method method, ParameterSpec<CommandEnvelope> paramSpec) {
        super(method, paramSpec);
    }

    /**
     * Always throws {@code IllegalOutcomeException} because command substitution method must
     * produce a new command in response to incoming.
     *
     * @return nothing ever
     * @throws IllegalOutcomeException always
     */
    @Override
    public Success fromEmpty(CommandEnvelope handledSignal) {
        var errorMessage = format(
                "Commander method `%s` did not produce any result for command with ID `%s`.",
                this,
                handledSignal.id()
        );
        throw new IllegalOutcomeException(errorMessage);
    }
}
