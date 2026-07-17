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

import com.google.errorprone.annotations.OverridingMethodsMustInvokeSuper;
import com.google.errorprone.annotations.concurrent.LazyInit;
import io.spine.base.EntityState;
import io.spine.server.BoundedContext;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Implementation of {@link RecordBasedRepository} that manages entities
 * derived from {@link AbstractEntity}.
 *
 * @param <I>
 *         the type of the entity identifiers
 * @param <E>
 *         the type of managed entities
 * @param <S>
 *         the type of the entity state
 */
public abstract class AbstractEntityRepository<I,
                                               E extends AbstractEntity<I, S>,
                                               S extends EntityState<I>>
                extends RecordBasedRepository<I, E, S> {

    @LazyInit
    private @MonotonicNonNull StorageConverter<I, E, S> storageConverter;

    /**
     * Creates a new instance with the {@linkplain #entityFactory() factory} of entities of
     * the class specified as the {@code <E>} generic parameter, and with the default
     * {@linkplain #storageConverter() entity storage converter}.
     */
    protected AbstractEntityRepository() {
        super();
    }

    @Override
    protected EntityFactory<E> entityFactory() {
        return entityModelClass().factory();
    }

    @Override
    protected final StorageConverter<I, E, S> storageConverter() {
        if (storageConverter == null) {
            var entityClass = entityModelClass();
            var stateType = entityClass.stateTypeUrl();
            storageConverter = DefaultConverter.forAllFields(stateType, entityFactory());
        }
        return storageConverter;
    }

    /**
     * Initializes the repository by performing the validation of the entity class and
     * creating the storage converter.
     *
     * @param context
     *         the Bounded Context of this repository
     */
    @Override
    @OverridingMethodsMustInvokeSuper
    public void registerWith(BoundedContext context) {
        super.registerWith(context);
        @SuppressWarnings("unused") // Trigger the method to initialize the converter.
        var unused = storageConverter();
    }
}
