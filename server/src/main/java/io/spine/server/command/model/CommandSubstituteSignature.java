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

package io.spine.server.command.model;

import com.google.common.reflect.TypeToken;
import io.spine.base.CommandMessage;
import io.spine.server.command.Command;
import io.spine.server.model.MethodParams;
import io.spine.server.model.ParameterSpec;
import io.spine.server.model.ReturnTypes;
import io.spine.server.type.CommandEnvelope;

import java.lang.reflect.Method;

/**
 * A signature of {@link CommandSubstituter}.
 */
public class CommandSubstituteSignature extends CommandAcceptingSignature<CommandSubstituter> {

    private static final ReturnTypes TYPES = new ReturnTypes(
            TypeToken.of(CommandMessage.class),
            new TypeToken<Iterable<CommandMessage>>() {}
    );

    CommandSubstituteSignature() {
        super(Command.class);
    }

    @Override
    public CommandSubstituter create(Method method, ParameterSpec<CommandEnvelope> params) {
        return new CommandSubstituter(method, params);
    }

    @Override
    protected ReturnTypes returnTypes() {
        return TYPES;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This method distinguishes {@linkplain Command Commander} methods one from
     * another, as they use the same annotation, but have a different parameter list.
     * It skips the methods which first parameter
     * {@linkplain MethodParams#firstIsCommand(Method) is NOT} a {@code Command} message.
     */
    @SuppressWarnings({
            "PMD.SimplifyBooleanReturns" /* Keep this way for better readability. */,
            "UnnecessaryInheritDoc" /* IDEA bug. */
    })
    @Override
    protected boolean skipMethod(Method method) {
        var parentResult = !super.skipMethod(method);
        if (parentResult) {
            return !MethodParams.firstIsCommand(method);
        }
        return true;
    }
}
