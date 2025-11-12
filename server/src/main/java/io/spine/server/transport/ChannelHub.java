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

import io.spine.annotation.SPI;
import io.spine.server.Closeable;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.google.common.collect.Sets.newHashSet;
import static io.spine.util.Exceptions.illegalStateWithCauseOf;
import static java.util.Collections.unmodifiableSet;

/**
 * The hub of channels grouped in some logical way.
 *
 * <p>Serves for channel creation and storage-per-ID which in a way makes the hub similar to
 * an entity repository.
 *
 * @param <C>
 *         the type of grouped channels
 */
@SPI
public abstract class ChannelHub<C extends MessageChannel> implements Closeable {

    private final TransportFactory transportFactory;
    private final Map<ChannelId, C> channels = new ConcurrentHashMap<>();

    protected ChannelHub(TransportFactory transportFactory) {
        this.transportFactory = transportFactory;
    }

    /**
     * Returns the identifiers of the channels for this hub.
     */
    public Set<ChannelId> ids() {
        return unmodifiableSet(channels.keySet());
    }

    /**
     * Creates a new channel under the specified ID.
     *
     * @param id
     *         the ID of the channel to create
     * @return the created channel.
     */
    protected abstract C newChannel(ChannelId id);

    /**
     * Obtains a channel from this hub by the given channel ID.
     *
     * <p>If there is no channel with this ID in this hub, creates it and adds to the hub
     * prior to returning it as a result of this method call.
     *
     * @param channelId
     *         the identifier of the resulting channel
     * @return a channel with the key
     */
    public C get(ChannelId channelId) {
        var channel = channels.computeIfAbsent(channelId, this::newChannel);
        return channel;
    }

    /**
     * Closes the stale channels and removes those from the hub.
     */
    public void closeStaleChannels() {
        var staleChannels = detectStale();
        for (var id : staleChannels) {
            channels.remove(id);
        }
    }

    private Set<ChannelId> detectStale() {
        Set<ChannelId> toRemove = newHashSet();
        for (var channelId : channels.keySet()) {
            var channel = channels.get(channelId);
            if (channel.isStale()) {
                try {
                    channel.close();
                } catch (RuntimeException e) {
                    throw illegalStateWithCauseOf(e);
                } finally {
                    toRemove.add(channelId);
                }
            }
        }
        return toRemove;
    }

    @Override
    public boolean isOpen() {
        return !channels.isEmpty();
    }

    @Override
    public void close() {
        for (var channel : channels.values()) {
            channel.close();
        }
        channels.clear();
    }

    TransportFactory transportFactory() {
        return transportFactory;
    }
}
