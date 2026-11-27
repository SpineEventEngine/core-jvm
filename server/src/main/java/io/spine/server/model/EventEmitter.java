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

package io.spine.server.model;

import com.google.errorprone.annotations.Immutable;
import io.spine.base.EventMessage;
import io.spine.server.EventProducer;
import io.spine.server.dispatch.ProducedEvents;
import io.spine.server.dispatch.Success;
import io.spine.server.event.EventFactory;
import io.spine.server.type.MessageEnvelope;
import io.spine.type.MessageClass;
import org.jspecify.annotations.Nullable;

/**
 * A {@link Receptor} that produces events in response to a signal.
 *
 * @param <T>
 *         the type of the target object
 * @param <C>
 *         the type of the incoming message class
 * @param <E>
 *         the type of the {@link MessageEnvelope} wrapping the method arguments
 */
@Immutable
public interface EventEmitter<T extends EventProducer,
                              C extends MessageClass<?>,
                              E extends MessageEnvelope<?, ?, ?>>
        extends Receptor<T, C, E> {

    /**
     * Produces an outcome in case the event producing method call finished successfully.
     *
     * @implNote This method does not perform the validation of the resulting
     *          {@code Success} message, as all of its parts (mostly
     *          {@link io.spine.core.Event Event} instances) are validated beforehand.
     */
    @Override
    default Success toSuccessfulOutcome(@Nullable Object rawResult, T target, E handledSignal) {
        var result = MethodResult.from(rawResult);
        var eventFactory = EventFactory.on(handledSignal, target.producerId());
        var version = target.version();

        var producedEvents = ProducedEvents.newBuilder();
        var eventMessages = result.messages(EventMessage.class);
        for (var msg : eventMessages) {
            var event = eventFactory.createEvent(msg, version);
            producedEvents.addEvent(event);
        }
        return Success.newBuilder()
                .setProducedEvents(producedEvents)
                .build();
    }
}
