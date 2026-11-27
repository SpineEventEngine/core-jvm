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
import io.spine.server.dispatch.Success;
import io.spine.server.type.MessageEnvelope;
import io.spine.type.MessageClass;
import org.jspecify.annotations.Nullable;

import static java.lang.String.format;

/**
 * A {@link Receptor} that processes a message and produces no response.
 *
 * @param <T>
 *         the type of the target object
 * @param <C>
 *         the type of the incoming message class
 * @param <E>
 *         the type of the {@link MessageEnvelope} wrapping the method arguments
 */
@Immutable
public interface VoidMethod<T,
                            C extends MessageClass<?>,
                            E extends MessageEnvelope<?, ?, ?>>
        extends Receptor<T, C, E> {

    @Override
    default Success toSuccessfulOutcome(@Nullable Object result, T target, E handledSignal) {
        if (result != null) {
            var errorMessage = format(
                    "Method `%s` should NOT produce any result. Produced: `%s`.",
                    this,
                    result
            );
            throw new IllegalOutcomeException(errorMessage);
        }
        return Success.getDefaultInstance();
    }
}
