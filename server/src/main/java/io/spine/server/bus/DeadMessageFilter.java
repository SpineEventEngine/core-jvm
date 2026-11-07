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
import io.spine.type.MessageClass;

import java.util.Collection;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The {@link BusFilter} preventing the messages that have no dispatchers from being posted to
 * the bus.
 *
 * <p>Such a message is passed to the given {@link DeadMessageHandler}, and then refused with
 * {@link ErrorCase#UNSUPPORTED_REQUEST UNSUPPORTED_REQUEST}.
 *
 * @param <T>
 *         the type of objects wrapping a passed message
 * @param <E>
 *         the type of envelopes of the passed messages
 * @param <C>
 *         the message class corresponding to the passed messages
 * @param <D>
 *         the type of dispatchers that were expected for the message
 */
final class DeadMessageFilter<T extends Message,
                              E extends MessageEnvelope<?, T, ?>,
                              C extends MessageClass<? extends Message>,
                              D extends MessageDispatcher<C, E>>
        implements BusFilter<E> {

    private final DeadMessageHandler<E> deadMessageHandler;
    private final DispatcherRegistry<C, E, D> registry;

    DeadMessageFilter(DeadMessageHandler<E> deadMessageHandler,
                      DispatcherRegistry<C, E, D> registry) {
        super();
        this.deadMessageHandler = checkNotNull(deadMessageHandler);
        this.registry = checkNotNull(registry);
    }

    @Override
    public Optional<Ack> filter(E envelope) {
        Collection<D> dispatchers = registry.dispatchersOf(envelope);
        if (dispatchers.isEmpty()) {
            deadMessageHandler.handle(envelope);
            return reject(envelope, ErrorCase.UNSUPPORTED_REQUEST);
        } else {
            return letPass();
        }
    }
}
