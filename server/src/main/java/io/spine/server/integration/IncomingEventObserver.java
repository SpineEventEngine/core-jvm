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
package io.spine.server.integration;

import io.spine.core.BoundedContextName;
import io.spine.core.Event;
import io.spine.protobuf.AnyPacker;
import io.spine.server.tenant.TenantAwareRunner;
import io.spine.server.type.EventClass;

import static io.spine.grpc.StreamObservers.noOpObserver;

/**
 * An observer of the incoming {@code external} domain events of the specified type.
 *
 * <p>Responsible of receiving those from the transport and dispatching to the local event bus.
 */
final class IncomingEventObserver extends AbstractChannelObserver {

    private final BusAdapter bus;

    /**
     * Creates a new observer.
     *
     * @param context
     *         the name of the Bounded Context which receives the events
     * @param eventCls
     *         the type of the observed events
     * @param bus
     *         the adapter over the event bus to which the observed events should be dispatched
     */
    IncomingEventObserver(BoundedContextName context, EventClass eventCls, BusAdapter bus) {
        super(context, eventCls.value());
        this.bus = bus;
    }

    @Override
    protected void handle(ExternalMessage message) {
        var event = AnyPacker.unpack(message.getOriginalMessage(), Event.class);
        TenantAwareRunner.with(event.tenant())
                         .run(() -> bus.dispatch(event, noOpObserver()));
    }
}
