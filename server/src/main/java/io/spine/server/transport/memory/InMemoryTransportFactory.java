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
package io.spine.server.transport.memory;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import io.spine.server.transport.ChannelId;
import io.spine.server.transport.Publisher;
import io.spine.server.transport.Subscriber;
import io.spine.server.transport.TransportFactory;

import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Multimaps.synchronizedMultimap;

/**
 * In-memory implementation of the {@link TransportFactory}.
 *
 * <p>Publishers and subscribers must be in the same JVM.
 * Therefore, this factory usage should be limited to tests.
 */
public class InMemoryTransportFactory implements TransportFactory {

    /**
     * An in-memory storage of subscribers per message class.
     */
    private final Multimap<ChannelId, Subscriber> subscribers =
            synchronizedMultimap(HashMultimap.create());

    /** Turns {@code true} upon {@linkplain #close()} closing} the factory. */
    private boolean closed;

    /** Prevent direct instantiation from outside the inheritance tree. */
    protected InMemoryTransportFactory() {
    }

    /**
     * Creates a new instance of {@code InMemoryTransportFactory}.
     *
     * @return a new instance of this factory
     */
    public static InMemoryTransportFactory newInstance() {
        return new InMemoryTransportFactory();
    }

    @Override
    public final synchronized Publisher createPublisher(ChannelId id) {
        checkNotNull(id);
        var result = new InMemoryPublisher(id, providerOf(subscribers()));
        return result;
    }

    @Override
    public final synchronized Subscriber createSubscriber(ChannelId id) {
        checkNotNull(id);
        var subscriber = newSubscriber(id);
        subscribers().put(id, subscriber);
        return subscriber;
    }

    /**
     * Creates a new instance of subscriber.
     *
     * <p>The descendants may override this method to customize the implementation of subscribers
     * to use within this {@code TransportFactory} instance.
     *
     * @param id
     *         the identifier of the resulting subscriber
     * @return an instance of subscribe
     */
    protected Subscriber newSubscriber(ChannelId id) {
        checkNotNull(id);
        return new InMemorySubscriber(id);
    }

    /**
     * Wraps currently registered in-memory subscribers into a function that returns a subset
     * of subscribers per message type.
     *
     * @param subscribers
     *         currently registered subscribers
     * @return a provider function allowing to fetch subscribers by the message type
     */
    private static Function<ChannelId, Iterable<Subscriber>>
    providerOf(Multimap<ChannelId, Subscriber> subscribers) {
        return channelId -> {
            checkNotNull(channelId);
            return subscribers.get(channelId);
        };
    }

    @Override
    public synchronized boolean isOpen() {
        return !closed;
    }

    private synchronized Multimap<ChannelId, Subscriber> subscribers() {
        checkOpen();
        return subscribers;
    }

    @Override
    public synchronized void close() {
        closed = true;
        subscribers.clear();
    }
}
