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

package io.spine.server.type;

import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Any;
import com.google.protobuf.Message;
import io.spine.base.CommandMessage;
import io.spine.core.Command;
import io.spine.core.Commands;
import io.spine.type.MessageClass;
import io.spine.type.TypeUrl;

import java.io.Serial;
import java.util.Arrays;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A value object for class type references.
 */
public final class CommandClass extends MessageClass<CommandMessage> {

    @Serial
    private static final long serialVersionUID = 0L;

    private CommandClass(Class<? extends CommandMessage> value) {
        super(value);
    }

    private CommandClass(Class<? extends CommandMessage> value, TypeUrl typeUrl) {
        super(value, typeUrl);
    }

    /**
     * Creates a new instance for the passed class value.
     *
     * @param value class reference
     * @return new instance
     */
    public static CommandClass from(Class<? extends CommandMessage> value) {
        return new CommandClass(checkNotNull(value));
    }

    /**
     * Creates a new instance for the class of the passed command.
     *
     * <p>If an instance of {@link Command} is passed to this method, enclosing command message will
     * be un-wrapped to determine the class of the command.
     *
     * <p>If an instance of {@link Any} is passed, it will be unpacked, and the class of the wrapped
     * message will be used.
     *
     * @param commandOrMessage a command for which to get the class
     * @return new instance
     */
    public static CommandClass of(Message commandOrMessage) {
        var commandMessage = Commands.ensureMessage(commandOrMessage);
        var typeUrl = TypeUrl.of(commandMessage.getDefaultInstanceForType());
        return new CommandClass(commandMessage.getClass(), typeUrl);
    }

    /**
     * Creates a set of {@code CommandClass} from the passed set.
     */
    public static ImmutableSet<CommandClass> setOf(Iterable<Class<? extends CommandMessage>> classes) {
        ImmutableSet.Builder<CommandClass> builder = ImmutableSet.builder();
        for (var cls : classes) {
            builder.add(from(cls));
        }
        return builder.build();
    }

    /**
     * Creates a set of {@code CommandClass} from the passed classes.
     */
    @SafeVarargs
    public static ImmutableSet<CommandClass> setOf(Class<? extends CommandMessage>... classes) {
        return setOf(Arrays.asList(classes));
    }
}
