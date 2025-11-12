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

package io.spine.server.event;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableSet;
import io.spine.annotation.Internal;
import io.spine.server.bus.DelegatingDispatcher;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.type.EventClass;
import io.spine.server.type.EventEnvelope;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A {@link EventDispatcher} which delegates the responsibilities to an aggregated {@link
 * EventDispatcherDelegate delegate instance}.
 *
 * @see EventDispatcherDelegate
 */
@Internal
public final class DelegatingEventDispatcher
        implements EventDispatcher,
                   DelegatingDispatcher<EventClass, EventEnvelope> {

    /**
     * A target delegate.
     */
    private final EventDispatcherDelegate delegate;

    /**
     * Creates a new instance of {@code DelegatingCommandDispatcher}, proxying the calls
     * to the passed {@code delegate}.
     *
     * @param delegate a delegate to pass the dispatching duties to
     * @return new instance
     */
    public static DelegatingEventDispatcher of(EventDispatcherDelegate delegate) {
        checkNotNull(delegate);
        return new DelegatingEventDispatcher(delegate);
    }

    private DelegatingEventDispatcher(EventDispatcherDelegate delegate) {
        this.delegate = delegate;
    }

    @Override
    public ImmutableSet<EventClass> messageClasses() {
        return delegate.events();
    }

    @Override
    public ImmutableSet<EventClass> domesticEventClasses() {
        return delegate.domesticEvents();
    }

    @Override
    public ImmutableSet<EventClass> externalEventClasses() {
        return delegate.externalEvents();
    }

    @Override
    public DispatchOutcome dispatch(EventEnvelope event) {
        return delegate.dispatchEvent(event);
    }

    @Override
    public boolean canDispatch(EventEnvelope envelope) {
        return delegate.canDispatchEvent(envelope);
    }

    @Override
    public EventDispatcherDelegate delegate() {
        return delegate;
    }

    /**
     * Returns the string representation of this dispatcher.
     *
     * <p>Includes an FQN of the {@code delegate} in order to allow distinguishing
     * {@code DelegatingEventDispatcher} instances with different delegates.
     */
    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                          .add("eventDelegate", delegate.getClass())
                          .toString();
    }
}
