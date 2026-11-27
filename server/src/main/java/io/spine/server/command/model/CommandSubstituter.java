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
        extends CommandReceptor<CommandReceiver>
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
