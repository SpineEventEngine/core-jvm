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

package io.spine.server.entity;

import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.OverridingMethodsMustInvokeSuper;
import io.spine.base.EntityState;
import io.spine.server.BoundedContext;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.event.EventDispatcher;
import io.spine.server.route.EventRouting;
import io.spine.server.route.setup.EventRoutingSetup;
import io.spine.server.type.EventEnvelope;

import java.util.Set;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.util.Suppliers2.memoize;

/**
 * Abstract base for repositories that deliver events to entities they manage.
 *
 * @param <I>
 *         the type of the entity identifiers
 * @param <E>
 *         the type of managed entities
 * @param <S>
 *         the type of the entity state
 */
public abstract class EventDispatchingRepository<I,
                                                 E extends AbstractEntity<I, S>,
                                                 S extends EntityState<I>>
        extends AbstractEntityRepository<I, E, S>
        implements EventDispatcher {

    private final Supplier<EventRouting<I>> eventRouting;

    protected EventDispatchingRepository() {
        super();
        this.eventRouting = memoize(
                () -> EventRouting.withDefaultByProducerIdOrFirstField(idClass()));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Sets up the routing of the events this repository dispatches.
     *
     * @param context
     *         the {@code BoundedContext} of this repository
     */
    @Override
    @OverridingMethodsMustInvokeSuper
    public void registerWith(BoundedContext context) {
        super.registerWith(context);
        EventRoutingSetup.apply(entityClass(), eventRouting.get());
        setupEventRouting(eventRouting.get());
    }

    /**
     * A callback for derived repository classes to customize routing schema for events.
     *
     * <p>Default routing returns the ID of the entity that
     * {@linkplain io.spine.core.EventContext#getProducerId() produced} the event.
     * This allows one to “link” different kinds of entities by having the same class of IDs.
     * More complex scenarios (e.g., one-to-many relationships) may require custom routing schemas.
     *
     * @param routing
     *         the routing schema to customize
     */
    @SuppressWarnings("NoopMethodInAbstractClass") // see Javadoc
    protected void setupEventRouting(EventRouting<I> routing) {
        // Do nothing.
    }

    /**
     * Dispatches the event to the corresponding entities.
     *
     * <p>If there is no stored entity with such an ID, a new one is created and stored after it
     * handles the passed event.
     *
     * @param event the event to dispatch
     */
    @Override
    public final DispatchOutcome dispatch(EventEnvelope event) {
        checkNotNull(event);
        var targets = route(event);
        return dispatchTo(targets, event);
    }

    /**
     * Dispatches the given event to entities with the given identifiers and
     * returns the dispatch outcome.
     *
     * @param ids
     *         the identifiers of the target entities
     * @param event
     *         the event to dispatch
     */
    protected abstract DispatchOutcome dispatchTo(Set<I> ids, EventEnvelope event);

    /**
     * Determines the targets of the given event.
     *
     * @param event the event to find targets for
     * @return a set of IDs of projections to dispatch the given event to
     */
    protected Set<I> route(EventEnvelope event) {
        var targets = route(eventRouting.get(), event);
        @SuppressWarnings("unchecked")
        var result = (Set<I>) targets.orElse(ImmutableSet.of());
        return result;
    }
}
