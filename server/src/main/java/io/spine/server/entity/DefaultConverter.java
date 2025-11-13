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

package io.spine.server.entity;

import com.google.protobuf.FieldMask;
import io.spine.base.EntityState;
import io.spine.type.TypeUrl;

import java.io.Serial;

/**
 * Default implementation of {@code EntityStorageConverter} for {@code AbstractEntity}.
 *
 * @param <I>
 *         the type of entity IDs
 * @param <E>
 *         the type of entities
 * @param <S>
 *         the type of entity states
 */
final class DefaultConverter<I, E extends AbstractEntity<I, S>, S extends EntityState<I>>
        extends StorageConverter<I, E, S> {

    @Serial
    private static final long serialVersionUID = 0L;

    private DefaultConverter(TypeUrl stateType, EntityFactory<E> factory, FieldMask fieldMask) {
        super(stateType, factory, fieldMask);
    }

    static <I, E extends AbstractEntity<I, S>, S extends EntityState<I>>
    StorageConverter<I, E, S> forAllFields(TypeUrl stateType, EntityFactory<E> factory) {
        var allFields = FieldMask.getDefaultInstance();
        return new DefaultConverter<>(stateType, factory, allFields);
    }

    @Override
    public StorageConverter<I, E, S> withFieldMask(FieldMask fieldMask) {
        var stateType = entityStateType();
        var factory = entityFactory();
        return new DefaultConverter<>(stateType, factory, fieldMask);
    }

    @Override
    protected void updateBuilder(EntityRecord.Builder builder, E entity) {
        // Do nothing here.
    }

    /**
     * Injects the state into an entity.
     *
     * @param entity
     *         the entity to inject the state
     * @param state
     *         the state message to inject
     * @param entityRecord
     *         the {@link EntityRecord} which contains additional attributes that may be injected
     */
    @Override
    protected void injectState(E entity, S state, EntityRecord entityRecord) {
        entity.updateState(state, entityRecord.getVersion());
        entity.setLifecycleFlags(entityRecord.lifecycleFlags());
    }
}
