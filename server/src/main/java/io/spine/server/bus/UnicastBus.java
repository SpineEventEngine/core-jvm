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

package io.spine.server.bus;

import com.google.protobuf.Message;
import io.spine.annotation.Internal;
import io.spine.base.Identifier;
import io.spine.core.Signal;
import io.spine.server.type.MessageEnvelope;
import io.spine.server.type.SignalEnvelope;
import io.spine.type.MessageClass;

import static io.spine.util.Exceptions.newIllegalStateException;

/**
 * A bus that delivers a message to one dispatcher.
 *
 * @param <T>
 *         the type of the delivered signals
 * @param <E>
 *         the type of the envelopes of the delivered signals
 * @param <C>
 *         the message class corresponding to the delivered signal
 * @param <D>
 *         the type of dispatchers which receive the signals
 * @see MulticastBus
 */
@Internal
public abstract class UnicastBus<T extends Signal<?, ?, ?>,
                                 E extends SignalEnvelope<?, T, ?>,
                                 C extends MessageClass<? extends Message>,
                                 D extends MessageDispatcher<C, E>>
        extends Bus<T, E, C, D> {

    protected UnicastBus(BusBuilder<?, T, E, C, D> builder) {
        super(builder);
    }

    protected D dispatcherOf(E envelope) {
        return registry().dispatcherOf(envelope)
                         .orElseThrow(() -> noDispatcherFound(envelope));
    }

    private static IllegalStateException noDispatcherFound(MessageEnvelope<?, ?, ?> envelope) {
        var id = Identifier.toString(envelope.id());
        throw newIllegalStateException(
                "No dispatcher found for the command (class: `%s` id: `%s`).",
                envelope.messageClass(), id
        );
    }
}
