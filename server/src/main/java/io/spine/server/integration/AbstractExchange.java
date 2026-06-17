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
import io.spine.server.transport.Subscriber;

import java.util.Set;

/**
 * Two-way communication bridge established from some Bounded Context to the transport.
 */
abstract class AbstractExchange {

    private final TransportLink link;

    /**
     * Creates a new exchange that uses the passed link.
     */
    AbstractExchange(TransportLink link) {
        this.link = link;
    }

    /**
     * Returns the name of the Bounded Context that uses this exchange.
     */
    protected final BoundedContextName context() {
        return link.context();
    }

    /**
     * Returns the IDs of all channels used for subscribing through the underlying link.
     */
    final Set<ChannelId> subscriptionChannels() {
        return link.subscriptionChannels();
    }

    /**
     * Returns the subscriber for the passed ID of the channel.
     */
    final Subscriber subscriber(ChannelId channel) {
        var result = link.subscriber(channel);
        return result;
    }

    /**
     * Returns the publisher for the passed ID of the channel.
     */
    final Publisher publisher(ChannelId channel) {
        var result = link.publisher(channel);
        return result;
    }
}
