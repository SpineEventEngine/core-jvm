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

    /**
     * The aggregate classes of Java classes.
     *
     * @see #asAggregateClass(Class)
     */
    @SuppressWarnings("unchecked") // `asAggregateClass()` accepts only classes of aggregates.
    private static final ClassValue<AggregateClass<?>> cache =
            cacheOf(cls -> new AggregateClass<>((Class<Aggregate<?, ?, ?>>) cls));

    private final ReceptorMap<EventClass, EmptyClass, Applier> stateEvents;
    private final ReactorClassDelegate<A> delegate;

    /** Creates a new instance. */
    protected AggregateClass(Class<A> cls) {
        super(checkNotNull(cls));
        this.stateEvents = ReceptorMap.create(cls, new EventApplierSignature());
        failIfHasAppliers(cls);
        this.delegate = new ReactorClassDelegate<>(cls);
    }

    /**
     * Fails fast if the aggregate class still declares any
     * {@code @Apply}-annotated event applier.
     *
     * <p>Event sourcing has been removed: an aggregate mutates its state directly in
     * {@code @Assign} / {@code @React} receptors via {@code builder()} and loads from its latest
     * persisted state, so appliers are never invoked. Silently ignoring them would drop state
     * transitions, hence the hard {@link ModelError}.
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
        @SuppressWarnings("unchecked") // The cache creates the model class of `cls`.
        var result = (AggregateClass<A>) cache.get(cls);
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
     * Obtains types of events that are going to be posted to {@code EventBus} as a result
     * of handling messages dispatched to aggregates of this class.
     *
     * <p>This includes:
     * <ol>
     *     <li>Events generated in response to commands.
     *     <li>Events generated as a reaction to incoming events.
     *     <li>Rejections that may be thrown if incoming commands cannot be handled.
     * </ol>
     */
    public ImmutableSet<EventClass> outgoingEvents() {
        var methodResults = union(commandOutput(), reactionOutput());
        var result = union(methodResults, rejections());
        return result.immutableCopy();
    }

    /**
     * Obtains the set of classes of events used as arguments of applier methods.
     *
     * <p>Since the event-sourcing cutover, an aggregate class must not declare any
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
     * Obtains the event applier method for the passed class of events.
     */
    public final Applier applierOf(EventEnvelope event) {
        return stateEvents.findReceptorFor(event).orElseThrow(() -> new ModelError(
                "Aggregate `%s` does not handle event `%s`.", this, event.typeUrl()
        ));
    }
}
