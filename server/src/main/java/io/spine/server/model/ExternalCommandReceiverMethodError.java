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

package io.spine.server.model;

import io.spine.annotation.Internal;
import io.spine.server.command.model.CommandReceptor;
import io.spine.server.command.model.CommandHandlingClass;

import java.util.Collection;

import static io.spine.string.Diags.toEnumerationBackticked;

/**
 * An error thrown when one or more of the command accepting methods are marked {@code external}
 * in the {@link CommandHandlingClass}.
 *
 * <p>Although technically it's possible for entities like
 * {@linkplain io.spine.server.command.Command commanders} to declare their command substitution
 * methods as {@linkplain io.spine.core.External external}, there is no notion
 * of "external" commands in the system, and, to avoid confusion, such declarations should be
 * avoided.
 *
 * <p>Example of a faulty method:
 * <pre>
 *{@literal @Command}
 * public StartProject on(@External CreateProject command) { ... }
 * </pre>
 */
public final class ExternalCommandReceiverMethodError extends ModelError {

    private static final long serialVersionUID = 0L;

    /**
     * Creates a new exception for the command handler that violates the {@code @Command} semantic.
     */
    @Internal
    public ExternalCommandReceiverMethodError(
            CommandHandlingClass<?> classWithViolation,
            Collection<? extends CommandReceptor<?>> invalidMethods) {
        super("The class `%s` declares `external` command receiver methods for command types: %s. "
                      + "Only event accepting methods should be marked as `external`.",
              classWithViolation, handledCommandTypes(invalidMethods));
    }

    private static
    String handledCommandTypes(Collection<? extends CommandReceptor<?>> receptors) {
        var result = receptors
                .stream()
                .map(CommandReceptor::messageClass)
                .collect(toEnumerationBackticked());
        return result;
    }
}
