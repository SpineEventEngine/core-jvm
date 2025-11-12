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
import io.spine.base.EntityState;
import io.spine.base.Identifier;
import io.spine.protobuf.Messages;
import io.spine.server.entity.DefaultEntityFactory;
import io.spine.server.entity.Entity;
import io.spine.server.entity.EntityFactory;
import io.spine.server.entity.EntityVisibility;
import io.spine.server.model.ModelClass;
import io.spine.server.model.ModelError;
import io.spine.system.server.EntityTypeName;
import io.spine.type.MessageType;
import io.spine.type.TypeUrl;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

import java.lang.reflect.Constructor;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.util.Exceptions.newIllegalStateException;

/**
 * A class of entities.
 *
 * @param <E> the type of entities
 */
@SuppressWarnings("SynchronizeOnThis") // Double-check idiom for lazy init.
public class EntityClass<E extends Entity<?, ?>> extends ModelClass<E> {

    /** The class of entity IDs. */
    private final Class<?> idClass;

    /** The class of the entity state. */
    private final Class<? extends EntityState<?>> stateClass;

    /** Type of the entity state. */
    private final TypeUrl entityStateType;

    /** Provides info on operations that can see entities of this class. */
    private final EntityVisibility visibility;

    /** The default state of entities of this class. */
    @LazyInit
    private volatile @MonotonicNonNull EntityState<?> defaultState;

    @LazyInit
    private volatile @MonotonicNonNull EntityFactory<E> factory;

    /** Creates a new instance of the model class for the passed class of entities. */
    protected EntityClass(Class<E> cls) {
        super(cls);
        this.idClass = idClass(cls);
        this.stateClass = stateClassOf(cls);
        this.entityStateType = TypeUrl.of(stateClass);
        this.visibility = EntityVisibility.of(stateClass)
                                          .orElseThrow(() -> noOptionDefined(cls));
    }

    private IllegalStateException noOptionDefined(Class<E> cls) {
        throw newIllegalStateException("The state message of type `%s` " +
                                               "corresponding to the entity `%s` " +
                                               "has no `entity` option defined.", stateClass, cls);
    }

    /**
     * Obtains an entity class for the passed raw class.
     */
    public static <E extends Entity<?, ?>> EntityClass<E> asEntityClass(Class<E> cls) {
        checkNotNull(cls);
        @SuppressWarnings("unchecked")
        var result = (EntityClass<E>) get(cls, EntityClass.class, () -> new EntityClass<>(cls));
        return result;
    }

    /**
     * Creates new entity.
     */
    public E create(Object constructionArgument) {
        checkNotNull(constructionArgument);
        var result = factory().create(constructionArgument);
        return result;
    }

    /**
     * Obtains the factory for creating entities.
     */
    public final EntityFactory<E> factory() {
        if (factory == null) {
            factory = createFactory();
        }
        return factory;
    }

    /**
     * Creates a new instance of the factory for creating entities.
     */
    protected EntityFactory<E> createFactory() {
        return new DefaultEntityFactory<>(rawClass());
    }

    /**
     * Obtains the default state for this class of entities.
     */
    public final EntityState<?> defaultState() {
        var result = defaultState;
        if (result == null) {
            synchronized (this) {
                result = defaultState;
                if (result == null) {
                    var stateClass = stateClass();
                    defaultState = Messages.getDefaultInstance(stateClass);
                    result = defaultState;
                }
            }
        }
        return result;
    }

    /**
     * Obtains constructor for the entities of this class.
     */
    public final Constructor<E> constructor() {
        return factory().constructor();
    }

    /**
     * Obtains the class of IDs used by the entities of this class.
     */
    public final Class<?> idClass() {
        return idClass;
    }

    /**
     * Obtains the class of the state of entities of this class.
     */
    public final Class<? extends EntityState<?>> stateClass() {
        return stateClass;
    }

    /**
     * Obtains the entity state type.
     */
    public final MessageType stateType() {
        var descriptor = defaultState().getDescriptorForType();
        var result = new MessageType(descriptor);
        return result;
    }

    /**
     * Obtains the visibility of this entity type as declared in the Protobuf definition.
     */
    public final EntityVisibility visibility() {
        return visibility;
    }

    /**
     * Obtains the name of this class as an {@link EntityTypeName}.
     */
    public final EntityTypeName typeName() {
        return EntityTypeName.newBuilder()
                .setJavaClassName(rawClass().getCanonicalName())
                .build();
    }

    /**
     * Obtains the raw class of the entities.
     */
    @SuppressWarnings("unchecked") // The cast is protected by the generic param of this class.
    @Override
    public Class<E> rawClass() {
        return (Class<E>) super.rawClass();
    }

    /**
     * Obtains type URL of the state of entities of this class.
     */
    public final TypeUrl stateTypeUrl() {
        return entityStateType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof EntityClass)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        var that = (EntityClass<?>) o;
        return Objects.equals(idClass, that.idClass) &&
                Objects.equals(stateClass, that.stateClass) &&
                Objects.equals(entityStateType, that.entityStateType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), idClass, stateClass, entityStateType);
    }

    /**
     * Obtains the class of identifiers from the passed entity class.
     *
     * <p>Checks that this class of identifiers obtained from the passed entity class
     * is supported by the framework.
     *
     * <p>The type of entity identifiers ({@code <I>}) cannot be bound because
     * it can be {@code Long}, {@code String}, {@code Integer}, and class implementing
     * {@code Message}.
     *
     * <p>We perform the check to detect possible programming error
     * in declarations of entity and repository classes <em>until</em> we have
     * compile-time model check.
     *
     * @throws ModelError if unsupported ID class passed
     */
    public static <I> Class<I> idClass(Class<? extends Entity<?, ?>> cls) {
        @SuppressWarnings("unchecked") // The type is preserved by the Entity type declaration.
        var idClass = (Class<I>) Entity.GenericParameter.ID.argumentIn(cls);
        try {
            Identifier.checkSupported(idClass);
        } catch (IllegalArgumentException e) {
            throw new ModelError(e);
        }
        return idClass;
    }

    /**
     * Retrieves the state class of the passed entity class.
     *
     * <p>Though this method is {@code public}, it is <em>not</em> considered a part of the
     * public API. It is used internally by other framework routines and not designed for efficient
     * execution by Spine users.
     */
    public static <S extends EntityState<?>> Class<S>
    stateClassOf(Class<? extends Entity<?, ?>> entityClass) {
        @SuppressWarnings("unchecked") // The type is preserved by the Entity type declaration.
        var result = (Class<S>) Entity.GenericParameter.STATE.argumentIn(entityClass);
        return result;
    }
}
