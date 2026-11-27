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

package io.spine.server.event.model;

import io.spine.base.EventMessage;
import io.spine.server.model.AbstractReceptor;
import io.spine.server.model.DispatchKey;
import io.spine.server.model.MethodParams;
import io.spine.server.model.ParameterSpec;
import io.spine.server.type.EventClass;
import io.spine.server.type.EventEnvelope;

import java.lang.reflect.Method;

/**
 * An abstract base for methods handling events.
 *
 * @param <T>
 *         the type of the target object
 */
public abstract class EventReceptor<T>
        extends AbstractReceptor<T, EventMessage, EventClass, EventEnvelope>
        implements RejectionHandler<T> {

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
     * <p>If the method is annotated to accept {@code external} events, the event passed to the
     * handler method must be produced
     * {@linkplain io.spine.core.EventContext#getExternal() outside} of the Bounded Context to
     * which the handling entity belongs.
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
