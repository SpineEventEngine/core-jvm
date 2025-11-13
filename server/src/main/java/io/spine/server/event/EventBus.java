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
package io.spine.server.event;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.concurrent.LazyInit;
import com.google.protobuf.Message;
import io.grpc.stub.StreamObserver;
import io.spine.annotation.Internal;
import io.spine.annotation.VisibleForTesting;
import io.spine.core.Ack;
import io.spine.core.Event;
import io.spine.core.EventContext;
import io.spine.core.Signal;
import io.spine.server.BoundedContext;
import io.spine.server.ContextAware;
import io.spine.server.ServerEnvironment;
import io.spine.server.bus.BusBuilder;
import io.spine.server.bus.DeadMessageHandler;
import io.spine.server.bus.DispatcherRegistry;
import io.spine.server.bus.EnvelopeValidator;
import io.spine.server.bus.MulticastBus;
import io.spine.server.enrich.Enricher;
import io.spine.server.type.EventClass;
import io.spine.server.type.EventEnvelope;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static io.spine.grpc.StreamObservers.noOpObserver;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Dispatches incoming events to subscribers and provides ways for registering those subscribers.
 *
 * <h2>Receiving Events</h2>
 *
 * <p>To receive event messages, a subscriber object should:
 * <ol>
 *     <li>Expose a {@code public} method that accepts an event message as the first parameter
 *         and an {@link EventContext EventContext} as the second (optional) parameter.
 *     <li>Mark the method with the {@link io.spine.core.Subscribe @Subscribe} annotation.
 *     <li>{@linkplain #register(io.spine.server.bus.MessageDispatcher)} Register} with an
 *         instance of {@code EventBus} directly or rely on message delivery
 *         from an {@link EventDispatcher}. An example of such a dispatcher is
 *         {@link io.spine.server.projection.ProjectionRepository ProjectionRepository}.
 * </ol>
 *
 * <p><strong>Note:</strong> A subscriber method cannot accept just {@link Message} as
 * the first parameter. It must be an <strong>exact type</strong> of the event message
 * that needs to be handled.
 *
 * <h2>Posting Events</h2>
 *
 * <p>Events are posted to an EventBus using {@link #post(Signal, StreamObserver)} method.
 * Normally this is done by an
 * {@link io.spine.server.aggregate.AggregateRepository AggregateRepository} in the process
 * of handling a command or by a {@link io.spine.server.procman.ProcessManager ProcessManager}.
 *
 * <p>The passed {@link Event} is stored in the {@link EventStore} associated with
 * the {@code EventBus} <strong>before</strong> it is passed to subscribers.
 *
 * <p>If there are no subscribers or dispatchers for the posted event, the fact is
 * logged as warning with no further processing.
 *
 * @see io.spine.server.projection.Projection Projection
 * @see io.spine.core.Subscribe @Subscribe
 */
@Internal
public final class EventBus
        extends MulticastBus<Event, EventEnvelope, EventClass, EventDispatcher>
        implements ContextAware {

    /**
     * The {@code EventStore} to store events before they get handled.
     *
     * @see #registerWith(BoundedContext)
     */
    private @MonotonicNonNull EventStore eventStore;

    /**
     * The handler for dead events.
     */
    private final DeadMessageHandler<EventEnvelope> deadMessageHandler;

    /**
     *  The observer of post operations.
     */
    private final StreamObserver<Ack> observer;

    /**
     * The validator for events posted to the bus lazily {@linkplain #validator() initialized}.
     */
    @LazyInit
    private @MonotonicNonNull EventValidator eventValidator;

    /**
     *  The enricher for posted events or {@code null} if the enrichment is not supported.
     */
    private final @Nullable EventEnricher enricher;

    /** Creates new instance by the passed builder. */
    private EventBus(Builder builder) {
        super(builder);
        this.enricher = builder.enricher;
        this.observer = requireNonNull(builder.observer);
        this.deadMessageHandler = new DeadEventTap(this::eventStore);
    }

    /** Creates a builder for new {@code EventBus}. */
    public static Builder newBuilder() {
        return new Builder();
    }

    @VisibleForTesting
    Set<? extends EventDispatcher> dispatchersOf(EventClass eventClass) {
        return registry().dispatchersOf(eventClass);
    }

    @VisibleForTesting
    boolean hasDispatchers(EventClass eventClass) {
        Set<?> dispatchers = dispatchersOf(eventClass);
        return !dispatchers.isEmpty();
    }

    /**
     * Obtains the view {@code Set} of events that are known to this {@code EventBus}.
     *
     * <p>This set is changed when event dispatchers or handlers are registered or un-registered.
     *
     * @return a set of classes of supported events
     */
    public Set<EventClass> registeredEventClasses() {
        return registry().registeredMessageClasses();
    }

    @Override
    protected DeadMessageHandler<EventEnvelope> deadMessageHandler() {
        return deadMessageHandler;
    }

    @Override
    protected EnvelopeValidator<EventEnvelope> validator() {
        if (eventValidator == null) {
            eventValidator = new EventValidator();
        }
        return eventValidator;
    }

    @Override
    protected EventEnvelope toEnvelope(Event message) {
        return EventEnvelope.of(message);
    }

    /** Returns {@link EventStore} associated with the bus. */
    public EventStore eventStore() {
        checkNotNull(
                eventStore,
                "`EventStore` is not initialized. " +
                        "Please call `EventBus.registerWith(BoundedContext)`."
        );
        return eventStore;
    }

    /**
     * Posts the event for handling.
     *
     * <p>Performs the same action as the
     * {@linkplain io.spine.server.bus.Bus#post(Signal, StreamObserver)} parent method},
     * but does not require any response observer.
     *
     * @param event the event to be handled
     * @see io.spine.server.bus.Bus#post(Signal, StreamObserver)
     */
    public void post(Event event) {
        post(event, observer());
    }

    /**
     * Posts the events for handling.
     *
     * <p>Performs the same action as the
     * {@linkplain io.spine.server.bus.Bus#post(Iterable, StreamObserver)} parent method}
     * but does not require any response observer.
     *
     * <p>This method should be used if the callee does not care about the events acknowledgement.
     *
     * @param events the events to be handled
     * @see io.spine.server.bus.Bus#post(Signal, StreamObserver)
     */
    public void post(Iterable<Event> events) {
        post(events, observer());
    }

    @VisibleForTesting
    StreamObserver<Ack> observer() {
        return observer;
    }

    /**
     * Checks whether the {@code EventEnricher} is used by this Event Bus.
     *
     * @return {@code true} if the enricher is set, {@code false} otherwise
     */
    @VisibleForTesting
    public boolean hasEnricher() {
        return enricher != null;
    }

    /**
     * Obtains the {@code EventEnricher} used by this Event Bus.
     *
     * @return the enricher
     * @throws NullPointerException if the enricher was not set
     */
    @VisibleForTesting
    public EventEnricher getEnricher() {
        return checkNotNull(enricher);
    }

    /**
     * Obtains the {@code EventEnricher} used by this Event Bus.
     *
     * @deprecated Use {@link #getEnricher()} and {@link #hasEnricher()} instead.
     */
    @Deprecated
    @VisibleForTesting
    public Optional<EventEnricher> enricher() {
        return Optional.ofNullable(enricher);
    }

    private EventEnvelope enrich(EventEnvelope event) {
        if (enricher == null) {
            return event;
        }
        var maybeEnriched = enricher.enrich(event);
        return maybeEnriched;
    }

    @Override
    protected void dispatch(EventEnvelope event) {
        var enrichedEnvelope = enrich(event);
        var dispatchersCalled = callDispatchers(enrichedEnvelope);
        checkState(dispatchersCalled != 0,
                   format("Message of type `%s` and ID `%s` has no dispatchers.",
                          event.messageClass(),
                          event.id().getValue()));
    }

    @Override
    protected void store(Iterable<Event> events) {
        eventStore().appendAll(events);
    }

    @Override
    public void close() {
        super.close();
        eventStore().close();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Overrides for return type covariance.
     */
    @Override
    protected EventDispatcherRegistry registry() {
        return (EventDispatcherRegistry) super.registry();
    }

    @Override
    @Internal
    public void registerWith(BoundedContext context) {
        eventStore =
                ServerEnvironment.instance()
                                 .storageFactory()
                                 .createEventStore(context.spec());
    }

    @Override
    public boolean isRegistered() {
        return eventStore != null;
    }

    /** The {@code Builder} for {@code EventBus}. */
    public static class Builder
            extends BusBuilder<Builder, Event, EventEnvelope, EventClass, EventDispatcher> {

        /**
         * Optional enricher for events.
         *
         * <p>If not set, the enrichments will NOT be supported
         * in the {@code EventBus} instance built.
         */
        private @Nullable EventEnricher enricher;

        /** The observer for {@link #post(Signal, StreamObserver)} operations. */
        private @Nullable StreamObserver<Ack> observer;

        /** Prevents direct instantiation. */
        private Builder() {
            super();
        }

        @Override
        protected DispatcherRegistry<EventClass, EventEnvelope, EventDispatcher> newRegistry() {
            return new EventDispatcherRegistry();
        }

        /**
         * Sets a custom {@link Enricher} for events posted to
         * the {@code EventBus} which is being built.
         *
         * <p>If the {@code Enricher} is not set, the enrichments
         * will <strong>NOT</strong> be supported for the {@code EventBus} instance built.
         *
         * @param enricher
         *         the {@code Enricher} for events or {@code null} if enrichment is not supported
         */
        public void injectEnricher(EventEnricher enricher) {
            this.enricher = checkNotNull(enricher);
        }

        /**
         * Checks whether the {@code EventEnricher} has been set.
         *
         * @return {@code true} if the enricher was set, {@code false} otherwise
         */
        public boolean hasEnricher() {
            return enricher != null;
        }

        /**
         * Obtains the {@code EventEnricher} assigned to the bus to be built.
         *
         * @return the enricher
         * @throws NullPointerException if the enricher was not set
         */
        public EventEnricher getEnricher() {
            return checkNotNull(enricher);
        }

        /**
         * Obtains {@code Enricher} assigned to the bus to be built.
         *
         * @deprecated Use {@link #getEnricher()} and {@link #hasEnricher()} instead.
         */
        @Deprecated
        public Optional<EventEnricher> enricher() {
            return Optional.ofNullable(enricher);
        }

        /**
         * Assigns the observer for the {@link #post(Signal, StreamObserver)} operations.
         */
        @CanIgnoreReturnValue
        public Builder setObserver(StreamObserver<Ack> observer) {
            this.observer = observer;
            return this;
        }

        /**
         * Checks whether the {@code StreamObserver} has been set.
         *
         * @return {@code true} if the observer was set, {@code false} otherwise
         */
        public boolean hasObserver() {
            return observer != null;
        }

        /**
         * Obtains the {@code StreamObserver} assigned to the bus.
         *
         * @return the observer
         * @throws NullPointerException if the observer was not set
         */
        public StreamObserver<Ack> getObserver() {
            return checkNotNull(observer);
        }

        /** 
         * Obtains {@code StreamObserver} assigned to the bus. 
         *
         * @deprecated Use {@link #getObserver()} and {@link #hasObserver()} instead.
         */
        @Deprecated
        public Optional<StreamObserver<Ack>> observer() {
            return Optional.ofNullable(observer);
        }

        /**
         * Builds an instance of {@link EventBus}.
         *
         * <p>This method is supposed to be called internally when building an enclosing
         * {@code BoundedContext}.
         */
        @Override
        @CheckReturnValue
        public EventBus build() {
            if (observer == null) {
                observer = noOpObserver();
            }
            var result = new EventBus(this);
            return result;
        }

        @Override
        protected Builder self() {
            return this;
        }
    }
}
