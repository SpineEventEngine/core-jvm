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
    private final ReactorClassDelegate<A> delegate;

    /** Creates new instance. */
    protected AggregateClass(Class<A> cls) {
        super(checkNotNull(cls));
        this.stateEvents = ReceptorMap.create(cls, new EventApplierSignature());
        failIfHasAppliers(cls);
        this.delegate = new ReactorClassDelegate<>(cls);
    }

    /**
     * Fails fast if the aggregate (or aggregate part) class still declares any
     * {@code @Apply}-annotated event applier.
     *
     * <p>Event sourcing has been removed: an aggregate mutates its state directly in
     * {@code @Assign} / {@code @React} receptors via {@code builder()} and loads from its latest
     * persisted state, so appliers are never invoked. Silently ignoring them would drop state
     * transitions, hence the hard {@link ModelError}. This check also covers aggregate parts,
     * because {@code AggregatePartClass} calls {@code super(cls)}.
     */
    private void failIfHasAppliers(Class<A> cls) {
        var appliers = stateEvents.messageClasses();
        if (!appliers.isEmpty()) {
            throw new ModelError(
                    "The aggregate class `%s` declares `@Apply`-annotated event applier(s) for " +
                            "%s. Event sourcing has been removed: move each applier's body into " +
                            "the `@Assign` / `@React` receptor that emits the event (mutating " +
                            "the state via `builder()`), and delete the `@Apply` method(s).",
                    cls.getName(), appliers);
        }
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
     * </ol>
     */
    public ImmutableSet<EventClass> outgoingEvents() {
        var methodResults = union(commandOutput(), reactionOutput());
        var result = union(methodResults, rejections());
        return result.immutableCopy();
    }

    /**
     * Obtains set of classes of events used as arguments of applier methods.
     *
     * <p>Since the event-sourcing cutover an aggregate class must not declare any
     * {@code @Apply}-annotated appliers (see the constructor), so for a successfully built class
     * this set is always empty. It is used only to <em>detect</em> lingering appliers and fail
     * fast with a {@link ModelError}.
     */
    public final ImmutableSet<EventClass> stateEvents() {
        return stateEvents.messageClasses();
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
