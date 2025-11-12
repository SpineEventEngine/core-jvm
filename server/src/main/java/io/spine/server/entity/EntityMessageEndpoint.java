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

package io.spine.server.entity;

import io.spine.annotation.Internal;
import io.spine.server.delivery.AbstractMessageEndpoint;
import io.spine.server.dispatch.DispatchOutcome;
import io.spine.server.type.SignalEnvelope;

/**
 * Abstract base for endpoints handling messages sent to entities.
 *
 * <p>Loading and storing an entity is a tenant-sensitive operation,
 * which must be performed under the context of the tenant ID in which
 * the message we dispatch was originated.
 *
 * @param <I> the type of entity IDs
 * @param <E> the type of entities
 * @param <M> the type of message envelopes
 */
@Internal
public abstract class EntityMessageEndpoint<I,
                                            E extends Entity<I, ?>,
                                            M extends SignalEnvelope<?, ?, ?>>
        extends AbstractMessageEndpoint<I, M> {

    /** The repository which created this endpoint. */
    private final Repository<I, E> repository;

    protected EntityMessageEndpoint(Repository<I, E> repository, M envelope) {
        super(envelope);
        this.repository = repository;
    }

    /**
     * The callback invoked after the message is dispatched to an entity with the given ID.
     */
    protected abstract void afterDispatched(I entityId);

    /**
     * Invokes entity-specific method for dispatching the message.
     */
    protected abstract DispatchOutcome invokeDispatcher(E entity);

    /**
     * Stores the entity if it was modified during message dispatching.
     *
     * @param entity the entity to store
     */
    protected final void store(E entity) {
        var isModified = isModified(entity);
        if (isModified) {
            onModified(entity);
        } else {
            onEmptyResult(entity);
        }
    }

    /**
     * Verifies whether the entity was modified during the message dispatching.
     */
    protected abstract boolean isModified(E entity);

    /**
     * Callback to perform operations if the entity was modified during message dispatching.
     */
    protected abstract void onModified(E entity);

    /**
     * Allows derived classes to handle empty list of uncommitted events returned by
     * the entity in response to the message.
     */
    protected abstract void onEmptyResult(E entity);

    /**
     * Obtains the parent repository of this endpoint.
     */
    @Override
    public Repository<I, E> repository() {
        return repository;
    }
}
