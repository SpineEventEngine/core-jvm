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

import com.google.common.base.Converter;
import com.google.protobuf.FieldMask;
import io.spine.annotation.Internal;
import io.spine.base.EntityState;
import io.spine.base.Identifier;
import io.spine.type.TypeUrl;
import org.jspecify.annotations.Nullable;

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
        extends Converter<E, EntityRecord> {

    private final TypeUrl entityStateType;
    private final EntityFactory<E> entityFactory;

    /**
     * Creates a new converter.
     *
     * @param entityStateType
     *         the type URL of the state of entities that this converter builds
     * @param factory
     *         the factory that creates the entities
     */
    protected StorageConverter(TypeUrl entityStateType, EntityFactory<E> factory) {
        super();
        this.entityFactory = factory;
        this.entityStateType = entityStateType;
    }

    /**
     * Creates a new converter ignoring the passed field mask.
     *
     * @param entityStateType
     *         the type URL of the state of entities that this converter builds
     * @param factory
     *         the factory that creates the entities
     * @param fieldMask
     *         the ignored field mask
     * @deprecated Field masks are no longer supported.
     *         Please use {@link #StorageConverter(TypeUrl, EntityFactory)}.
     */
    @Deprecated
    protected StorageConverter(TypeUrl entityStateType,
                               EntityFactory<E> factory,
                               FieldMask fieldMask) {
        this(entityStateType, factory);
        checkNotNull(fieldMask);
    }

    /**
     * Obtains the type URL of the state of entities that this converter builds.
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
     * Returns the default instance of {@code FieldMask}.
     *
     * <p>Formerly, this method obtained the field mask used by this converter to trim
     * the state of entities.
     *
     * @deprecated Field masks are no longer supported. The converter does not trim
     *         the state of entities. Please remove the calls and overrides.
     */
    @Deprecated
    protected FieldMask fieldMask() {
        return FieldMask.getDefaultInstance();
    }

    /**
     * Returns this converter.
     *
     * <p>Formerly, this method created a copy of this converter modified with
     * the passed field mask.
     *
     * @param fieldMask
     *         the ignored field mask
     * @return this converter
     * @deprecated Field masks are no longer supported. Please remove the call.
     */
    @Deprecated
    public StorageConverter<I, E, S> withFieldMask(FieldMask fieldMask) {
        checkNotNull(fieldMask);
        return this;
    }

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

    @Override
    @SuppressWarnings({"unchecked", "ConstantValue"})
    protected E doBackward(EntityRecord entityRecord) {
        var state = (S) unpack(entityRecord.getState());
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
     *         the entity whose data is passed to the {@link EntityRecord} we are building
     */
    @SuppressWarnings({"WeakerAccess", "unused"})
    protected abstract void updateBuilder(EntityRecord.Builder builder, E entity);

    /**
     * Derived classes must implement providing state injection into the passed entity.
     *
     * @param entity
     *         the entity into which to inject the state
     * @param state
     *         the state message extracted from the record
     * @param entityRecord
     *         the record that may contain additional properties for the entity
     */
    protected abstract void injectState(E entity, S state, EntityRecord entityRecord);

    @Override
    public int hashCode() {
        return Objects.hash(entityFactory, entityStateType);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        return (this == obj)
                ||
                ((obj instanceof StorageConverter<?, ?, ?> other)
                        && Objects.equals(this.entityStateType, other.entityStateType)
                        && Objects.equals(this.entityFactory, other.entityFactory));
    }
}
