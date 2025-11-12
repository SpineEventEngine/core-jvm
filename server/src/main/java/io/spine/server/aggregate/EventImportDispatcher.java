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

package io.spine.server.aggregate;

import com.google.common.collect.ImmutableSet;
import io.spine.annotation.Internal;
import io.spine.logging.WithLogging;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.event.EventDispatcher;
import io.spine.server.type.EventClass;
import io.spine.server.type.EventEnvelope;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Dispatches events to be imported to the associated aggregate repository.
 *
 * @param <I>
 *         the type of aggregate IDs
 * @apiNote This internal class is made {@code public} for restricting types of dispatchers
 *         that can be registered with an {@link io.spine.server.aggregate.ImportBus ImportBus}.
 *         Since only {@linkplain io.spine.server.aggregate.AggregateRepository Aggregate Repositories}
 *         can dispatch imported events to their aggregates, we limit the type of the import event
 *         dispatches to this class, which neither can be extended, nor created from outside of this
 *         package. Instances of this class are proxies that Aggregate Repositories create and
 *         {@linkplain io.spine.server.aggregate.ImportBus#register(io.spine.server.bus.MessageDispatcher)
 *         register} with an {@code ImportBus} of their parent Bounded Context.
 */
@Internal
public final class EventImportDispatcher<I> implements EventDispatcher, WithLogging {

    private final AggregateRepository<I, ?, ?> repository;

    private EventImportDispatcher(AggregateRepository<I, ?, ?> repository) {
        this.repository = repository;
    }

    static <I> EventImportDispatcher<I> of(AggregateRepository<I, ?, ?> repository) {
        checkNotNull(repository);
        return new EventImportDispatcher<>(repository);
    }

    @Override
    public ImmutableSet<EventClass> messageClasses() {
        return repository.importableEvents();
    }

    @Override
    public ImmutableSet<EventClass> domesticEventClasses() {
        return eventClasses();
    }

    /**
     * Always returns empty set because external events cannot be imported.
     */
    @Override
    public ImmutableSet<EventClass> externalEventClasses() {
        return EventClass.emptySet();
    }

    @Override
    public DispatchOutcome dispatch(EventEnvelope event) {
        return repository.importEvent(event);
    }
}
