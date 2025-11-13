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

package io.spine.server.entity.model;

import com.google.errorprone.annotations.concurrent.LazyInit;
import io.spine.server.entity.Entity;
import io.spine.server.entity.EntityFactory;
import io.spine.server.model.ModelError;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

import java.lang.reflect.Constructor;
import java.util.Objects;
import java.io.Serial;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Abstract base for entity factories.
 *
 * @param <E>
 *         the type of produced entities
 */
public abstract class AbstractEntityFactory<E extends Entity<?, ?>> implements EntityFactory<E>  {

    @Serial
    private static final long serialVersionUID = 0L;

    /** The class of entities this factory creates. */
    private final Class<E> entityClass;

    /** The class of entity identifiers. */
    private final Class<?> idClass;

    /**
     * The constructor for entities of this class.
     *
     * <p>If the entity class has multiple constructors, this one points to the one which
     * accepts one parameter.
     *
     * @see #constructor()
     */
    @LazyInit
    @SuppressWarnings("Immutable") // effectively
    private transient volatile @MonotonicNonNull Constructor<E> constructor;

    protected AbstractEntityFactory(Class<E> entityClass) {
        checkNotNull(entityClass);
        this.idClass = EntityClass.idClass(entityClass);
        this.entityClass = entityClass;
    }

    /**
     * Obtains the class of entities created by the factory.
     */
    protected final Class<E> entityClass() {
        return entityClass;
    }

    /**
     * Obtains the class of identifiers of entities that this factory creates.
     */
    protected final Class<?> idClass() {
        return idClass;
    }

    @Override
    @SuppressWarnings("SynchronizeOnThis") // Double-check idiom for lazy init.
    public final Constructor<E> constructor() {
        var result = constructor;
        if (result == null) {
            synchronized (this) {
                result = constructor;
                if (result == null) {
                    constructor = findConstructor();
                    result = constructor;
                }
            }
        }
        return result;
    }

    /**
     * Obtains the constructor for the entity class.
     *
     * @throws ModelError if the entity class does not have the required constructor
     */
    protected abstract Constructor<E> findConstructor();

    @Override
    public int hashCode() {
        return Objects.hash(idClass, entityClass);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof AbstractEntityFactory)) {
            return false;
        }
        final var other = (AbstractEntityFactory<?>) obj;
        return Objects.equals(this.idClass, other.idClass)
                && Objects.equals(this.entityClass, other.entityClass);
    }
}
