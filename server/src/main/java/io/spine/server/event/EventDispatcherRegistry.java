/*
 * Copyright 2026, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
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

package io.spine.server.event;

import io.spine.server.bus.DispatcherRegistry;
import io.spine.server.type.EventClass;
import io.spine.server.type.EventEnvelope;

import java.util.Set;
import java.util.function.BiPredicate;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The registry of objects that dispatch event to handlers.
 *
 * <p>There can be multiple dispatchers per event class.
 */
final class EventDispatcherRegistry
        extends DispatcherRegistry<EventClass, EventEnvelope, EventDispatcher> {

    @Override
    public void register(EventDispatcher dispatcher) {
        checkNotNull(dispatcher);
        Set<EventClass> eventClasses = dispatcher.messageClasses();
        checkNotEmpty(dispatcher, eventClasses);

        super.register(dispatcher);
    }

    @Override
    public void unregister(EventDispatcher dispatcher) {
        checkNotNull(dispatcher);
        Set<EventClass> eventClasses = dispatcher.messageClasses();
        checkNotEmpty(dispatcher, eventClasses);

        super.unregister(dispatcher);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Overrides in order to expose itself to
     * {@linkplain EventBus#dispatchersOf(EventClass) EventBus}.
     */
    @Override
    protected Set<EventDispatcher> dispatchersOf(EventClass messageClass) {
        return super.dispatchersOf(messageClass);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Overrides to expose the method to {@link EventBus#registeredEventClasses() EventBus}.
     */
    @Override
    protected Set<EventClass> registeredMessageClasses() {
        return super.registeredMessageClasses();
    }

    /**
     * Tells whether the passed envelope can be dispatched to the passed dispatcher according
     * to the envelope attributes.
     *
     * <p>In case the packed event is external, it may be dispatched only to the dispatcher,
     * which declares the event class as external.
     *
     * <p>If the passed event is domestic, it may only be dispatched to the dispatcher, which
     * declares the corresponding event class as domestic.
     *
     * @return the predicate filtering out the pairs of {@linkplain EventEnvelope envelopes}
     *         and {@linkplain EventDispatcher dispatchers} in which the dispatching is not allowed
     */
    @Override
    protected final BiPredicate<EventEnvelope, EventDispatcher> attributeFilter() {
        return (envelope, dispatcher) -> {
            boolean external = envelope.context()
                                       .getExternal();
            EventClass eventClass = envelope.messageClass();
            return external
                   ? dispatcher.externalEventClasses().contains(eventClass)
                   : dispatcher.domesticEventClasses().contains(eventClass);
        };
    }

    /**
     * Ensures that the dispatcher forwards at least one event.
     *
     * @throws IllegalArgumentException if the dispatcher returns empty set of event classes
     * @throws NullPointerException     if the dispatcher returns null set
     */
    private void checkNotEmpty(EventDispatcher dispatcher, Set<EventClass> messageClasses) {
        checkArgument(!messageClasses.isEmpty(),
                      "%s: No message types are forwarded by this dispatcher: %s",
                      getClass().getName(),  dispatcher);
    }
}
