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

import io.spine.annotation.Internal;
import io.spine.core.BoundedContextName;
import io.spine.server.BoundedContext;
import io.spine.server.Closeable;
import io.spine.server.ContextAware;
import io.spine.server.ServerEnvironment;
import io.spine.server.event.EventDispatcher;
import io.spine.server.transport.PublisherHub;
import io.spine.server.transport.SubscriberHub;
import io.spine.server.transport.TransportFactory;
import io.spine.server.type.EventEnvelope;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Dispatches {@linkplain ExternalMessage external messages} from and to a Bounded Context with
 * which this broker is associated.
 *
 * <p>In a multi-component environment messages may travel across components from one
 * Bounded Context to another. Accordingly, an {@code IntegrationBroker} is available as a part
 * of a Bounded Context.
 *
 * <p>An {@code IntegrationBroker} is always based upon {@linkplain TransportFactory transport}
 * that delivers messages from and to it. For several Bounded Contexts to communicate, their brokers
 * have to share the transport. Typically, that would be a single message queue.
 *
 * <p>{@code IntegrationBroker}s communicate with each other in order to keep the whole list of
 * wanted external events and their potential sources (Bounded Contexts) up-to-date.
 * They use two special messages for this:
 * <ul>
 *     <li>{@link BoundedContextOnline} is sent when a broker (and through it, its Bounded Context)
 *     is connected to the transport
 *     <li>{@link ExternalEventsWanted} is sent
 *     <ul>
 *         <li>in response to {@link BoundedContextOnline} sent by other brokers;
 *         <li>when the collection of external events is updated inside of a Bounded Context.
 *     </ul>
 * </ul>
 *
 * <p><b>Receiving</b>
 *
 * <p>The messages from external components received by an {@code IntegrationBroker} via
 * the transport are propagated into the Bounded Context via the domestic {@code EventBus}.
 *
 * <p><b>Publishing</b>
 *
 * <p>The messages requested by other parties are published from the domestic
 * {@code EventBus} with the help of {@linkplain DomesticEventPublisher special dispatcher}.
 *
 * <p><b>Sample Usage</b>
 *
 * <p>Bounded Context "Projects" has a projection with an event receptor that is subscribed to an
 * external event as follows:
 * <pre>
 * {@code
 * public class ProjectListView extends Projection<...>  {
 *
 *     {@literal @}Subscribe
 *      public void on(@External UserDeleted event) {
 *          // Remove the projects that belong to this user.
 *          // ...
 *      }
 *  }
 *  }
 * </pre>
 *
 * <p>Upon a registration of the corresponding repository for this projection in the context,
 * the broker associated with that context is informed that one more external event is needed.
 * It sends out an updated {@code ExternalEventsWanted} saying that {@code UserDeleted}
 * events are needed too.
 *
 * <p>Let's say the second Context is "Users". Its broker will receive
 * the {@code ExternalEventsWanted} sent by "Projects". To handle it, it will create a bridge
 * between "Users"'s Event Bus (which may eventually be transmitting a {@code UserDeleted} event)
 * and the {@linkplain ServerEnvironment#transportFactory() transport}.
 *
 * <p>Once {@code UserDeleted} is emitted locally in "Users" context, it will be received
 * by this bridge (as well as other local dispatchers) and published to the external transport by
 * its {@code IntegrationBroker}.
 *
 * <p>The integration broker on the "Projects" side will receive the {@code UserDeleted}
 * external message. The event will be dispatched to the external event receptor of the projection.
 *
 * <p><b>Limitations</b>
 *
 * <p>An event type may be consumed by many Contexts but produced only by a single Context. This
 * means that each given event type belongs only to one Context. The ownership of an event type may
 * be changed between the versions of a system, but may not be changed while events are travelling
 * between the Contexts.
 */
@Internal
public final class IntegrationBroker implements ContextAware, Closeable {

    private final SubscriberHub subscriberHub;
    private final PublisherHub publisherHub;

    private @MonotonicNonNull BoundedContextName contextName;

    private @MonotonicNonNull ConfigExchange config;
    private @MonotonicNonNull EventsExchange events;

    public IntegrationBroker() {
        var transportFactory = ServerEnvironment
                .instance()
                .transportFactory();
        this.subscriberHub = new SubscriberHub(transportFactory);
        this.publisherHub = new PublisherHub(transportFactory);
    }

    @Override
    public void registerWith(BoundedContext context) {
        checkNotRegistered();
        this.contextName = context.name();

        var bus = new BusAdapter(this, context.eventBus());
        var link = new TransportLink(contextName, subscriberHub, publisherHub);
        config = new ConfigExchange(link);
        events = new EventsExchange(link, bus);
        config.transmitRequestedEventsFrom(bus);
        runStatusExchange(link);
    }

    private void runStatusExchange(TransportLink link) {
        var statuses = new StatusExchange(link);
        statuses.onBoundedContextOnline((msg) -> config.requestWantedEvents());
        statuses.declareOnlineStatus();
    }

    @Override
    public boolean isRegistered() {
        return contextName != null;
    }

    /**
     * Publishes the given event for other Bounded Contexts.
     *
     * @param event
     *         event to publish
     */
    @Internal
    public void publish(EventEnvelope event) {
        events.publish(event);
    }

    /**
     * Registers a local dispatcher which is subscribed to {@code external} messages.
     *
     * @param dispatcher
     *         the dispatcher to register
     */
    public void register(EventDispatcher dispatcher) {
        events.register(dispatcher);
        config.notifyTypesChanged();
    }

    /**
     * Unregisters a local dispatcher which should no longer be subscribed
     * to {@code external} messages.
     *
     * @param dispatcher
     *         the dispatcher to unregister
     */
    public void unregister(EventDispatcher dispatcher) {
        events.unregister(dispatcher);
        subscriberHub.closeStaleChannels();
    }

    /**
     * Removes all subscriptions and closes all the underlying transport channels.
     */
    @Override
    public void close() {
        if (config != null) {
            config.close();
        }
        subscriberHub.close();
        publisherHub.close();
    }

    @Override
    public boolean isOpen() {
        return subscriberHub.isOpen();
    }

    @Override
    public String toString() {
        return "`IntegrationBroker` of <" + contextName.getValue() + '>';
    }
}
