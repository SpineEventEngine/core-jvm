/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
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
import io.spine.server.model.ModelClassValue;
import io.spine.server.projection.Projection;
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

    /**
     * Associates a {@code ProjectionClass} with a Java class of projections.
     *
     * @see #asProjectionClass(Class)
     */
    @SuppressWarnings("unchecked") // `asProjectionClass()` accepts only classes of projections.
    private static final ModelClassValue<ProjectionClass<?>> classValue =
            new ModelClassValue<>(cls -> new ProjectionClass<>((Class<Projection<?, ?, ?>>) cls));

    private final EventReceivingClassDelegate<P, SubscriberMethod> delegate;

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
        @SuppressWarnings("unchecked") // The class value creates the model class of `cls`.
        var result = (ProjectionClass<P>) classValue.get(cls);
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
