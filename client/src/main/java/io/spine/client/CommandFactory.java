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

package io.spine.client;

import com.google.protobuf.Any;
import io.spine.annotation.VisibleForTesting;
import io.spine.base.CommandMessage;
import io.spine.core.Command;
import io.spine.core.CommandContext;
import io.spine.core.CommandId;
import io.spine.protobuf.AnyPacker;
import io.spine.validation.ValidationException;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.validation.Validate.check;

/**
 * A factory of {@link Command} instances.
 *
 * <p>Uses the given {@link ActorRequestFactory} as a source of command meta information
 * such as the actor, the tenant, and others.
 *
 * <p>The command messages passed to the factory are
 * {@linkplain io.spine.validation.Validate#check(com.google.protobuf.Message) validated}
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
     * <p>The {@code targetVersion} parameter defines the version of the entity that handles
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
     * Creates a command context for a new command.
     */
    @VisibleForTesting
    CommandContext createContext() {
        var actorContext = actorRequestFactory.newActorContext();
        return CommandContext.newBuilder()
                .setActorContext(actorContext)
                .build();
    }

    /**
     * Creates a command context for a new command with entity ID.
     */
    private CommandContext createContext(int targetVersion) {
        var actorContext = actorRequestFactory.newActorContext();
        return CommandContext.newBuilder()
                .setActorContext(actorContext)
                .setTargetVersion(targetVersion)
                .build();
    }
}
