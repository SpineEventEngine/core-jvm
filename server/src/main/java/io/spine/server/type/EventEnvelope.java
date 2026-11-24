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

package io.spine.server.type;

import io.spine.base.EventMessage;
import io.spine.core.Event;
import io.spine.core.EventContext;
import io.spine.core.EventId;
import io.spine.core.TenantId;
import io.spine.type.MessageClass;
import io.spine.type.TypeName;
import io.spine.type.TypeUrl;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The holder of an {@code Event} that provides convenient access to its properties.
 */
public final class EventEnvelope
        extends AbstractMessageEnvelope<EventId, Event, EventContext>
        implements
        SignalEnvelope<EventId, Event, EventContext>,
        EnvelopeWithOrigin<EventId, Event, EventContext> {

    private final EventClass eventClass;
    private final boolean rejection;

    private EventEnvelope(Event event) {
        super(event);
        this.eventClass = EventClass.of(event.enclosedMessage());
        this.rejection = event.isRejection();
    }

    /**
     * Creates instance for the passed event.
     */
    public static EventEnvelope of(Event event) {
        checkNotNull(event);
        return new EventEnvelope(event);
    }

    /**
     * Obtains the event ID.
     */
    @Override
    public EventId id() {
        var result = outerObject().getId();
        return result;
    }

    /**
     * Obtains the tenant ID of the event.
     */
    @Override
    public TenantId tenantId() {
        return actorContext().getTenantId();
    }

    /**
     * Obtains the event message.
     */
    @Override
    public EventMessage message() {
        return outerObject().enclosedMessage();
    }

    /**
     * Obtains the type URL of the event message.
     */
    public TypeUrl typeUrl() {
        return message().typeUrl();
    }

    /**
     * Obtains the class of the event.
     */
    @Override
    public EventClass messageClass() {
        return this.eventClass;
    }

    /**
     * Obtains the context of the event.
     */
    @Override
    public EventContext context() {
        return outerObject().context();
    }

    /**
     * Obtains the type of the event message.
     */
    public TypeName messageTypeName() {
        var result = TypeName.of(message());
        return result;
    }

    /**
     * {@inheritDoc}
     *
     * <p>If this envelope represents a {@linkplain Event#isRejection() rejection}, returns
     * the type of rejected command. Otherwise — an {@link EmptyClass}.
     */
    @Override
    public MessageClass<?> originClass() {
        if (isRejection()) {
            var rejection = context().getRejection();
            var commandMessage = rejection.getCommand()
                                          .enclosedMessage();
            return CommandClass.of(commandMessage);
        } else {
            return EmptyClass.instance();
        }
    }

    /**
     * Returns {@code true} if the wrapped event is a rejection, {@code false} otherwise.
     */
    public boolean isRejection() {
        return rejection;
    }

    /**
     * Returns {@code true} if the wrapped event is
     * {@link io.spine.system.server.event.EntityStateChanged EntityStateChanged},
     * {@code false} otherwise.
     */
    public boolean isEntityStateUpdate() {
        return eventClass.isEntityStateChanged();
    }

    /**
     * Returns {@code true} if the wrapped event is external, {@code false} otherwise.
     */
    public boolean isExternal() {
        var external = context().getExternal();
        return external;
    }
}
