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

package io.spine.server.event.model;

import io.spine.base.EventMessage;
import io.spine.server.model.AbstractReceptor;
import io.spine.server.model.DispatchKey;
import io.spine.server.model.MethodParams;
import io.spine.server.model.ParameterSpec;
import io.spine.server.type.EventClass;
import io.spine.server.type.EventEnvelope;
import io.spine.type.MessageClass;

import java.lang.reflect.Method;

/**
 * An abstract base for methods handling events.
 *
 * @param <T>
 *         the type of the target object
 * @param <R>
 *         the type of the produced message classes
 */
public abstract class EventReceptor<T, R extends MessageClass<?>>
        extends AbstractReceptor<T, EventMessage, EventClass, EventEnvelope, R>
        implements RejectionHandler<T, R> {

    /**
     * Creates a new instance to wrap {@code method} on {@code target}.
     *
     * @param method
     *         subscriber method
     */
    EventReceptor(Method method, ParameterSpec<EventEnvelope> parameterSpec) {
        super(method, parameterSpec);
    }

    @Override
    public EventAcceptingMethodParams parameterSpec() {
        return (EventAcceptingMethodParams) super.parameterSpec();
    }

    @Override
    public DispatchKey key() {
        return RejectionHandler.super.key();
    }

    @Override
    public MethodParams params() {
        if (RejectionHandler.super.handlesRejection()) {
            var result = MethodParams.of(rawMethod());
            return result;
        }
        return super.params();
    }

    /**
     * Ensures that the passed event matches the {@code external} attribute
     * of the method annotation.
     *
     * <p>If the method annotated to accept {@code external} events, the event passed to the handler
     * method must be produced {@linkplain io.spine.core.EventContext#getExternal() outside} of
     * the Bounded Context to which the handling entity belongs.
     *
     * <p>And vice versa, if the event handling method is designed for domestic events,
     * it does not accept external events.
     *
     * @see io.spine.core.External
     */
    @Override
    protected void checkAttributesMatch(EventEnvelope event) {
        var external = event.context().getExternal();
        ensureExternalMatch(external);
    }
}
