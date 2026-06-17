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

import io.spine.core.BoundedContextName;
import io.spine.server.transport.ChannelId;
import io.spine.server.transport.Publisher;
import io.spine.server.transport.PublisherHub;
import io.spine.server.transport.Subscriber;
import io.spine.server.transport.SubscriberHub;

import java.util.Set;

/**
 * Connections to the transport layer made from a certain Bounded Context.
 */
final class TransportLink {

    private final BoundedContextName context;
    private final SubscriberHub subscriberHub;
    private final PublisherHub publisherHub;

    /**
     * Creates a new link with the name of the Bounded Context that establishes it.
     *
     * <p>Operates on top of the passed hubs of {@code Subscriber}s and {@code Publisher}s.
     */
    TransportLink(BoundedContextName context,
                  SubscriberHub subscriberHub,
                  PublisherHub publisherHub) {
        this.context = context;
        this.subscriberHub = subscriberHub;
        this.publisherHub = publisherHub;
    }

    /**
     * Obtains the publisher for the given channel ID.
     */
    Publisher publisher(ChannelId channel) {
        var result = publisherHub.get(channel);
        return result;
    }

    /**
     * Obtains the subscriber for the given channel ID.
     */
    Subscriber subscriber(ChannelId channel) {
        var result = subscriberHub.get(channel);
        return result;
    }

    /**
     * Returns the name of the Bounded Context that established this link.
     */
    BoundedContextName context() {
        return context;
    }

    /**
     * Returns all channels that currently exist in the subscriber hub used in this link.
     */
    Set<ChannelId> subscriptionChannels() {
        return subscriberHub.ids();
    }
}
