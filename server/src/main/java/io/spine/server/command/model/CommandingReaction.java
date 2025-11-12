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

package io.spine.server.command.model;

import io.spine.base.EventMessage;
import io.spine.server.dispatch.Success;
import io.spine.server.event.EventReceiver;
import io.spine.server.model.AbstractReceptor;
import io.spine.server.model.ParameterSpec;
import io.spine.server.type.CommandClass;
import io.spine.server.type.EventClass;
import io.spine.server.type.EventEnvelope;

import java.lang.reflect.Method;

/**
 * A method which <em>may</em> generate one or more command messages in response to an event.
 */
public final class CommandingReaction
        extends AbstractReceptor<EventReceiver,
                                 EventMessage,
                                 EventClass,
                                 EventEnvelope,
                                 CommandClass>
        implements CommandingMethod<EventReceiver, EventClass, EventEnvelope> {

    CommandingReaction(Method method, ParameterSpec<EventEnvelope> signature) {
        super(method, signature);
    }

    @Override
    public EventClass messageClass() {
        return EventClass.from(rawMessageClass());
    }

    /**
     * Returns an empty {@code Success} instance which means that the method ignored
     * the incoming event.
     */
    @Override
    public Success fromEmpty(EventEnvelope handledSignal) {
        return Success.newBuilder()
                      .build();
    }

    /**
     * Ensures that the domestic events are dispatched to the domestic-event handlers
     * and the external events are dispatched to the external-event handlers.
     *
     * @see io.spine.core.External
     */
    @Override
    protected void checkAttributesMatch(EventEnvelope envelope) {
        super.checkAttributesMatch(envelope);
        var expected = envelope.context().getExternal();
        ensureExternalMatch(expected);
    }
}
