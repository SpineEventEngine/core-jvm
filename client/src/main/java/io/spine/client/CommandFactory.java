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

package io.spine.client;

import com.google.protobuf.Any;
import io.spine.annotation.Internal;
import io.spine.annotation.VisibleForTesting;
import io.spine.base.CommandMessage;
import io.spine.core.Command;
import io.spine.core.CommandContext;
import io.spine.core.CommandId;
import io.spine.protobuf.AnyPacker;
import io.spine.validate.ValidationException;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.base.Time.currentTime;
import static io.spine.validate.Validate.check;

/**
 * A factory of {@link Command} instances.
 *
 * <p>Uses the given {@link ActorRequestFactory} as a source of command meta information
 * such as the actor, the tenant, and others.
 *
 * <p>The command messages passed to the factory are
 * {@linkplain io.spine.validate.Validate#check(com.google.protobuf.Message) validated}
 * according to their Proto definitions. If a given message is invalid,
 * a {@link ValidationException} is thrown.
 *
 * @see ActorRequestFactory#command()
 */
public final class CommandFactory {

    private final ActorRequestFactory actorRequestFactory;

    CommandFactory(ActorRequestFactory actorRequestFactory) {
        this.actorRequestFactory = checkNotNull(actorRequestFactory);
    }

    /**
     * Creates a new {@link Command} with the given message.
     *
     * @param message
     *         the command message
     * @return new command instance
     * @throws ValidationException
     *         if the passed message does not satisfy the constraints,
     *         set for it in its Protobuf definition
     */
    public Command create(CommandMessage message) throws ValidationException {
        checkNotNull(message);
        check(message);

        var context = createContext();
        var result = createCommand(message, context);
        return result;
    }

    /**
     * Creates a new {@code Command} with the passed message and target entity version.
     *
     * <p>The {@code targetVersion} parameter defines the version of the entity which handles
     * the resulting command. Note that the framework performs no validation of the target version
     * before a command is handled. Instead, users themselves can perform validation.
     *
     * @param message
     *         the command message
     * @param targetVersion
     *         the version of the entity for which this command is intended
     * @return new command instance
     * @throws ValidationException
     *         if the passed message does not satisfy the constraints
     *         set for it in its Protobuf definition
     */
    public Command create(CommandMessage message, int targetVersion) throws ValidationException {
        checkNotNull(message);
        check(message);

        var context = createContext(targetVersion);
        var result = createCommand(message, context);
        return result;
    }

    /**
     * Creates new {@code Command} with the passed message using the existing context.
     *
     * <p>The produced command is created with a {@code CommandContext} instance, copied from
     * the given one, but with the current time set as a context timestamp.
     *
     * @param message
     *         the command message
     * @param context
     *         the command context to use as a base for the new command
     * @return new command instance
     * @throws ValidationException
     *         if the passed message does not satisfy the constraints set for it
     *         in its Protobuf definition
     */
    @Internal
    @VisibleForTesting
    public Command createBasedOnContext(CommandMessage message, CommandContext context)
            throws ValidationException {
        checkNotNull(message);
        checkNotNull(context);
        check(message);

        var newContext = withCurrentTime(context);
        var result = createCommand(message, newContext);
        return result;
    }

    /**
     * Creates a command instance with the given {@code message} and {@code context}.
     *
     * <p>If an instance of {@link Any} is passed as the {@code message} parameter, the packed
     * message is used for the command construction.
     *
     * <p>The ID of the new command instance is automatically generated.
     *
     * @param message
     *         the command message
     * @param context
     *         the context of the command
     * @return a new command
     */
    private static Command createCommand(CommandMessage message, CommandContext context) {
        var packed = AnyPacker.pack(message);
        var result = Command.newBuilder()
                .setId(CommandId.generate())
                .setMessage(packed)
                .setContext(context);
        return result.build();
    }

    /**
     * Creates command context for a new command.
     */
    @VisibleForTesting
    CommandContext createContext() {
        var actorContext = actorRequestFactory.newActorContext();
        return CommandContext.newBuilder()
                .setActorContext(actorContext)
                .build();
    }

    /**
     * Creates command context for a new command with entity ID.
     */
    private CommandContext createContext(int targetVersion) {
        var actorContext = actorRequestFactory.newActorContext();
        return CommandContext.newBuilder()
                .setActorContext(actorContext)
                .setTargetVersion(targetVersion)
                .build();
    }

    /**
     * Creates a copy of the passed {@code CommandContext} updated with the current time.
     */
    private static CommandContext withCurrentTime(CommandContext value) {
        var withCurrentTime = value.getActorContext()
                .toBuilder()
                .setTimestamp(currentTime())
                .build();
        var result = value.toBuilder()
                .setActorContext(withCurrentTime)
                .build();
        return result;
    }
}
