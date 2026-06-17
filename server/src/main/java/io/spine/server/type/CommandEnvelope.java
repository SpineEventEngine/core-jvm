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

import io.spine.base.CommandMessage;
import io.spine.core.Command;
import io.spine.core.CommandContext;
import io.spine.core.CommandId;
import io.spine.core.TenantId;
import io.spine.type.TypeName;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The holder of a {@code Command} that provides convenient access to its properties.
 */
public final class CommandEnvelope
        extends AbstractMessageEnvelope<CommandId, Command, CommandContext>
        implements SignalEnvelope<CommandId, Command, CommandContext> {

    /** The command class. */
    private final CommandClass commandClass;

    private CommandEnvelope(Command command) {
        super(command);
        this.commandClass = CommandClass.of(command.enclosedMessage());
    }

    /**
     * Creates an instance with the passed command.
     */
    public static CommandEnvelope of(Command command) {
        checkNotNull(command);
        return new CommandEnvelope(command);
    }

    /**
     * Obtains the enclosed command object.
     */
    public Command command() {
        return outerObject();
    }

    /**
     * Obtains the tenant ID of the command.
     */
    @Override
    public TenantId tenantId() {
        return command().tenant();
    }

    /**
     * Obtains the command ID.
     */
    @Override
    public CommandId id() {
        return command().getId();
    }

    /**
     * Obtains the command message.
     */
    @Override
    public CommandMessage message() {
        return command().enclosedMessage();
    }

    /**
     * Obtains the command class.
     */
    @Override
    public CommandClass messageClass() {
        return commandClass;
    }

    /**
     * Obtains the command context.
     */
    @Override
    public CommandContext context() {
        return outerObject().context();
    }

    /**
     * Obtains {@link TypeName} of the command message.
     */
    public TypeName messageTypeName() {
        return TypeName.of(message());
    }
}
