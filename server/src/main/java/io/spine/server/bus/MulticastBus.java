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
import io.spine.core.Signal;
import io.spine.core.SignalId;
import io.spine.server.ServerEnvironment;
import io.spine.server.type.SignalEnvelope;
import io.spine.type.MessageClass;

import java.util.Collection;

/**
 * A {@code Bus}, which delivers a single message to multiple dispatchers.

 * @param <M> the type of outer objects (containing messages of interest) that are posted to the bus
 * @param <E> the type of envelopes for outer objects used by this bus
 * @param <C> the type of message class
 * @param <D> the type of dispatches used by this bus
 */
public abstract class MulticastBus<M extends Signal<?, ?, ?>,
                                   E extends SignalEnvelope<?, M, ?>,
                                   C extends MessageClass<? extends Message>,
                                   D extends MessageDispatcher<C, E>>
        extends Bus<M, E, C, D> {

    private final MulticastDispatchListener listener;

    protected MulticastBus(BusBuilder<?, M, E, C, D> builder) {
        super(builder);
        this.listener = ServerEnvironment.instance()
                                         .delivery()
                                         .dispatchListener();
    }

    /**
     * Call the dispatchers for the {@code messageEnvelope}.
     *
     * @param messageEnvelope
     *         the message envelope to pass to the dispatchers
     * @return the number of the dispatchers called or {@code 0} if there weren't any
     */
    @SuppressWarnings("ResultOfMethodCallIgnored"
            /* Dispatching outcomes are reported via system events, if needed. */)
    protected int callDispatchers(E messageEnvelope) {
        Collection<D> dispatchers = registry().dispatchersOf(messageEnvelope);
        for (var dispatcher : dispatchers) {
            dispatcher.dispatch(messageEnvelope);
        }
        return dispatchers.size();
    }

    @Override
    protected final void onDispatchingStarted(SignalId signal) {
        listener.onStarted(signal);
    }

    @Override
    protected final void onDispatched(SignalId signal) {
        listener.onCompleted(signal);
    }
}
