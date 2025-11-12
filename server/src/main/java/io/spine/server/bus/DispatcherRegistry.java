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

package io.spine.server.bus;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.protobuf.Message;
import io.spine.server.type.MessageEnvelope;
import io.spine.type.MessageClass;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.collect.Multimaps.synchronizedMultimap;

/**
 * A registry of message dispatchers.
 *
 * @param <C>
 *         the type of the class of dispatched messages
 * @param <E>
 *         the type of message envelopes
 * @param <D>
 *         the type of the message dispatchers
 */
public abstract class DispatcherRegistry<C extends MessageClass<? extends Message>,
                                         E extends MessageEnvelope<?, ?, ?>,
                                         D extends MessageDispatcher<C, E>> {
    /**
     * The map from a message class to one or more dispatchers of
     * messages of this class.
     *
     * <p>Some implementations like {@link io.spine.server.commandbus.CommandBus CommandBus}
     * will allow only one dispatcher per message class. This should be handled
     * when registering dispatchers.
     */
    private final Multimap<C, D> dispatchers = synchronizedMultimap(HashMultimap.create());

    public void register(D dispatcher) {
        checkDispatcher(dispatcher);
        Set<C> messageClasses = dispatcher.messageClasses();
        for (var messageClass : messageClasses) {
            dispatchers.put(messageClass, dispatcher);
        }
    }

    public void unregister(D dispatcher) {
        checkNotNull(dispatcher);
        checkNotEmpty(dispatcher);
        Set<C> messageClasses = dispatcher.messageClasses();
        for (var messageClass : messageClasses) {
            dispatchers.remove(messageClass, dispatcher);
        }
    }

    public void unregister(DispatcherDelegate<C, E> delegate) {
        checkNotNull(delegate);
        @SuppressWarnings("unchecked") // Checked with `instanceof`.
        var delegatingDispatcher = dispatchers.values().stream()
                .filter(d -> d instanceof DelegatingDispatcher)
                .map(d -> (DelegatingDispatcher<C, E>) d)
                .filter(d -> d.delegate().equals(delegate))
                .map(d -> (D) d)
                .findFirst();
        delegatingDispatcher.ifPresent(this::unregister);
    }

    /**
     * Unregisters all dispatchers.
     */
    protected void unregisterAll() {
        dispatchers.clear();
    }

    /**
     * Obtains message classes from all registered dispatchers.
     */
    protected Set<C> registeredMessageClasses() {
        return dispatchers.keySet();
    }

    /**
     * Obtains dispatchers for the passed message class.
     *
     * @param envelope
     *         the message envelope to find dispatchers for
     * @return a set of dispatchers or an empty set if no dispatchers are registered
     */
    final Set<D> dispatchersOf(E envelope) {
        checkNotNull(envelope);
        var messageClass = classOf(envelope);
        Set<D> dispatchers = this.dispatchers
                .get(messageClass)
                .stream()
                .filter(dispatcher -> attributeFilter().test(envelope, dispatcher))
                .filter(dispatcher -> dispatcher.canDispatch(envelope))
                .collect(toImmutableSet());
        return dispatchers;
    }

    /**
     * Returns a filter allowing to tell whether the attributes of the envelope match
     * the dispatcher requirements.
     */
    protected BiPredicate<E, D> attributeFilter() {
        return (e, d) -> true;
    }

    /**
     * Obtains a single dispatcher (if available) for the passed message.
     *
     * @param envelope
     *         the message to find a dispatcher for
     * @throws IllegalStateException
     *         if more than one dispatcher is found
     * @apiNote This method must be called only for serving {@link UnicastBus}es.
     */
    protected Optional<D> dispatcherOf(E envelope) {
        checkNotNull(envelope);
        var dispatchers = dispatchersOf(envelope);
        checkNotMoreThanOne(dispatchers, classOf(envelope));
        var result = dispatchers.stream().findFirst();
        return result;
    }

    /**
     * Obtains all the dispatchers for the passed message class.
     *
     * @param messageClass
     *         the target message class
     * @return all the registered dispatchers of the given class
     */
    protected Set<D> dispatchersOf(C messageClass) {
        checkNotNull(messageClass);
        var dispatchersForType = dispatchers.get(messageClass);
        return ImmutableSet.copyOf(dispatchersForType);
    }

    /**
     * Obtains a single dispatcher (if available) for the passed message.
     *
     * @param messageClass
     *         the class of the message to find a dispatcher for
     * @throws IllegalStateException
     *         if more than one dispatcher is found
     */
    protected Optional<? extends D> getDispatcherForType(C messageClass) {
        var dispatchersOfClass = dispatchers.get(messageClass);
        checkNotMoreThanOne(dispatchersOfClass, messageClass);
        var dispatcher = dispatchersOfClass.stream().findFirst();
        return dispatcher;
    }

    private void checkNotMoreThanOne(Collection<D> dispatchers, C messageClass) {
        var size = dispatchers.size();
        checkState(size <= 1,
                   "More than one (%s) dispatchers found for the message class `%s`.",
                   size, messageClass);
    }

    private C classOf(E envelope) {
        @SuppressWarnings("unchecked") // Logically valid.
        var messageClass = (C) envelope.messageClass();
        return messageClass;
    }

    /**
     * Ensures that the passed dispatcher is valid.
     *
     * <p>The passed dispatcher must {@linkplain MessageDispatcher#messageClasses() expose}
     * at least one message class.
     *
     * @param dispatcher
     *         the dispatcher to check
     * @throws IllegalArgumentException
     *         if the check is failed
     */
    protected void checkDispatcher(D dispatcher) throws IllegalArgumentException {
        checkNotNull(dispatcher);
        checkNotEmpty(dispatcher);
    }

    private static <D extends MessageDispatcher<?, ?>> void checkNotEmpty(D dispatcher) {
        Set<?> messageClasses = dispatcher.messageClasses();
        checkArgument(!messageClasses.isEmpty(),
                      "The dispatcher (%s) has empty message class set.",
                      dispatcher);
    }
}
