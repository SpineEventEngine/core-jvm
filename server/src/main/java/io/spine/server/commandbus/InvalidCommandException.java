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

import com.google.protobuf.Message;
import com.google.protobuf.Value;
import io.spine.base.CommandMessage;
import io.spine.base.Error;
import io.spine.base.Identifier;
import io.spine.core.Command;
import io.spine.core.CommandValidationError;
import io.spine.server.MessageInvalid;
import io.spine.server.type.CommandClass;
import io.spine.server.type.CommandEnvelope;
import io.spine.type.TypeName;
import io.spine.validate.ConstraintViolation;
import io.spine.validate.ExceptionFactory;

import java.io.Serial;
import java.util.Map;

import static io.spine.type.ProtoTexts.shortDebugString;
import static java.lang.String.format;

/**
 * The exception for reporting invalid commands.
 *
 * <p>A command is invalid if it is supported (i.e. there is a receptor for the command), but its
 * attributes are not populated according to framework conventions <strong>OR</strong>
 * validation constraints of the command message are violated.
 */
public class InvalidCommandException extends CommandException implements MessageInvalid {

    private static final long serialVersionUID = 0L;

    private static final String MSG_VALIDATION_ERROR = "Command message does not match " +
                                                       "validation constrains.";

    private InvalidCommandException(String messageText, Command command, Error error) {
        super(messageText, command, error);
    }

    /**
     * Creates an exception instance for a command message,
     * which has fields that violate validation constraint(s).
     *
     * @param command    an invalid command
     * @param violations constraint violations for the command message
     */
    public static InvalidCommandException onConstraintViolations(
            Command command, Iterable<ConstraintViolation> violations) {

        var helper = new Factory(command, violations);
        return helper.newException();
    }

    /**
     * Creates an exception for a command with missing {@code tenant_id} attribute in
     * the {@code CommandContext} which is required in a multitenant application.
     */
    public static InvalidCommandException missingTenantId(Command command) {
        var envelope = CommandEnvelope.of(command);
        Message commandMessage = envelope.message();
        var errMsg = format(
                "The command (class: `%s`, type: `%s`, id: `%s`) is posted to " +
                "multi-tenant Bounded Context, but has no `tenant_id` attribute in the context.",
                CommandClass.of(commandMessage)
                            .value()
                            .getName(),
                TypeName.of(commandMessage),
                Identifier.toString(envelope.id()));
        var error = unknownTenantError(commandMessage, errMsg);
        return new InvalidCommandException(errMsg, command, error);
    }

    /**
     * Creates an error for a command with missing {@code tenant_id}
     * attribute in the {@code CommandContext} which is required in a multitenant application.
     */
    public static Error unknownTenantError(Message commandMessage, String errorText) {
        var error = Error.newBuilder()
                .setType(InvalidCommandException.class.getCanonicalName())
                .setCode(CommandValidationError.TENANT_UNKNOWN.getNumber())
                .setMessage(errorText)
                .putAllAttributes(commandTypeAttribute(commandMessage))
                .build();
        return error;
    }

    /**
     * Creates an exception for the command which specifies a tenant in a single-tenant context.
     */
    public static InvalidCommandException inapplicableTenantId(Command command) {
        var cmd = CommandEnvelope.of(command);
        var typeName = TypeName.of(cmd.message());
        var errMsg = format(
                "The command (class: `%s`, type: `%s`, id: `%s`) was posted to a single-tenant " +
                "Bounded Context, but has `tenant_id` (`%s`) attribute set in the command context.",
                cmd.messageClass(),
                typeName,
                shortDebugString(cmd.id()),
                shortDebugString(cmd.tenantId()));
        var error = inapplicableTenantError(cmd.message(), errMsg);
        return new InvalidCommandException(errMsg, command, error);
    }

    private static Error inapplicableTenantError(CommandMessage commandMessage, String errMsg) {
        var error = Error.newBuilder()
                .setType(InvalidCommandException.class.getCanonicalName())
                .setCode(CommandValidationError.TENANT_INAPPLICABLE.getNumber())
                .setMessage(errMsg)
                .putAllAttributes(commandTypeAttribute(commandMessage))
                .build();
        return error;
    }

    /**
     * A helper utility aimed to create an {@code InvalidCommandException} to report the
     * command which field values violate validation constraint(s).
     */
    private static final class Factory
            extends ExceptionFactory<InvalidCommandException,
                                     Command,
                                     CommandClass,
                                     CommandValidationError> {

        private final CommandClass commandClass;

        private Factory(Command command, Iterable<ConstraintViolation> violations) {
            super(command, violations);
            this.commandClass = CommandClass.of(command);
        }

        @Override
        protected CommandClass getMessageClass() {
            return commandClass;
        }

        @Override
        protected CommandValidationError getErrorCode() {
            return CommandValidationError.INVALID_COMMAND;
        }

        @Override
        protected String getErrorText() {
            return MSG_VALIDATION_ERROR;
        }

        @Override
        protected Map<String, Value> getMessageTypeAttribute(Message commandMessage) {
            return commandTypeAttribute(commandMessage);
        }

        @Override
        protected InvalidCommandException createException(String exceptionMsg,
                                                          Command command,
                                                          Error error) {
            return new InvalidCommandException(exceptionMsg, command, error);
        }
    }
}
