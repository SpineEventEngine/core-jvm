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

package io.spine.server.commandbus;

import com.google.common.collect.ImmutableMap;
import com.google.protobuf.Message;
import com.google.protobuf.Value;
import io.spine.base.Error;
import io.spine.code.java.ClassName;
import io.spine.core.Command;
import io.spine.core.CommandValidationError;
import io.spine.server.MessageError;
import io.spine.server.type.CommandEnvelope;
import io.spine.type.TypeName;

import java.io.Serial;
import java.util.Map;

import static java.lang.String.format;

/**
 * Abstract base for exceptions related to commands.
 */
public abstract class CommandException extends RuntimeException implements MessageError {

    /**
     * The name of the attribute of the command type reported in an error.
     *
     * @see #commandTypeAttribute(Message)
     * @see Error
     */
    public static final String ATTR_COMMAND_TYPE_NAME = "commandType";

    @Serial
    private static final long serialVersionUID = 0L;

    private final Command command;
    private final Error error;

    /**
     * Creates a new instance.
     *
     * @param messageText an error message text
     * @param command a related command
     * @param error an error occurred
     */
    protected CommandException(String messageText, Command command, Error error) {
        super(messageText);
        this.command = command;
        this.error = error;
    }

    /**
     * Returns a map with a command type attribute.
     *
     * @param commandMessage a command message to get the type from
     */
    public static Map<String, Value> commandTypeAttribute(Message commandMessage) {
        var commandType = TypeName.of(commandMessage).value();
        var value = Value.newBuilder()
                .setStringValue(commandType)
                .build();
        return ImmutableMap.of(ATTR_COMMAND_TYPE_NAME, value);
    }

    protected static Error createError(String format,
                                       Command command,
                                       CommandValidationError errorCode) {
        Message commandMessage = CommandEnvelope.of(command)
                                                .message();

        var commandType = commandMessage.getDescriptorForType()
                                        .getFullName();
        var errMsg = format(format, commandType);
        var descriptorName = CommandValidationError.getDescriptor()
                                                   .getFullName();
        var error = Error.newBuilder()
                .setType(descriptorName)
                .setMessage(errMsg)
                .setCode(errorCode.getNumber())
                .putAllAttributes(commandTypeAttribute(commandMessage));
        return error.build();
    }

    /** Returns a related command. */
    public Command getCommand() {
        return command;
    }

    @Override
    public Error asError() {
        return error;
    }

    @Override
    public Throwable asThrowable() {
        return this;
    }

    /**
     * Builds a formatted string for the passed format and a command.
     *
     * <p>The first parameter of the formatted string is a {@link ClassName} of the command message.
     *
     * <p>The second parameter is a {@link TypeName} of the command message.
     */
    protected static String messageFormat(String format, Command command) {
        var envelope = CommandEnvelope.of(command);
        Class<? extends Message> commandClass = envelope.messageClass().value();
        var commandClassName = ClassName.of(commandClass);
        var typeName = envelope.messageTypeName();
        var result = format(format, commandClassName, typeName);
        return result;
    }
}
