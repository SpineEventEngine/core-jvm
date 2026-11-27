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

package io.spine.server.command.model;

import io.spine.base.EventMessage;
import io.spine.server.dispatch.Success;
import io.spine.server.event.EventReceiver;
import io.spine.server.model.AbstractReceptor;
import io.spine.server.model.ParameterSpec;
import io.spine.server.type.EventClass;
import io.spine.server.type.EventEnvelope;

import java.lang.reflect.Method;

/**
 * A method that <em>may</em> generate one or more command messages in response to an event.
 */
public final class CommandingReaction
        extends AbstractReceptor<EventReceiver,
                                 EventMessage,
                                 EventClass,
                                 EventEnvelope>
        implements CommandingMethod<EventReceiver, EventClass, EventEnvelope> {

    CommandingReaction(Method method, ParameterSpec<EventEnvelope> signature) {
        super(method, signature);
    }

    @Override
    public EventClass messageClass() {
        return EventClass.from(rawMessageClass());
    }

    /**
     * Returns an empty {@code Success} instance that means that the method ignored
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
