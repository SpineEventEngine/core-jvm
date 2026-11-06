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

package io.spine.server.bus;

import com.google.protobuf.Message;
import io.spine.core.Ack;
import io.spine.core.ErrorCase;
import io.spine.server.type.MessageEnvelope;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A filter validating the {@linkplain MessageEnvelope envelopes} with the given
 * {@link EnvelopeValidator}.
 *
 * <p>An invalid message is passed to the given {@link InvalidMessageHandler}, and then refused
 * with {@link ErrorCase#INVALID_REQUEST INVALID_REQUEST}.
 *
 * @param <E>
 *         the type of the validated envelopes
 * @param <T>
 *         the type of the messages packed into the validated envelopes
 */
final class ValidatingFilter<E extends MessageEnvelope<?, T, ?>, T extends Message>
        implements BusFilter<E> {

    private final EnvelopeValidator<E> validator;
    private final InvalidMessageHandler<E> invalidMessageHandler;

    ValidatingFilter(EnvelopeValidator<E> validator,
                     InvalidMessageHandler<E> invalidMessageHandler) {
        super();
        this.validator = checkNotNull(validator);
        this.invalidMessageHandler = checkNotNull(invalidMessageHandler);
    }

    @Override
    public Optional<Ack> filter(E envelope) {
        checkNotNull(envelope);
        var violations = validator.validate(envelope);
        if (violations.isPresent()) {
            invalidMessageHandler.handle(envelope, violations.get());
            return reject(envelope, ErrorCase.INVALID_REQUEST);
        } else {
            return letPass();
        }
    }
}
