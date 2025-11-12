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

package io.spine.server.aggregate.model;

import com.google.common.collect.ImmutableSet;
import io.spine.server.aggregate.Aggregate;
import io.spine.server.entity.model.AssigneeEntityClass;
import io.spine.server.event.model.EventReactorMethod;
import io.spine.server.event.model.ReactingClass;
import io.spine.server.event.model.ReactorClassDelegate;
import io.spine.server.model.ModelError;
import io.spine.server.model.ReceptorMap;
import io.spine.server.type.EmptyClass;
import io.spine.server.type.EventClass;
import io.spine.server.type.EventEnvelope;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Sets.union;

/**
 * Provides message-handling information on an {@code Aggregate} class.
 *
 * @param <A> the type of aggregates
 */
public class AggregateClass<A extends Aggregate<?, ?, ?>>
        extends AssigneeEntityClass<A>
        implements ReactingClass {

    private final ReceptorMap<EventClass, EmptyClass, Applier> stateEvents;
    private final ImmutableSet<EventClass> importableEvents;
    private final ReactorClassDelegate<A> delegate;

    /** Creates new instance. */
    protected AggregateClass(Class<A> cls) {
        super(checkNotNull(cls));
        this.stateEvents = ReceptorMap.create(cls, new EventApplierSignature());
        this.importableEvents = stateEvents.messageClasses(Applier::allowsImport);
        this.delegate = new ReactorClassDelegate<>(cls);
    }

    /**
     * Obtains an aggregate class for the passed raw class.
     */
    public static <A extends Aggregate<?, ?, ?>> AggregateClass<A> asAggregateClass(Class<A> cls) {
        checkNotNull(cls);
        @SuppressWarnings("unchecked")
        var result = (AggregateClass<A>)
                get(cls, AggregateClass.class, () -> new AggregateClass<>(cls));
        return result;
    }

    @Override
    public final ImmutableSet<EventClass> events() {
        return delegate.events();
    }

    /**
     * Obtains the set of <em>external</em> event classes on which this aggregate class reacts.
     */
    @Override
    public final ImmutableSet<EventClass> externalEvents() {
        return delegate.externalEvents();
    }

    /**
     * Obtains the set of <em>domestic</em> event classes on which this aggregate class reacts.
     */
    @Override
    public final ImmutableSet<EventClass> domesticEvents() {
        return delegate.domesticEvents();
    }

    /**
     * Obtains types of events that are going to be posted to {@code EventBus} as the result
     * of handling messages dispatched to aggregates of this class.
     *
     * <p>This includes:
     * <ol>
     *     <li>Events generated in response to commands.
     *     <li>Events generated as reaction to incoming events.
     *     <li>Rejections that may be thrown if incoming commands cannot be handled.
     *     <li>Events imported by the aggregate.
     * </ol>
     *
     * <p>Although technically imported events are not "produced" by the aggregates,
     * they end up in the same {@code EventBus} and have the same behaviour as the ones
     * emitted by the aggregates.
     */
    public ImmutableSet<EventClass> outgoingEvents() {
        var methodResults = union(commandOutput(), reactionOutput());
        var generatedEvents = union(methodResults, rejections());
        var result = union(generatedEvents, importableEvents());
        return result.immutableCopy();
    }

    /**
     * Obtains set of classes of events used as arguments of applier methods.
     *
     * @see #importableEvents()
     */
    public final ImmutableSet<EventClass> stateEvents() {
        return stateEvents.messageClasses();
    }

    /**
     * Obtains a set of event classes that are
     * {@linkplain io.spine.server.aggregate.Apply#allowImport() imported}
     * by the aggregates of this class.
     *
     * @see #stateEvents()
     */
    public final ImmutableSet<EventClass> importableEvents() {
        return importableEvents;
    }

    /**
     * Returns {@code true} if aggregates of this class
     * {@link io.spine.server.aggregate.Apply#allowImport() import} events of at least one class;
     * {@code false} otherwise.
     */
    public final boolean importsEvents() {
        return !importableEvents.isEmpty();
    }

    @Override
    public final Optional<EventReactorMethod> reactorOf(EventEnvelope event) {
        return delegate.reactorOf(event);
    }

    @Override
    public ImmutableSet<EventClass> reactionOutput() {
        return delegate.reactionOutput();
    }

    /**
     * Obtains event applier method for the passed class of events.
     */
    public final Applier applierOf(EventEnvelope event) {
        return stateEvents.findReceptorFor(event).orElseThrow(() -> new ModelError(
                "Aggregate `%s` does not handle event `%s`.", this, event.typeUrl()
        ));
    }
}
