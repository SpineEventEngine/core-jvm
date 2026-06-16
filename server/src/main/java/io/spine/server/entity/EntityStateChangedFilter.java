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

import io.spine.base.EventMessage;
import io.spine.server.entity.model.EntityClass;
import io.spine.system.server.event.EntityStateChanged;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * An {@link EventFilter} that may filter out entity state updates.
 */
final class EntityStateChangedFilter implements EventFilter {

    private final boolean allowUpdates;

    private EntityStateChangedFilter(boolean updates) {
        this.allowUpdates = updates;
    }

    /**
     * Creates a new filter for the given type.
     *
     * <p>If the visibility of the entity type is sufficient for subscription or querying,
     * the resulting filter allows all events. Otherwise, the filter discards
     * {@link EntityStateChanged} events.
     *
     * @param entityClass
     *         the class of entity to create a filter for
     * @return new instance
     */
    static EntityStateChangedFilter forType(EntityClass<?> entityClass) {
        checkNotNull(entityClass);
        var allowUpdates = entityClass.visibility().isNotNone();
        return new EntityStateChangedFilter(allowUpdates);
    }

    @Override
    public Optional<? extends EventMessage> filter(EventMessage event) {
        if (allowUpdates || !(event instanceof EntityStateChanged)) {
            return Optional.of(event);
        } else {
            return Optional.empty();
        }
    }
}
