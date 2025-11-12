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

package io.spine.server.aggregate;

import com.google.common.collect.Streams;
import io.spine.core.Event;
import io.spine.core.TenantId;
import io.spine.server.bus.BusBuilder;
import io.spine.server.bus.DeadMessageHandler;
import io.spine.server.bus.DispatcherRegistry;
import io.spine.server.bus.EnvelopeValidator;
import io.spine.server.bus.MessageUnhandled;
import io.spine.server.bus.UnicastBus;
import io.spine.server.event.EventDispatcher;
import io.spine.server.tenant.TenantIndex;
import io.spine.server.type.EventClass;
import io.spine.server.type.EventEnvelope;

import java.util.Optional;

/**
 * Dispatches events to repositories of aggregates that
 * {@linkplain io.spine.server.aggregate.Apply#allowImport() import} these events.
 *
 * <h2>Usage Scenarios</h2>
 *
 * <p>Importing events may be used for registering facts occurred in a legacy or a third-party
 * system, which the Bounded Context translates into facts (events) of its history.
 *
 * <p>Another scenario is registering facts occurred <em>within</em> a Bounded Context
 * <em>without</em> having intermediate commands or events.
 *
 * <p>Adding an event to an aggregate history normally requires
 * either a command (handling of which produces the event) or an event (reaction on which may
 * produce the event). Such a command or an event:
 * <ol>
 *   <li>serves as a dispatched message type which is used as the first argument of the
 *       corresponding method in the aggregate;
 *   <li>carries the information about the fact we want to remember.
 * </ol>
 *
 * <p>{@linkplain Apply#allowImport() Marking} events and ensuring proper
 * {@linkplain AggregateRepository#setupImportRouting(io.spine.server.route.EventRouting) routing}
 * allows to store aggregate events without having intermediate messages.
 *
 * <h2>Temporal Logic</h2>
 *
 * <p>Importing events through dispatching
 * {@linkplain #post(io.spine.core.Signal, io.grpc.stub.StreamObserver) one} or
 * {@linkplain #post(Iterable, io.grpc.stub.StreamObserver) several} events is designed for
 * importing of events <em>as they occur</em>.
 *
 * <p>Importing events which occurred before the events already stored in the aggregate
 * history may result in hard to track bugs, and is not recommended.
 */
public final class ImportBus
        extends UnicastBus<Event, EventEnvelope, EventClass, EventImportDispatcher<?>> {

    private static final ImportValidator validator = new ImportValidator();
    private final DeadImportEventHandler deadImportEventHandler = new DeadImportEventHandler();
    private final TenantIndex tenantIndex;

    private ImportBus(Builder builder) {
        super(builder);
        this.tenantIndex = builder.ensureTenantIndex();
    }

    /**
     * Creates a builder for creating a new {@code ImportBus}.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    @Override
    protected DeadMessageHandler<EventEnvelope> deadMessageHandler() {
        return deadImportEventHandler;
    }

    @Override
    protected EnvelopeValidator<EventEnvelope> validator() {
        return validator;
    }

    @Override
    protected EventEnvelope toEnvelope(Event wrapper) {
        return EventEnvelope.of(wrapper);
    }

    @Override
    @SuppressWarnings("ResultOfMethodCallIgnored"
            /* Dispatching outcome is reported via system events, if needed. */)
    protected void dispatch(EventEnvelope event) {
        EventDispatcher dispatcher = dispatcherOf(event);
        dispatcher.dispatch(event);
    }

    @Override
    protected Registry registry() {
        return (Registry) super.registry();
    }

    /**
     * Updates the {@link io.spine.server.tenant.TenantIndex TenantIndex} with the ID
     * obtained from the passed events.
     */
    @Override
    protected void store(Iterable<Event> events) {
        var tenantId = tenantOf(events);
        tenantIndex.keep(tenantId);
    }

    private static TenantId tenantOf(Iterable<Event> events) {
        return Streams.stream(events)
                      .map((e) -> e.getContext()
                                   .getImportContext()
                                   .getTenantId())
                      .findAny()
                      .orElse(TenantId.getDefaultInstance());
    }

    /**
     * Creates {@link UnsupportedImportEventException} in response to an event message
     * of an unsupported type.
     */
    private static class DeadImportEventHandler implements DeadMessageHandler<EventEnvelope> {

        @Override
        public MessageUnhandled handle(EventEnvelope event) {
            return new UnsupportedImportEventException(event);
        }
    }

    /**
     * The registry of import dispatchers.
     */
    protected static final class Registry
            extends DispatcherRegistry<EventClass, EventEnvelope, EventImportDispatcher<?>> {

        @Override
        protected Optional<EventImportDispatcher<?>> dispatcherOf(EventEnvelope event) {
            return super.dispatcherOf(event);
        }
    }

    /**
     * The builder for {@link ImportBus}.
     */
    public static class Builder extends BusBuilder<Builder,
                                                   Event,
                                                   EventEnvelope,
                                                   EventClass,
                                                   EventImportDispatcher<?>> {

        /** Prevents direct instantiation. */
        private Builder() {
            super();
        }

        @Override
        protected Registry newRegistry() {
            return new Registry();
        }

        @Override
        public ImportBus build() {
            return new ImportBus(this);
        }

        @Override
        protected Builder self() {
            return this;
        }
    }
}
