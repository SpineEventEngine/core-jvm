/*
 * Copyright 2026, TeamDev. All rights reserved.
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

package io.spine.server.integration;

import com.google.protobuf.Message;
import io.grpc.stub.StreamObserver;
import io.spine.base.EventMessage;
import io.spine.core.Ack;
import io.spine.core.Event;
import io.spine.server.event.EventBus;
import io.spine.server.event.EventDispatcher;
import io.spine.server.type.EventClass;

import static io.spine.core.Events.toExternal;

/**
 * An adapter between the {@link IntegrationBroker} and the {@link EventBus}.
 */
final class BusAdapter {

    private final IntegrationBroker broker;

    /**
     * The wrapped local event bus.
     */
    private final EventBus targetBus;

    BusAdapter(IntegrationBroker broker, EventBus targetBus) {
        this.broker = broker;
        this.targetBus = targetBus;
    }

    void dispatch(Event event, StreamObserver<Ack> ackObserver) {
        targetBus.post(toExternal(event), ackObserver);
    }

    void register(Class<? extends Message> messageClass) {
        var dispatcher = createDispatcher(messageClass);
        targetBus.register(dispatcher);
    }

    void unregister(Class<? extends Message> messageClass) {
        var dispatcher = createDispatcher(messageClass);
        targetBus.unregister(dispatcher);
    }

    /**
     * Creates a dispatcher suitable for the wrapped local bus, dispatching the messages of
     * the given class.
     *
     * <p>The created dispatcher is serving as a listener, notifying the {@code IntegrationBroker}
     * of the messages that are requested by the collaborators outside this bounded context.
     *
     * @param messageClass
     *         the class of message to be dispatched by the created dispatcher
     * @return a dispatcher for the local bus
     */
    private EventDispatcher createDispatcher(Class<? extends Message> messageClass) {
        @SuppressWarnings("unchecked") // Logically checked.
        var eventClass = (Class<? extends EventMessage>) messageClass;
        var eventType = EventClass.from(eventClass);
        EventDispatcher result = new DomesticEventPublisher(broker, eventType);
        return result;
    }
}
