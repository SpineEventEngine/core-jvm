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

import com.google.errorprone.annotations.Immutable;
import io.spine.base.CommandMessage;
import io.spine.client.ActorRequestFactory;
import io.spine.core.Command;
import io.spine.core.Origin;
import io.spine.server.dispatch.ProducedCommands;
import io.spine.server.dispatch.Success;
import io.spine.server.type.CommandClass;
import io.spine.server.type.MessageEnvelope;
import io.spine.type.MessageClass;
import org.jspecify.annotations.Nullable;

/**
 * A {@link Receptor} that produces commands in response to a signal.
 *
 * @param <T>
 *         the type of the target object
 * @param <C>
 *         the type of the incoming message class
 * @param <E>
 *         the type of the {@link MessageEnvelope} wrapping the method arguments
 */
@Immutable
public interface CommandEmitter<T,
                                C extends MessageClass<?>,
                                E extends MessageEnvelope<?, ?, ?>>
        extends Receptor<T, C, E, CommandClass> {

    /**
     * Creates success result from the empty result of the method execution.
     *
     * @param handledSignal
     *         the signal passed to the method
     * @throws IllegalOutcomeException
     *         if the method is not allowed to return empty result
     */
    Success fromEmpty(E handledSignal);

    /**
     * Produces an outcome in case the command producing method call finished successfully.
     *
     * <p>The handled signal becomes the origin of each produced command, so that
     * the command leads back to the root of the signal chain.
     *
     * @implNote This method does not perform the validation of the resulting
     *          {@code Success} message, as all of its parts (mostly {@link Command} instances)
     *          are validated beforehand.
     */
    @Override
    default Success toSuccessfulOutcome(@Nullable Object rawResult, T target, E handledSignal) {
        var result = MethodResult.from(rawResult);
        if (result.isEmpty()) {
            return fromEmpty(handledSignal);
        }
        var origin = handledSignal.asMessageOrigin();
        var commandFactory = ActorRequestFactory.fromContext(origin.getActorContext()).command();
        var signals = ProducedCommands.newBuilder();
        var messages = result.messages(CommandMessage.class);
        for (var msg : messages) {
            var command = commandFactory.create(msg);
            signals.addCommand(withOrigin(command, origin));
        }
        return Success.newBuilder()
                .setProducedCommands(signals)
                .build();
    }

    /**
     * Creates a copy of the given command whose context has the given origin.
     */
    private static Command withOrigin(Command command, Origin origin) {
        var context = command.context()
                .toBuilder()
                .setOrigin(origin)
                .build();
        return command.toBuilder()
                .setContext(context)
                .build();
    }
}
