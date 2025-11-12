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

import com.google.common.base.Converter;
import com.google.protobuf.FieldMask;
import io.spine.annotation.Internal;
import io.spine.base.EntityState;
import io.spine.base.Identifier;
import io.spine.type.TypeUrl;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static io.spine.protobuf.AnyPacker.pack;
import static io.spine.protobuf.AnyPacker.unpack;

/**
 * An abstract base for converters of entities into {@link EntityRecord}.
 *
 * @param <I>
 *         the type of the entity identifiers
 * @param <E>
 *         the type of managed entities
 * @param <S>
 *         the type of the entity state
 */
public abstract class StorageConverter<I, E extends Entity<I, S>, S extends EntityState<I>>
        extends Converter<E, EntityRecord> implements Serializable {

    private static final long serialVersionUID = 0L;
    private final TypeUrl entityStateType;
    private final EntityFactory<E> entityFactory;
    private final FieldMask fieldMask;

    protected StorageConverter(TypeUrl entityStateType,
                               EntityFactory<E> factory,
                               FieldMask fieldMask) {
        super();
        this.fieldMask = checkNotNull(fieldMask);
        this.entityFactory = factory;
        this.entityStateType = entityStateType;
    }

    /**
     * Obtains the type URL of the state of entities which this converter builds.
     */
    protected TypeUrl entityStateType() {
        return entityStateType;
    }

    /**
     * Obtains the entity factory used by the converter.
     */
    protected EntityFactory<E> entityFactory() {
        return entityFactory;
    }

    /**
     * Obtains the field mask used by this converter to trim the state of entities before the state
     * is {@linkplain #injectState(Entity, EntityState, EntityRecord) injected} into entities.
     */
    protected FieldMask fieldMask() {
        return this.fieldMask;
    }

    /**
     * Creates a copy of this converter modified with the passed filed mask.
     */
    public abstract StorageConverter<I, E, S> withFieldMask(FieldMask fieldMask);

    @Override
    protected EntityRecord doForward(E entity) {
        var builder = toEntityRecord(entity);
        updateBuilder(builder, entity);
        return builder.build();
    }

    /**
     * Creates a new builder of {@code EntityRecord} on top of the passed {@code Entity} state.
     *
     * <p>This method is internal to the framework. End-users should rely on other public
     * endpoints of {@code StorageConverter}.
     *
     * @param entity
     *         an entity to create a record builder from
     * @param <I>
     *         type of entity identifiers
     * @param <E>
     *         type of entity
     * @param <S>
     *         type of entity state
     * @return a new entity record builder reflecting the current state of the passed entity
     */
    @Internal
    public static <I, E extends Entity<I, S>, S extends EntityState<I>>
    EntityRecord.Builder toEntityRecord(E entity) {
        checkNotNull(entity);

        var entityId = Identifier.pack(entity.id());
        var stateAny = pack(entity.state());
        var builder = EntityRecord.newBuilder()
                .setEntityId(entityId)
                .setState(stateAny)
                .setVersion(entity.version())
                .setLifecycleFlags(entity.lifecycleFlags());
        return builder;
    }

    @SuppressWarnings("unchecked" /* The cast is safe since the <I> and <S> types are bound with
            the type <E>, and forward conversion is performed on the entity of type <E>. */)
    @Override
    protected E doBackward(EntityRecord entityRecord) {
        var unpacked = (S) unpack(entityRecord.getState());
        var state = FieldMasks.applyMask(fieldMask(), unpacked);
        var id = (I) Identifier.unpack(entityRecord.getEntityId());
        var entity = entityFactory.create(id);
        checkState(entity != null, "`EntityFactory` produced `null` entity.");
        injectState(entity, state, entityRecord);
        return entity;
    }

    /**
     * Updates the builder with required values, if needed.
     *
     * <p>Derived classes may override to additionally tune
     * the passed entity record builder.
     *
     * @param builder
     *         the entity builder to update
     * @param entity
     *         the entity which data is passed to the {@link EntityRecord} we are building
     */
    @SuppressWarnings({"WeakerAccess", "unused"})
    protected abstract void updateBuilder(EntityRecord.Builder builder, E entity);

    /**
     * Derived classes must implement providing state injection into the passed entity.
     *
     * @param entity
     *         the entity into which inject the state
     * @param state
     *         the state message extracted from the record
     * @param entityRecord
     *         the record which may contain additional properties for the entity
     */
    protected abstract void injectState(E entity, S state, EntityRecord entityRecord);

    @Override
    public int hashCode() {
        return Objects.hash(entityFactory, entityStateType, fieldMask);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof StorageConverter)) {
            return false;
        }
        var other = (StorageConverter<?, ?, ?>) obj;
        return Objects.equals(this.entityStateType, other.entityStateType)
                && Objects.equals(this.entityFactory, other.entityFactory)
                && Objects.equals(this.fieldMask, other.fieldMask);
    }
}
