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

package io.spine.server.event.model;

import com.google.common.collect.ImmutableSet;
import io.spine.server.event.EventSubscriber;
import io.spine.server.model.ModelClass;
import io.spine.server.model.ModelClassValue;
import io.spine.server.type.EventClass;
import io.spine.server.type.EventEnvelope;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Provides type information on an {@link EventSubscriber} class.
 *
 * @param <S>
 *         the type of event subscribers
 */
public final class EventSubscriberClass<S extends EventSubscriber> extends ModelClass<S>
    implements EventReceiverClass, SubscribingClass {

    /**
     * Associates an {@code EventSubscriberClass} with a Java class of event subscribers.
     *
     * @see #asEventSubscriberClass(Class)
     */
    @SuppressWarnings("unchecked") // `asEventSubscriberClass()` accepts only subscriber classes.
    private static final ModelClassValue<EventSubscriberClass<?>> classValue =
            new ModelClassValue<>(cls -> new EventSubscriberClass<>((Class<EventSubscriber>) cls));

    private final EventReceivingClassDelegate<S, SubscriberMethod> delegate;

    private EventSubscriberClass(Class<S> cls) {
        super(cls);
        this.delegate = new EventReceivingClassDelegate<>(cls, new SubscriberSignature());
    }

    /**
     * Obtains an event subscriber class for the passed raw class.
     */
    public static <S extends EventSubscriber>
    EventSubscriberClass<S> asEventSubscriberClass(Class<S> cls) {
        checkNotNull(cls);
        @SuppressWarnings("unchecked") // The class value creates the model class of `cls`.
        var result = (EventSubscriberClass<S>) classValue.get(cls);
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
    public Optional<SubscriberMethod> subscriberOf(EventEnvelope event) {
        return delegate.findReceptorOf(event);
    }
}
