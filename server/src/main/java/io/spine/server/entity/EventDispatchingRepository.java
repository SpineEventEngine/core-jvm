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
import static io.spine.server.Suppliers2.memoize;

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
        extends DefaultRecordBasedRepository<I, E, S>
        implements EventDispatcher {

    private final Supplier<EventRouting<I>> eventRouting;

    protected EventDispatchingRepository() {
        super();
        this.eventRouting = memoize(
                () -> EventRouting.withDefaultByProducerIdOrFirstField(idClass())
        );
    }

    /**
     * Registers itself as an event dispatcher with the parent {@code BoundedContext}.
     *
     * @param context
     *         the {@code BoundedContext} of this repository
     */
    @Override
    @OverridingMethodsMustInvokeSuper
    public void registerWith(BoundedContext context) {
        super.registerWith(context);
        context.internalAccess()
               .registerEventDispatcher(this);

        EventRoutingSetup.apply(entityClass(), eventRouting.get());
        setupEventRouting(eventRouting.get());
    }

    /**
     * A callback for derived repository classes to customize routing schema for events.
     *
     * <p>Default routing returns the ID of the entity that
     * {@linkplain io.spine.core.EventContext#getProducerId() produced} the event.
     * This allows to “link” different kinds of entities by having the same class of IDs.
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

    /**
     * {@inheritDoc}
     *
     * <p>Loads through the {@linkplain #cache() cache}, so that a batch delivered to
     * an entity costs one read instead of one per message.
     *
     * @implSpec An overriding repository is expected to do nothing but call {@code super}.
     *         Overriding is allowed only so that a repository in another package can
     *         re-declare this method to expose it to that package; that is also why this
     *         method is not {@code final}.
     */
    @Override
    protected E findOrCreate(I id) {
        return cache().load(id);
    }

    /**
     * {@inheritDoc}
     *
     * @implSpec Reads through to the storage, bypassing the cache — this is the loading
     *         function the cache itself calls on a miss. Not overridable: were it to go
     *         through {@link #findOrCreate(Object) findOrCreate()}, the two would call
     *         each other in a loop.
     */
    @Override
    protected final E doLoadOrCreate(I id) {
        return super.findOrCreate(id);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Stores through the {@linkplain #cache() cache}, so that a batch delivered to
     * an entity costs one write instead of one per message; the cache flushes the entity
     * to the storage when the batch ends.
     */
    @Override
    public final void store(E entity) {
        cache().store(entity);
    }

    /**
     * {@inheritDoc}
     *
     * @implSpec Writes through to the storage, bypassing the cache — this is the storing
     *         function the cache itself calls when flushing. Not overridable, for the
     *         reason given on {@link #doLoadOrCreate(Object)}.
     */
    @Override
    protected final void doStore(E entity) {
        super.store(entity);
    }
}
