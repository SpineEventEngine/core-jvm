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

package io.spine.server.model;

import io.spine.server.command.model.CommandHandlingClass;
import io.spine.server.command.model.CommandReceptor;
import io.spine.server.type.CommandClass;

import java.io.Serial;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static io.spine.string.Diags.backtick;
import static io.spine.string.Diags.toEnumerationBackticked;
import static java.lang.String.format;

/**
 * An error thrown on attempt to add a class which declares a
 * {@linkplain CommandReceptor receptor} that handles
 * a command which is already handled by another class in the {@link Model}.
 */
public final class DuplicateCommandReceptorError extends ModelError {

    @Serial
    private static final long serialVersionUID = 0L;

    DuplicateCommandReceptorError(
            CommandHandlingClass<?, ?> duplicatingClass,
            Map<Set<CommandClass>, CommandHandlingClass<?, ?>> registeredHandlers) {
        super(fmt(duplicatingClass, registeredHandlers));
    }

    private static String
    fmt(CommandHandlingClass<?, ?> duplicatingClass,
        Map<Set<CommandClass>, CommandHandlingClass<?, ?>> registeredHandlers) {
        checkNotNull(duplicatingClass);
        checkNotNull(registeredHandlers);
        @SuppressWarnings("MagicNumber") // the buffer size that should cover most cases.
        var builder = new StringBuilder(512);

        builder.append(format("The class `%s` declares handler ", duplicatingClass));

        // Do we have more than one command to report?
        var totalDuplicatedCommands = registeredHandlers.keySet()
                .stream()
                .mapToLong(Collection::size)
                .sum();
        checkState(totalDuplicatedCommands >= 1);
        builder.append(
                totalDuplicatedCommands == 1
                ? "method for the command which is "
                : "methods for commands that are "
        );
        builder.append("already handled by ");

        // How many handling classes do we have already?
        long totalHandlingClasses = registeredHandlers.values()
                                                      .size();
        builder.append(
                totalHandlingClasses == 1
                ? "another class."
                : "other classes."
        );

        var newLine = format("%n");
        builder.append(newLine);

        // Now list the commands and their handlers.
        for (var commandClasses : registeredHandlers.keySet()) {
            builder.append(newLine);
            if (commandClasses.size() > 1) {
                builder.append(" Commands ");
                var commandsBackTicked = commandClasses.stream()
                        .collect(toEnumerationBackticked());
                builder.append(commandsBackTicked);
                builder.append(" are handled by ");
            } else {
                // One command.
                builder.append(" The command ");
                var cmdClass = commandClasses.iterator()
                                             .next();
                builder.append(backtick(cmdClass));
                builder.append(" is handled by ");
            }
            var handlingClass = registeredHandlers.get(commandClasses);
            builder.append(backtick(handlingClass));
            builder.append('.');
        }
        return builder.toString();
    }
}
