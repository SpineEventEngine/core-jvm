/*
 * Copyright 2024, TeamDev. All rights reserved.
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

package io.spine.server.bus;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.OverridingMethodsMustInvokeSuper;
import com.google.protobuf.Message;
import io.grpc.stub.StreamObserver;
import io.spine.annotation.Internal;
import io.spine.annotation.VisibleForTesting;
import io.spine.base.Mistake;
import io.spine.core.Ack;
import io.spine.core.Signal;
import io.spine.core.SignalId;
import io.spine.logging.WithLogging;
import io.spine.server.Closeable;
import io.spine.server.type.MessageEnvelope;
import io.spine.server.type.SignalEnvelope;
import io.spine.type.MessageClass;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Suppliers.memoize;
import static io.spine.server.bus.MessageIdExtensions.acknowledge;
import static io.spine.util.Preconditions2.checkNotDefaultArg;
import static java.lang.String.format;
import static java.util.Collections.singleton;

/**
 * Abstract base for message buses.
 *
 * @param <T> the type of outer objects (containing messages of interest) that are posted to the bus
 * @param <E> the type of envelopes for outer objects used by this bus
 * @param <C> the type of message class
 * @param <D> the type of dispatches used by this bus
 */
@Internal
@SuppressWarnings("ClassWithTooManyMethods") // OK for the abstract bus.
public abstract class Bus<T extends Signal<?, ?, ?>,
                          E extends SignalEnvelope<?, T, ?>,
                          C extends MessageClass<? extends Message>,
                          D extends MessageDispatcher<C, E>>
        implements Closeable, WithLogging {

    /** Dispatchers of messages by their class. */
    private final DispatcherRegistry<C, E, D> registry;

    /** Listeners of the messages posted to the bus. */
    private final Listeners<E> listeners;

    /**
     * A supplier of the filter chain, which can be configured by the derived classes.
     *
     * @see #setupFilters(Iterable)
     */
    private final Supplier<FilterChain<E>> filterChain;

    protected Bus(BusBuilder<?, T, E, C, D> builder) {
        super();
        this.listeners = new Listeners<>(builder);
        this.filterChain = memoize(() -> this.createFilterChain(builder.filters()));
        this.registry = builder.newRegistry();
    }

    /**
     * Registers the passed dispatcher.
     *
     * @param dispatcher the dispatcher to register
     * @throws IllegalArgumentException
     *         if the set of message classes {@linkplain MessageDispatcher#messageClasses()
     *         exposed} by the dispatcher is empty
     */
    public void register(D dispatcher) {
        checkNotNull(dispatcher);
        registry.register(dispatcher);
    }

    /**
     * Stops dispatching messages using the given dispatcher.
     *
     * @param dispatcher the dispatcher to unregister
     */
    public void unregister(D dispatcher) {
        checkNotNull(dispatcher);
        registry.unregister(dispatcher);
    }

    /**
     * Stops dispatching messages the given dispatcher delegate.
     */
    public void unregister(DispatcherDelegate<C, E> delegate) {
        checkNotNull(delegate);
        registry.unregister(delegate);
    }

    /**
     * Adds the given listener to the bus.
     */
    public void add(Listener<E> listener) {
        checkNotNull(listener);
        listeners.add(listener);
    }

    /**
     * Removes the given listener from the bus.
     */
    public void remove(Listener<E> listener) {
        checkNotNull(listener);
        listeners.remove(listener);
    }

    /**
     * Posts the message to the bus.
     *
     * @param message  the message to post
     * @param observer the observer to receive an outcome of the operation
     * @see #post(Iterable, StreamObserver) for posing multiple messages at once
     */
    public final void post(T message, StreamObserver<Ack> observer) {
        checkNotDefaultArg(message);
        checkNotNull(observer);
        post(singleton(message), observer);
    }

    /**
     * Posts the given messages to the bus.
     *
     * <p>The {@linkplain StreamObserver observer} serves to notify the consumer about the result
     * of the call. The {@link StreamObserver#onNext StreamObserver.onNext()} is called for each
     * message posted to the bus.
     *
     * <p>If the message is accepted by the bus, {@link Ack} with the
     * {@link io.spine.core.Status.StatusCase#OK OK} status is passed to the observer.
     *
     * <p>If the message cannot be sent due to some issues, a corresponding
     * {@link io.spine.base.Error Error} status is passed in {@code Ack} instance.
     *
     * <p>Depending on the underlying {@link MessageDispatcher}, a message which causes a business
     * {@linkplain io.spine.base.RejectionThrowable rejection} may result either a rejection
     * status or an {@link io.spine.core.Status.StatusCase#OK OK} status {@link Ack} instance.
     * The rejection status may only pop up if the {@link MessageDispatcher} processes the message
     * sequentially and throws the rejection (wrapped in a
     * the {@linkplain io.spine.base.RejectionThrowable RejectionThrowables}) instead of
     * handling them. Otherwise, the {@code OK} status should be expected.
     *
     * <p>Note that {@linkplain StreamObserver#onError StreamObserver.onError()} is never called
     * for the passed observer, since errors are propagated as statuses of {@code Ack} response.
     *
     * @param messages the messages to post
     * @param observer the observer to receive the outcome of the operation
     */
    public final void post(Iterable<T> messages, StreamObserver<Ack> observer) {
        checkNotNull(messages);
        checkNotNull(observer);
        messages.forEach(message -> listeners.accept(toEnvelope(message)));
        var wrappedObserver = prepareObserver(messages, observer);
        filterAndPost(messages, wrappedObserver);
    }

    private void filterAndPost(Iterable<T> messages, StreamObserver<Ack> observer) {
        var filteredMessages = filter(messages, observer);
        if (!filteredMessages.isEmpty()) {
            store(filteredMessages.keySet());
            Iterable<E> envelopes = filteredMessages.values();
            doPost(envelopes, observer);
        }
        observer.onCompleted();
    }

    /**
     * Prepares the given {@link StreamObserver} to post messages into this bus.
     *
     * <p>This method is an extension point of a {@code Bus}.
     *
     * <p>When {@linkplain #post(Iterable, StreamObserver) posting} messages into the bus,
     * the message {@linkplain Ack acknowledgements} are passed to the observer created by this
     * method.
     *
     * <p>Conventionally, the resulting {@link StreamObserver} should delegate calls to
     * the {@code source} observer, so that the caller receives the posting outcome. If violating
     * this convention, the {@code Bus} implementation should specify the altered behavior
     * explicitly.
     *
     * <p>The {@code messages} can be used to construct the observer. The resulting
     * observer is used only for acknowledgment of the given messages.
     *
     * <p>By default, this method returns the {@code source} observer. See {@code Bus} subclasses
     * for the altered behavior specification.
     *
     * @param messages the messages to create an observer for
     * @param source   the source {@link StreamObserver} to be transformed
     * @return a transformed observer of {@link Ack} streams
     */
    protected
    StreamObserver<Ack> prepareObserver(Iterable<T> messages, StreamObserver<Ack> source) {
        return source;
    }

    /**
     * Tells if this bus can accept messages for posting.
     *
     * <p>If the bus is closed posting to it is going to cause {@code IllegalStateException}.
     */
    @Override
    public final boolean isOpen() {
        return filterChain().isOpen();
    }

    /**
     * Closes the {@linkplain BusFilter filters} of this bus and unregisters all the dispatchers.
     */
    @Override
    public void close() {
        filterChain().close();
        registry.unregisterAll();
    }

    /**
     * Obtains the instance of {@link DeadMessageHandler} for this bus.
     */
    protected abstract DeadMessageHandler<E> deadMessageHandler();

    /**
     * Obtains the instance of {@link EnvelopeValidator} for this bus.
     */
    protected abstract EnvelopeValidator<E> validator();

    /**
     * Obtains the dispatcher registry.
     */
    protected DispatcherRegistry<C, E, D> registry() {
        return registry;
    }

    /**
     * Returns the filter chain for this bus.
     */
    private FilterChain<E> filterChain() {
        return filterChain.get();
    }

    @VisibleForTesting
    public boolean hasFilter(BusFilter<E> filter) {
        return filterChain().contains(filter);
    }

    @VisibleForTesting
    public boolean hasListener(Listener<E> listener) {
        return listeners.contains(listener);
    }

    /**
     * Filters the given messages.
     *
     * <p>Each message goes through the filter chain, specific to the {@code Bus} implementation.
     *
     * <p>If a message passes the filtering, it is included into the resulting {@link Iterable};
     * otherwise, {@linkplain StreamObserver#onNext StreamObserver.onNext()} is called for that
     * message.
     *
     * <p>Any filter in the filter chain may process the message by itself. In this case an observer
     * is notified by the filter directly.
     *
     * @param messages
     *         the message to filter
     * @param observer
     *         the observer to receive the negative outcome of the operation
     * @return a map of filtered messages where keys are messages, and values are envelopes with
     *         these messages
     * @implNote This method returns a map to avoid repeated creation of envelopes when
     *         dispatching. Messages in the returned map come in the same order as in
     *         the incoming sequence.
     */
    private Map<T, E> filter(Iterable<T> messages, StreamObserver<Ack> observer) {
        checkNotNull(messages);
        checkNotNull(observer);
        Map<T, E> result = new LinkedHashMap<>();
        for (var message : messages) {
            var envelope = toEnvelope(message);
            var response = filter(envelope);
            if (response.isPresent()) {
                observer.onNext(response.get());
            } else {
                result.put(message, envelope);
            }
        }
        return result;
    }

    /**
     * Feeds the given message to the bus filters.
     *
     * <p>If the given message is completely processed and should not be passed to the dispatchers,
     * the returned {@link Optional} contains a value with either status.
     *
     * <p>If the message should be passed to the dispatchers, the result of this method is
     * {@link Optional#empty()}.
     *
     * @param message the {@linkplain MessageEnvelope message envelope} to pre-process
     * @return the result of message processing by this bus if any, or
     * {@link Optional#empty()} otherwise
     */
    private Optional<Ack> filter(E message) {
        var filterOutput = filterChain().filter(message);
        return filterOutput;
    }

    /**
     * Packs the given message of type {@code T} into an envelope of type {@code E}.
     *
     * @param message the message to pack
     * @return new envelope with the given message inside
     */
    protected abstract E toEnvelope(T message);

    /**
     * Passes the given envelope for dispatching.
     *
     * <p>Finds and invokes the {@linkplain MessageDispatcher MessageDispatcher(s)} for the given
     * message.
     *
     * <p>This method assumes that the given message has passed the filtering.
     *
     * @see #post(Signal, StreamObserver) the public API for posting
     */
    protected abstract void dispatch(E envelope);

    /**
     * Posts each of the given envelopes into the bus and notifies the given observer.
     *
     * <p>If {@link #dispatch(SignalEnvelope) dispatching} throws a {@link Mistake} it is rethrown.
     * Other types of exceptions are logged and then rethrown.
     *
     * <p>We treat {@link Mistake}s differently and want to void much of console output
     * for this special case of exceptions.
     * Please see {@link io.spine.server.model.AbstractReceptor#invoke(Object, MessageEnvelope)}
     * for more details on special treatment of {@link Mistake} during dispatching.
     *
     * @param envelopes
     *         the envelopes to post
     * @param observer
     *         the observer to be notified of the operation result
     * @see #dispatch(SignalEnvelope)
     */
    @SuppressWarnings("ProhibitedExceptionThrown") // Rethrow a caught exception.
    private void doPost(Iterable<E> envelopes, StreamObserver<Ack> observer) {
        for (var envelope : envelopes) {
            var signalId = envelope.id();
            var ack = acknowledge(signalId);
            observer.onNext(ack);
            onDispatchingStarted(signalId);
            try {
                dispatch(envelope);
            } catch (Mistake m) {
                throw m;
            } catch (Throwable t) {
                logger().atError().withCause(t).log(() -> format(
                        "Unable to dispatch `%s` with ID `%s`.",
                        envelope.messageClass(),
                        signalId.value())
                );
                throw t;
            } finally {
                onDispatched(signalId);
            }
        }
    }

    /**
     * Called before the dispatching of the signal with the passed ID is started.
     *
     * <p>Descendants may override this method and define their own logic on handling the
     * dispatching lifecycle.
     *
     * @param signal
     *         the ID of the signal being dispatched
     * @see #onDispatched(SignalId)
     */
    @SuppressWarnings("NoopMethodInAbstractClass")      // Sets a default behavior.
    protected void onDispatchingStarted(SignalId signal) {
        // Do nothing.
    }

    /**
     * Called after the dispatching of the signal to all of the target dispatchers
     * has been completed.
     *
     * <p>This method is called even if the dispatching of the message has failed.
     *
     * <p>Descendants may override this method and define their own logic on handling the
     * dispatching lifecycle.
     *
     * @param signal
     *         the ID of the dispatched signal
     * @see #onDispatchingStarted(SignalId)
     */
    @SuppressWarnings("NoopMethodInAbstractClass")      // Sets a default behavior.
    protected void onDispatched(SignalId signal) {
        // Do nothing.
    }

    /**
     * Stores the given messages into the underlying storage.
     *
     * @param messages the messages to store
     */
    protected abstract void store(Iterable<T> messages);

    /**
     * A callback for derived classes to modify the order of filters used by the bus.
     *
     * <p>Default implementation inserts:
     * <ol>
     *   <li>a filter which validates incoming messages
     *   <li>a filter for unhandled messages
     * </ol>
     * before the passed filters.
     */
    @OverridingMethodsMustInvokeSuper
    protected Iterable<BusFilter<E>> setupFilters(Iterable<BusFilter<E>> filters) {
        return ImmutableList.<BusFilter<E>>builder()
                .add(new ValidatingFilter<>(validator()))
                .add(new DeadMessageFilter<>(deadMessageHandler(), registry()))
                .addAll(filters)
                .build();
    }

    private FilterChain<E> createFilterChain(Iterable<BusFilter<E>> filters) {
        var possiblyModifiedFilters = setupFilters(filters);
        var result = new FilterChain<>(possiblyModifiedFilters);
        return result;
    }
}
