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

package io.spine.server.type;

import io.spine.base.EventMessage;
import io.spine.core.Enrichment;
import io.spine.core.Event;
import io.spine.core.EventContext;
import io.spine.core.EventId;
import io.spine.core.TenantId;
import io.spine.server.enrich.EnrichmentService;
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
        EnvelopeWithOrigin<EventId, Event, EventContext>,
        EnrichableMessageEnvelope<EventId, Event, EventMessage, EventContext, EventEnvelope> {

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
     * Obtains tenant ID of the event.
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
     * Returns {@code true} is the wrapped event is external, {@code false} otherwise.
     */
    public boolean isExternal() {
        var external = context().getExternal();
        return external;
    }

    @Override
    public EventEnvelope toEnriched(EnrichmentService<EventMessage, EventContext> service) {
        if (!isEnrichmentEnabled()) {
            return this;
        }
        var enrichment = service.createEnrichment(message(), this.context());
        var result = enrichment.map(this::withEnrichment)
                               .orElse(this);
        return result;
    }

    /**
     * Verifies if the enrichment of the message is enabled.
     */
    public final boolean isEnrichmentEnabled() {
        var result = enrichment().getModeCase() != Enrichment.ModeCase.DO_NOT_ENRICH;
        return result;
    }

    private Enrichment enrichment() {
        return context().getEnrichment();
    }

    private EventEnvelope withEnrichment(Enrichment enrichment) {
        var context = this.context()
                .toBuilder()
                .setEnrichment(enrichment)
                .build();
        var enrichedCopy = outerObject().toBuilder()
                .setContext(context)
                .build();
        return of(enrichedCopy);
    }
}
