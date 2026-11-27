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
import io.spine.server.model.ModelClassValue;
import io.spine.server.type.EventClass;
import io.spine.server.type.EventEnvelope;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Provides message-handling information on an {@code Aggregate} class.
 *
 * @param <A> the type of aggregates
 */
public class AggregateClass<A extends Aggregate<?, ?, ?>>
        extends AssigneeEntityClass<A>
        implements ReactingClass {

    /**
     * Associates an {@code AggregateClass} with a Java class of aggregates.
     *
     * @see #asAggregateClass(Class)
     */
    @SuppressWarnings("unchecked") // `asAggregateClass()` accepts only classes of aggregates.
    private static final ModelClassValue<AggregateClass<?>> classValue =
            new ModelClassValue<>(cls -> new AggregateClass<>((Class<Aggregate<?, ?, ?>>) cls));

    private final ReactorClassDelegate<A> delegate;

    /** Creates a new instance. */
    protected AggregateClass(Class<A> cls) {
        super(checkNotNull(cls));
        this.delegate = new ReactorClassDelegate<>(cls);
    }

    /**
     * Obtains an aggregate class for the passed raw class.
     */
    public static <A extends Aggregate<?, ?, ?>> AggregateClass<A> asAggregateClass(Class<A> cls) {
        checkNotNull(cls);
        @SuppressWarnings("unchecked") // The class value creates the model class of `cls`.
        var result = (AggregateClass<A>) classValue.get(cls);
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

    @Override
    public final Optional<EventReactorMethod> reactorOf(EventEnvelope event) {
        return delegate.reactorOf(event);
    }
}
