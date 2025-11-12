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

import io.spine.server.Closeable;
import io.spine.server.transport.ChannelId;
import io.spine.type.TypeUrl;

import java.util.Collection;
import java.util.HashSet;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static io.spine.server.transport.MessageChannel.channelIdFor;

/**
 * Tells other Bounded Contexts about the {@code external} domain events requested for subscription
 * in this Bounded Context, and listens to similar messages from other Bounded Contexts.
 */
final class ConfigExchange extends SingleChannelExchange implements Closeable {

    private boolean closed = false;

    /**
     * ID of the channel used to exchange the {@code ExternalEventsWanted} messages.
     */
    private static final ChannelId CHANNEL = channelIdFor(TypeUrl.of(ExternalEventsWanted.class));

    /**
     * Routine that performs the broadcast from this Context to other Bounded Contexts.
     */
    private final BroadcastWantedEvents broadcast;

    /**
     * The observers which listen to {@code ExternalEventsWanted} sent by other contexts.
     */
    private final Collection<ObserveWantedEvents> observers = new HashSet<>();

    /**
     * Creates a new exchange with the passed link.
     */
    ConfigExchange(TransportLink link) {
        super(link);
        this.broadcast = new BroadcastWantedEvents(link.context(), publisher());
    }

    /**
     * Starts observing the {@link ExternalEventsWanted} messages sent by other Bounded Contexts
     * and, if applicable, creates the corresponding subscriptions in passed {@code bus}.
     *
     * <p>After such subscriptions are created, the matching events travelling through the bus
     * will be transmitted to other Bounded Contexts via this exchange.
     */
    void transmitRequestedEventsFrom(BusAdapter bus) {
        var observer = new ObserveWantedEvents(context(), bus);
        subscriber().addObserver(observer);
        observers.add(observer);
    }

    @Override
    ChannelId channel() {
        return CHANNEL;
    }

    /**
     * Sends out the collection of the domain events which this Bounded Context would like
     * to receive as {@code external}.
     */
    void requestWantedEvents() {
        broadcast.send();
    }

    /**
     * Notifies other Bounded Contexts that this Bounded Context now requests
     * a different set of event types.
     *
     * <p>Sends out an instance of {@link ExternalEventsWanted} for that purpose.
     */
    void notifyTypesChanged() {
        var eventTypes = subscriptionChannels()
                .stream()
                .map(ConfigExchange::typeOfTransmittedEvents)
                .collect(toImmutableSet());
        broadcast.onEventsChanged(eventTypes);
    }

    /**
     * Interprets the channel as the one transmitting external events, and extracts
     * the external event type from the ID value.
     */
    private static ExternalEventType typeOfTransmittedEvents(ChannelId channel) {
        return ExternalEventType.newBuilder()
                .setTypeUrl(channel.getTargetType())
                .build();
    }

    @Override
    public boolean isOpen() {
        return !closed;
    }

    /**
     * Closes all the observers previously created within this exchange, and notifies other
     * Bounded Contexts that no more {@code external} events are requested by the current
     * Bounded Context.
     */
    @Override
    public void close() {
        observers.forEach(ObserveWantedEvents::close);
        notifyTypesChanged();
    }
}
