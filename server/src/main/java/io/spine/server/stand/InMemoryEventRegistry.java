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

package io.spine.server.stand;

import com.google.common.collect.ImmutableSet;
import io.spine.server.EventProducer;
import io.spine.server.entity.EventProducingRepository;
import io.spine.server.entity.Repository;
import io.spine.server.type.EventClass;
import io.spine.type.TypeUrl;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * The in-memory concurrency-friendly implementation of the {@link EventRegistry}.
 */
final class InMemoryEventRegistry implements EventRegistry {

    private final ConcurrentMap<TypeUrl, EventClass> eventClasses = new ConcurrentHashMap<>();

    /** Prevents instantiation of this class from outside. */
    private InMemoryEventRegistry() {
    }

    public static InMemoryEventRegistry newInstance() {
        return new InMemoryEventRegistry();
    }

    @Override
    public void register(Repository<?, ?> repository) {
        if (repository instanceof EventProducingRepository) {
            var repo = (EventProducingRepository) repository;
            repo.outgoingEvents()
                .forEach(this::putIntoMap);
        }
    }

    @Override
    public void register(EventProducer producer) {
        producer.producedEvents()
                .forEach(this::putIntoMap);
    }

    @Override
    public ImmutableSet<TypeUrl> typeSet() {
        return ImmutableSet.copyOf(eventClasses.keySet());
    }

    @Override
    public boolean contains(TypeUrl type) {
        return eventClasses.containsKey(type);
    }

    @Override
    public ImmutableSet<EventClass> eventClasses() {
        return ImmutableSet.copyOf(eventClasses.values());
    }

    @Override
    public boolean isOpen() {
        return true;
    }

    @Override
    public void close() {
        eventClasses.clear();
    }

    private void putIntoMap(EventClass eventClass) {
        var typeUrl = eventClass.typeUrl();
        eventClasses.put(typeUrl, eventClass);
    }
}
