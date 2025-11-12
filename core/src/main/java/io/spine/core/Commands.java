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

package io.spine.core;

import com.google.protobuf.Message;
import com.google.protobuf.util.Timestamps;
import io.spine.annotation.SPI;
import io.spine.base.CommandMessage;
import io.spine.string.Stringifier;
import io.spine.string.StringifierRegistry;

import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.protobuf.Messages.ensureUnpacked;

/**
 * Client-side utilities for working with commands.
 */
@SPI
public final class Commands {

    private static final Stringifier<CommandId> idStringifier = new CommandIdStringifier();

    static {
        StringifierRegistry.instance()
                           .register(idStringifier(), CommandId.class);
    }

    /** Prevent instantiation of this utility class. */
    private Commands() {
    }

    /**
     * Extracts a command message if the passed instance is a {@link Command} object or
     * {@link com.google.protobuf.Any Any}, otherwise returns the passed message.
     */
    public static CommandMessage ensureMessage(Message commandOrMessage) {
        checkNotNull(commandOrMessage);
        if (commandOrMessage instanceof Command) {
            return ((Command) commandOrMessage).enclosedMessage();
        }
        var unpacked = (CommandMessage) ensureUnpacked(commandOrMessage);
        return unpacked;
    }

    /**
     * Sorts the command given command request list by command timestamp value.
     *
     * @param commands the command list to sort
     */
    public static void sort(List<Command> commands) {
        checkNotNull(commands);
        commands.sort((c1, c2) -> Timestamps.compare(c1.timestamp(), c2.timestamp()));
    }

    /**
     * Obtains stringifier for command IDs.
     */
    public static Stringifier<CommandId> idStringifier() {
        return idStringifier;
    }

    /**
     * The stringifier for command IDs.
     */
    static class CommandIdStringifier extends Stringifier<CommandId> {
        @Override
        protected String toString(CommandId commandId) {
            var result = commandId.getUuid();
            return result;
        }

        @Override
        protected CommandId fromString(String str) {
            var result = CommandId.of(str);
            return result;
        }
    }
}
