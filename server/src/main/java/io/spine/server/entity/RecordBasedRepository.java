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

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.OverridingMethodsMustInvokeSuper;
import com.google.protobuf.FieldMask;
import io.spine.annotation.Experimental;
import io.spine.annotation.Internal;
import io.spine.annotation.VisibleForTesting;
import io.spine.base.EntityState;
import io.spine.client.EntityId;
import io.spine.client.OrderBy;
import io.spine.client.ResponseFormat;
import io.spine.client.TargetFilters;
import io.spine.client.Targets;
import io.spine.core.Signal;
import io.spine.query.EntityQuery;
import io.spine.server.entity.storage.EntityRecordStorage;
import io.spine.server.entity.storage.ToEntityRecordQuery;
import io.spine.server.storage.QueryConverter;
import io.spine.server.storage.RecordWithColumns;
import io.spine.type.TypeUrl;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.Iterators.transform;
import static com.google.common.collect.Lists.newLinkedList;
import static io.spine.protobuf.AnyPacker.unpack;
import static io.spine.util.Exceptions.newIllegalArgumentException;
import static io.spine.util.Exceptions.newIllegalStateException;
import static io.spine.util.Iterators2.filter;
import static io.spine.validation.Validate.check;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * The base class for repositories that store entities as records.
 *
 * <p>Such a repository is backed by {@link EntityRecordStorage}.
 * Entity states are stored as {@link EntityRecord}s.
 *
 * @param <I>
 *         the type of entity identifiers
 * @param <E>
 *         the type of stored entities
 * @param <S>
 *         the type of entity state messages
 */
public abstract class RecordBasedRepository<I, E extends Entity<I, S>, S extends EntityState<I>>
        extends Repository<I, E> implements QueryableRepository<I, S> {

    /** Creates a new instance. */
    protected RecordBasedRepository() {
        super();
    }

    /**
     * Obtains {@link EntityFactory} associated with this repository.
     */
    protected abstract EntityFactory<E> entityFactory();

    /**
     * Obtains {@link StorageConverter} associated with this repository.
     */
    protected abstract StorageConverter<I, E, S> storageConverter();

    /**
     * Ensures that the repository has the storage.
     *
     * @return storage instance
     * @throws IllegalStateException
     *         if the storage is null
     */
    protected EntityRecordStorage<I, S> recordStorage() {
        @SuppressWarnings("unchecked") // OK as we control the creation in createStorage().
        var storage = (EntityRecordStorage<I, S>) storage();
        return storage;
    }

    @OverridingMethodsMustInvokeSuper
    @Override
    public E create(I id) {
        var result = entityFactory().create(id);
        return result;
    }

    @Override
    protected void doStore(E entity) {
        var record = toRecord(entity);
        var storage = recordStorage();
        storage.write(record);
    }

    @Override
    public Iterator<E> iterator(Predicate<E> filter) {
        var allEntities = loadAll(ResponseFormat.getDefaultInstance());
        return filter(allEntities, filter);
    }

    /**
     * Applies a given {@link Migration} to an entity with the given ID.
     *
     * <p>The operation is performed in three steps:
     * <ol>
     *     <li>Load an entity by the given ID.
     *     <li>Transform it through the migration operation.
     *     <li>Store the entity back in the repository or delete it depending on the migration
     *         configuration.
     * </ol>
     *
     * <p>This operation is only supported for entities that are
     * {@linkplain TransactionalEntity transactional}.
     *
     * @throws IllegalArgumentException
     *         if the entity with the given ID is not found in the repository
     * @throws IllegalStateException
     *         if the repository manages a non-transactional entity type
     * @see Migration
     * @see #applyMigration(Set, Migration) the batch version of the method
     */
    @SuppressWarnings("unchecked") // Checked at runtime.
    @Experimental
    public final <T extends TransactionalEntity<I, S, ?>>
    void applyMigration(I id, Migration<I, T, S, ?> migration) {
        checkNotNull(id);
        checkNotNull(migration);
        checkEntityIsTransactional();

        var entity = findOrThrow(id);
        migration.applyTo((T) entity, (RecordBasedRepository<I, T, S>) this);
        if (migration.physicallyRemoveRecord()) {
            delete(id, migration);
        } else {
            store(entity);
        }
        migration.finishCurrentOperation();
    }

    /**
     * Applies a {@link Migration} to several entities in batch.
     *
     * <p>The operation is performed in three steps:
     * <ol>
     *     <li>Load entities by the given IDs.
     *     <li>Transform each entity through the migration operation.
     *     <li>Store the entities that are not configured to be deleted by the {@link Migration}
     *         back to the repository.
     * </ol>
     *
     * <p>This operation is only supported for entities that are
     * {@linkplain TransactionalEntity transactional}.
     *
     * @throws IllegalStateException
     *         if the repository manages a non-transactional entity type
     * @see Migration
     */
    @SuppressWarnings("unchecked") // Checked at runtime.
    @Experimental
    public final <T extends TransactionalEntity<I, S, ?>>
    void applyMigration(Set<I> ids, Migration<I, T, S, ?> migration) {
        checkNotNull(ids);
        checkNotNull(migration);
        checkEntityIsTransactional();

        var filters = Targets.someOf(entityModelClass().stateClass(), ids)
                             .getFilters();
        var entities = find(filters, ResponseFormat.getDefaultInstance());

        Deque<E> migratedEntities = newLinkedList();
        while (entities.hasNext()) {
            var entity = entities.next();
            migration.applyTo((T) entity, (RecordBasedRepository<I, T, S>) this);
            if (migration.physicallyRemoveRecord()) {
                var id = entity.id();
                delete(id, migration);
            } else {
                migratedEntities.add(entity);
            }
            migration.finishCurrentOperation();
        }
        store(migratedEntities);
    }

    @Override
    protected EntityRecordStorage<I, S> createStorage() {
        var sf = defaultStorageFactory();
        var result = sf.createEntityRecordStorage(context().spec(), entityClass());
        return result;
    }

    /**
     * Stores {@linkplain Entity Entities} in bulk.
     *
     * <p>Note: The storage must be assigned before calling this method.
     *
     * @param entities
     *         the {@linkplain Entity Entities} to store
     */
    public void store(Collection<E> entities) {
        var records = entities.stream()
                .map(this::toRecord)
                .collect(toImmutableList());
        recordStorage().writeAll(records);
        entities.forEach(this::afterStore);
    }

    /**
     * Finds an entity with the passed ID.
     *
     * @param id
     *         the ID of the entity to find
     * @return the entity or {@link Optional#empty()} if there is no entity with such an ID
     */
    @Override
    public Optional<E> find(I id) {
        var record = findRecord(id);
        return record.map(this::toEntity);
    }

    /**
     * Finds an entity with the passed ID even if the entity is
     * {@linkplain WithLifecycle#isActive() active}.
     *
     * @param id
     *         the ID of the entity to find
     * @return the entity or {@link Optional#empty()} if there is no entity with such an ID,
     *         or the entity is not active
     */
    public Optional<E> findActive(I id) {
        var record = findRecord(id);
        var result = record.filter(WithLifecycle::isActive)
                           .map(this::toEntity);
        return result;
    }

    /**
     * Finds a record and returns it if its {@link LifecycleFlags} don't make it
     * {@linkplain WithLifecycle#isActive() active}.
     */
    private Optional<EntityRecord> findRecord(I id) {
        var storage = recordStorage();
        var found = storage.read(id);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        var record = found.get();
        return Optional.of(record);
    }

    @VisibleForTesting
    public Iterator<E> loadAll(ResponseFormat format) {
        var records = findRecords(format);
        var result = transform(records, this::toEntity);
        return result;
    }

    /**
     * Loads all the entities in this repository with IDs
     * contained within the passed {@code ids} values.
     *
     * <p>Provides a convenience wrapper around multiple invocations of
     * {@link #find(Object)}. Descendants may optimize the execution of this
     * method, choosing the most suitable way for the particular storage engine used.
     *
     * <p>The result only contains those entities whose IDs are contained inside
     * the passed {@code ids}. The resulting collection is always returned
     * with no {@code null} values.
     *
     * <p>The order of objects in the result is not guaranteed to be the same
     * as the order of IDs passed as argument.
     *
     * <p>If the IDs contain duplicates, the result may also contain duplicates
     * depending on a particular implementation.
     *
     * <p>Note: The storage must be assigned before calling this method.
     *
     * @param ids
     *         entity IDs to search for
     * @return all the entities in this repository with the IDs matching the given {@code Iterable}
     */
    public Iterator<E> loadAll(Iterable<I> ids) {
        var storage = recordStorage();
        var records = storage.readAll(ids);
        var result = transform(records, this::toEntity);
        return result;
    }

    /**
     * Loads all the entities in this repository with IDs
     * contained within the passed {@code ids} values.
     *
     * <p>The passed field mask is ignored.
     *
     * @param ids
     *         entity IDs to search for
     * @param fieldMask
     *         the ignored field mask
     * @return all the entities in this repository with the IDs matching the given {@code Iterable}
     * @deprecated Field masks are no longer supported. Please use {@link #loadAll(Iterable)}.
     */
    @Deprecated
    public Iterator<E> loadAll(Iterable<I> ids, FieldMask fieldMask) {
        return loadAll(ids);
    }

    /**
     * Obtains iterator over all present {@linkplain EntityRecord entity records}.
     *
     * <p>The maximum number of resulting entity states is limited by
     * the {@code ResponseFormat.limit}. If the limit is {@code 0}, all the entity states are
     * retrieved.
     *
     * <p>The order of the resulting entity states is defined by {@code ResponseFormat.order_by}.
     *
     * <p>The resulting entity states contain all the fields. The deprecated
     * {@code ResponseFormat.field_mask} is ignored.
     *
     * @param format
     *         the expected format of the response
     * @return an iterator over all records
     */
    @Internal
    @Override
    public Iterator<EntityRecord> findRecords(ResponseFormat format) {
        checkNotNull(format);
        var storage = recordStorage();
        var query = QueryConverter.newQuery(storage.recordSpec(), format);
        var records = storage.readAll(query);
        return records;
    }

    /**
     * Finds the entities passing the given filters.
     *
     * <p>A number of elements to retrieve can be limited to a certain number. The order of
     * the resulting entities is specified by the {@link OrderBy}.
     *
     * <p>The states of the resulting entities contain all the fields. The deprecated
     * {@code ResponseFormat.field_mask} is ignored.
     *
     * <p>The field paths in the entity column field filters are specified
     * to contain a single path member - the name of the entity column.
     *
     * <p>The filtering process is delegated to the underlying {@link EntityRecordStorage}.
     *
     * <p>Note: The storage must be assigned before calling this method.
     *
     * @param filters
     *         the entity filters
     * @param format
     *         the expected format of the query response
     * @return all the entities in this repository passed through the filters
     */
    public Iterator<E> find(TargetFilters filters, ResponseFormat format) {
        checkNotNull(filters);
        check(filters);
        checkNotNull(format);

        var records = findRecords(filters, format);
        var result = transform(records, this::toEntity);
        return result;
    }

    /**
     * Finds the entities according to the given {@code EntityQuery}.
     *
     * <p>Note: The storage must be assigned before calling this method.
     *
     * @param query
     *         the entity query
     * @return all the entities in this repository that satisfy the query
     */
    public Iterator<E> find(EntityQuery<I, S, ?> query) {
        checkNotNull(query);
        var records = findRecords(query);
        var result = transform(records, this::toEntity);
        return result;
    }

    @Override
    public Iterator<S> findStates(EntityQuery<I, S, ?> query) {
        checkNotNull(query);
        var records = findRecords(query);
        var stateIterator = transform(records, this::stateFrom);
        return stateIterator;
    }

    @Override
    @Internal
    public Iterator<EntityRecord> findRecords(TargetFilters filters, ResponseFormat format) {
        checkNotNull(filters);
        check(filters);
        checkNotNull(format);

        var storage = recordStorage();
        var query =
                QueryConverter.convert(filters, format, storage.recordSpec());
        var records = storage.readAll(query);
        return records;
    }

    private Iterator<EntityRecord> findRecords(EntityQuery<I, S, ?> query) {
        var recordQuery = ToEntityRecordQuery.transform(query);
        return recordStorage().readAll(recordQuery);
    }

    private E findOrThrow(I id) {
        return find(id).orElseThrow(() -> newIllegalArgumentException(
                "An entity `%s` with ID `%s` is not found in the repository.",
                entityClass().getCanonicalName(), id
        ));
    }

    /**
     * Removes an entity record with a passed ID from the storage.
     */
    private boolean delete(I id) {
        var deleted = recordStorage().delete(id);
        return deleted;
    }

    /**
     * Removes an entity record from the storage and posts a corresponding system event.
     *
     * @param id
     *         the entity ID
     * @param deletionCause
     *         the {@code Signal} that caused the deletion
     */
    private boolean deleteAndPostEvent(I id, Signal<?, ?, ?> deletionCause) {
        var deleted = delete(id);
        if (deleted) {
            lifecycleOf(id).onRemovedFromStorage(ImmutableList.of(deletionCause.messageId()));
        }
        return deleted;
    }

    /**
     * Deletes an entity record as a result of the {@link Migration} operation.
     */
    private void delete(I id, Migration<I, ?, S, ?> migration) {
        var event = migration.systemEvent();
        boolean deleted = event.map(value -> deleteAndPostEvent(id, value))
                               .orElseGet(() -> delete(id));
        if (!deleted) {
            logger().atWarning().log(() -> format(
                    "Could not delete an entity record of type `%s` with ID `%s`.",
                    entityStateType(), id));
        }
    }

    /**
     * Converts the passed entity into a record.
     */
    @VisibleForTesting
    RecordWithColumns<I, EntityRecord> toRecord(E entity) {
        var record = storageConverter().convert(entity);
        requireNonNull(record);
        var result = RecordWithColumns.create(record, recordStorage().recordSpec());
        return result;
    }

    /**
     * Converts the passed record into an entity.
     */
    @OverridingMethodsMustInvokeSuper
    protected E toEntity(EntityRecord record) {
        var result = storageConverter().reverse().convert(record);
        requireNonNull(result);
        return result;
    }

    private void checkEntityIsTransactional() {
        checkState(TransactionalEntity.class.isAssignableFrom(entityClass()),
                   "`%s` is not a transactional entity type. The requested operation is only " +
                           "supported for transactional entity types.",
                   entityClass().getCanonicalName()
        );
    }
}
