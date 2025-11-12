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
import io.spine.type.TypeUrl;

import java.util.function.Consumer;

import static io.spine.protobuf.AnyPacker.unpack;
import static io.spine.server.transport.MessageChannel.channelIdFor;

/**
 * Sends and receives the {@link BoundedContextOnline} statuses.
 */
final class StatusExchange extends SingleChannelExchange {

    private static final Class<BoundedContextOnline> TYPE = BoundedContextOnline.class;

    /**
     * The ID of the channel used to exchange {@code BoundedContextOnline} messages.
     */
    private static final ChannelId CHANNEL = channelIdFor(TypeUrl.of(TYPE));

    /**
     * Creates a new exchange over the passed transport link.
     */
    StatusExchange(TransportLink link) {
        super(link);
    }

    @Override
    ChannelId channel() {
        return CHANNEL;
    }

    /**
     * Tells other Bounded Contexts that this context is now connected to the transport
     * and available for communication.
     */
    void declareOnlineStatus() {
        var notification = BoundedContextOnline.newBuilder()
                .setContext(context())
                .build();
        var message = ExternalMessages.of(notification);
        publisher().publish(message.getId(), message);
    }

    /**
     * Reacts upon {@link BoundedContextOnline} message by calling the passed {@code callback}.
     */
    void onBoundedContextOnline(Consumer<BoundedContextOnline> callback) {
        subscriber().addObserver(new Observer(context(), callback));
    }

    /**
     * Triggers the specified callback upon each received {@code BoundedContextOnline} message.
     */
    private static final class Observer extends AbstractChannelObserver {

        private final Consumer<BoundedContextOnline> callback;

        private Observer(BoundedContextName context, Consumer<BoundedContextOnline> callback) {
            super(context, TYPE);
            this.callback = callback;
        }

        @Override
        protected void handle(ExternalMessage message) {
            var msg = asOriginal(message);
            callback.accept(msg);
        }

        private static BoundedContextOnline asOriginal(ExternalMessage message) {
            var msg = unpack(message.getOriginalMessage(), TYPE);
            return msg;
        }
    }
}
