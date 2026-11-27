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
import io.spine.server.event.EventReactor;
import io.spine.server.model.ModelClass;
import io.spine.server.model.ModelClassValue;
import io.spine.server.model.Receptor;
import io.spine.server.model.ReceptorMap;
import io.spine.server.type.EventClass;
import io.spine.server.type.EventEnvelope;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Provides information on an {@link EventReactor} class.
 *
 * @param <S>
 *         the type of event reactors
 */
public final class EventReactorClass<S extends EventReactor> extends ModelClass<S>
        implements ReactingClass {

    /**
     * Associates an {@code EventReactorClass} with a Java class of event reactors.
     *
     * @see #asReactorClass(Class)
     */
    @SuppressWarnings("unchecked") // `asReactorClass()` accepts only classes of event reactors.
    private static final ModelClassValue<EventReactorClass<?>> classValue =
            new ModelClassValue<>(cls -> new EventReactorClass<>((Class<EventReactor>) cls));

    private final ReceptorMap<EventClass, EventClass, EventReactorMethod> reactors;
    private final ImmutableSet<EventClass> events;
    private final ImmutableSet<EventClass> domesticEvents;
    private final ImmutableSet<EventClass> externalEvents;

    private EventReactorClass(Class<? extends S> cls) {
        super(cls);
        this.reactors = ReceptorMap.create(cls, new EventReactorSignature());
        this.events = reactors.messageClasses();
        this.domesticEvents = reactors.messageClasses((h) -> !h.isExternal());
        this.externalEvents = reactors.messageClasses(Receptor::isExternal);
    }

    /** Obtains an event reactor class for the given raw class. */
    public static <S extends EventReactor> EventReactorClass<S>
    asReactorClass(Class<S> cls) {
        checkNotNull(cls);
        @SuppressWarnings("unchecked") // The class value creates the model class of `cls`.
        var result = (EventReactorClass<S>) classValue.get(cls);
        return result;
    }

    @Override
    public Optional<EventReactorMethod> reactorOf(EventEnvelope event) {
        return reactors.findReceptorFor(event);
    }

    @Override
    public ImmutableSet<EventClass> events() {
        return events;
    }

    @Override
    public ImmutableSet<EventClass> externalEvents() {
        return externalEvents;
    }

    @Override
    public ImmutableSet<EventClass> domesticEvents() {
        return domesticEvents;
    }
}
