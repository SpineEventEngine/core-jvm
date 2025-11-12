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
package io.spine.server.transport;

import io.spine.type.TypeUrl;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A channel dedicated to exchanging the messages.
 */
public interface MessageChannel extends AutoCloseable {

    /**
     * Obtains the channel identifier.
     */
    ChannelId id();

    /**
     * Allows understanding whether this channel is stale and can be closed.
     *
     * @return {@code true} if the channel is stale, {@code false} otherwise
     */
    boolean isStale();

    /**
     * Obtains the type of the messages transferred through this channel.
     */
    default TypeUrl targetType() {
        var channelId = id();
        return TypeUrl.parse(channelId.getTargetType());
    }

    /**
     * Converts the given message type into a {@link ChannelId}.
     *
     * @param messageType
     *         the type of messages transmitted through the channel
     * @return channel ID
     */
    static ChannelId channelIdFor(TypeUrl messageType) {
        checkNotNull(messageType);
        var channelId = ChannelId.newBuilder()
                .setTargetType(messageType.value())
                .build();
        return channelId;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Overrides to remove the checked exception from the signature.
     */
    @Override
    void close();
}
