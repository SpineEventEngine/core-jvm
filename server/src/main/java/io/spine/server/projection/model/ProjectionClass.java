/*
 * Copyright 2025, TeamDev. All rights reserved.
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

package io.spine.server.projection.model;

import com.google.common.collect.ImmutableSet;
import io.spine.server.entity.model.EntityClass;
import io.spine.server.entity.model.StateClass;
import io.spine.server.entity.model.StateSubscribingClass;
import io.spine.server.event.model.EventReceiverClass;
import io.spine.server.event.model.EventReceivingClassDelegate;
import io.spine.server.event.model.SubscriberMethod;
import io.spine.server.event.model.SubscriberSignature;
import io.spine.server.event.model.SubscribingClass;
import io.spine.server.projection.Projection;
import io.spine.server.type.EmptyClass;
import io.spine.server.type.EventClass;
import io.spine.server.type.EventEnvelope;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Provides type information on a projection class.
 *
 * @param <P>
 *         the type of projections
 */
public final class ProjectionClass<P extends Projection<?, ?, ?>>
        extends EntityClass<P>
        implements EventReceiverClass, SubscribingClass, StateSubscribingClass {

    private final EventReceivingClassDelegate<P, EmptyClass, SubscriberMethod> delegate;

    private ProjectionClass(Class<P> cls) {
        super(cls);
        this.delegate = new EventReceivingClassDelegate<>(cls, new SubscriberSignature());
    }

    /**
     * Obtains a model class for the passed raw class.
     */
    public static <P extends Projection<?, ?, ?>> ProjectionClass<P>
    asProjectionClass(Class<P> cls) {
        checkNotNull(cls);
        @SuppressWarnings("unchecked")
        var result = (ProjectionClass<P>)
                get(cls, ProjectionClass.class, () -> new ProjectionClass<>(cls));
        return result;
    }

    @Override
    public ImmutableSet<EventClass> events() {
        return delegate.events();
    }

    @Override
    public ImmutableSet<EventClass> domesticEvents() {
        return delegate.domesticEvents();
    }

    @Override
    public ImmutableSet<EventClass> externalEvents() {
        return delegate.externalEvents();
    }

    @Override
    public ImmutableSet<StateClass<?>> domesticStates() {
        return delegate.domesticStates();
    }

    @Override
    public ImmutableSet<StateClass<?>> externalStates() {
        return delegate.externalStates();
    }

    @Override
    public Optional<SubscriberMethod> subscriberOf(EventEnvelope event) {
        return delegate.findReceptorOf(event);
    }
}
