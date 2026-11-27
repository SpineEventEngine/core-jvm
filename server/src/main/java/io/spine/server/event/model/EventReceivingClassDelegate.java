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
import io.spine.server.entity.model.StateClass;
import io.spine.server.event.EventReceiver;
import io.spine.server.model.ModelClass;
import io.spine.server.model.Receptor;
import io.spine.server.model.ReceptorMap;
import io.spine.server.model.ReceptorSignature;
import io.spine.server.type.EventClass;
import io.spine.server.type.EventEnvelope;

import java.util.Optional;

/**
 * Helper object for storing information about methods and handlers of an
 * {@linkplain EventReceiverClass event receiving class}.
 *
 * @param <T>
 *         the type of target objects that handle messages
 * @param <R>
 *         the type of the receptors
 */
public class EventReceivingClassDelegate<T extends EventReceiver,
                                         R extends Receptor<?, EventClass, ?>>
        extends ModelClass<T> {

    private final ReceptorMap<EventClass, R> receptors;
    private final ImmutableSet<EventClass> events;
    private final ImmutableSet<EventClass> domesticEvents;
    private final ImmutableSet<EventClass> externalEvents;
    private final ImmutableSet<StateClass<?>> domesticStates;
    private final ImmutableSet<StateClass<?>> externalStates;

    /**
     * Creates a new instance for the passed raw class with methods obtained
     * through the passed factory.
     */
    public EventReceivingClassDelegate(Class<T> delegatingClass, ReceptorSignature<R, ?> signature) {
        super(delegatingClass);
        this.receptors = ReceptorMap.create(delegatingClass, signature);
        this.events = receptors.messageClasses();
        this.domesticEvents = receptors.messageClasses((r) -> !r.isExternal());
        this.externalEvents = receptors.messageClasses(Receptor::isExternal);
        this.domesticStates = extractStates(false);
        this.externalStates = extractStates(true);
    }

    public boolean contains(EventClass eventClass) {
        return receptors.containsClass(eventClass);
    }

    /**
     * Obtains all event classes handled by the delegating class.
     */
    public ImmutableSet<EventClass> events() {
        return events;
    }

    /**
     * Obtains domestic event classes handled by the delegating class.
     */
    public ImmutableSet<EventClass> domesticEvents() {
        return domesticEvents;
    }

    /**
     * Obtains external event classes handled by the delegating class.
     */
    public ImmutableSet<EventClass> externalEvents() {
        return externalEvents;
    }

    /**
     * Obtains domestic entity states to which the delegating class is subscribed.
     */
    public ImmutableSet<StateClass<?>> domesticStates() {
        return domesticStates;
    }

    /**
     * Obtains external entity states to which the delegating class is subscribed.
     */
    public ImmutableSet<StateClass<?>> externalStates() {
        return externalStates;
    }

    /**
     * Looks up the method that handles the passed event class.
     *
     * @param event
     *         the event which must be handled
     * @return event handler method or {@code Optional.empty()} if there is no such method
     */
    public Optional<R> findReceptorOf(EventEnvelope event) {
        return receptors.findReceptorFor(event);
    }

    /**
     * Obtains the classes of entity state messages from the passed handlers.
     */
    private ImmutableSet<StateClass<?>> extractStates(boolean external) {
        var updateEvent = StateClass.updateEvent();
        if (!receptors.containsClass(updateEvent)) {
            return ImmutableSet.of();
        }
        var stateHandlers = receptors.receptorsOf(updateEvent);
        var result = stateHandlers.stream()
                .filter(h -> h instanceof StateSubscriberMethod)
                .map(h -> (StateSubscriberMethod) h)
                .filter(external ? Receptor::isExternal : Receptor::isDomestic)
                .map(StateSubscriberMethod::stateClass)
                .collect(ImmutableSet.<StateClass<?>>toImmutableSet());
        return result;
    }
}
