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

package io.spine.server.entity.storage;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.FieldMask;
import io.spine.annotation.Internal;
import io.spine.base.EntityState;
import io.spine.query.EntityQuery;
import io.spine.query.Query;
import io.spine.query.QueryPredicate;
import io.spine.query.RecordQuery;
import io.spine.query.Subject;
import io.spine.server.ContextSpec;
import io.spine.server.entity.Entity;
import io.spine.server.entity.EntityRecord;
import io.spine.server.storage.RecordSpec;
import io.spine.server.storage.RecordStorageDelegate;
import io.spine.server.storage.RecordWithColumns;
import io.spine.server.storage.StorageFactory;

import java.util.Iterator;

import static com.google.common.base.Preconditions.checkState;
import static io.spine.server.entity.model.EntityClass.stateClassOf;
import static io.spine.server.entity.storage.EntityRecordColumn.archived;
import static io.spine.server.entity.storage.EntityRecordColumn.deleted;
import static io.spine.server.entity.storage.EntityRecordColumn.isLifecycleColumn;
import static io.spine.server.entity.storage.ToEntityRecordQuery.transform;

/**
 * A storage for {@link EntityRecord}s.
 *
 * <p>Delegates all storage operations to the underlying
 * {@link io.spine.server.storage.RecordStorage RecordStorage}. For that, creates a new
 * {@code RecordStorage} instance through the provided {@code StorageFactory} and configures
 * it with the {@linkplain RecordSpec record specification} corresponding
 * to the stored {@code EntityRecord}.
 *
 * @param <I>
 *         the type of the identifiers of stored entities
 * @param <S>
 *         the type of {@code Entity} state
 */
public class EntityRecordStorage<I, S extends EntityState<I>>
        extends RecordStorageDelegate<I, EntityRecord> {

    /**
     * The query which aims to find only the records, which entity origins are neither archived
     * nor deleted.
     */
    private final RecordQuery<I, EntityRecord> findActiveRecordsQuery;

    private final Class<S> stateClass;

    /**
     * Creates a new instance.
     *
     * <p>This constructor takes the specification of the Bounded Context in which the storage
     * is created. It may be used by other framework libraries and SPI users to set properties
     * of an underlying DBMS (such as DB table names), with which this record storage interacts.
     *
     * @param context
     *         specification of the Bounded Context in which the created storage will be used
     * @param factory
     *         storage factory
     * @param entityClass
     *         class of an Entity which data is stored in the created storage
     */
    public EntityRecordStorage(ContextSpec context,
                               StorageFactory factory,
                               Class<? extends Entity<I, S>> entityClass) {
        super(context, factory.createRecordStorage(context, SpecScanner.scan(entityClass)));
        this.findActiveRecordsQuery = findActiveRecords();
        this.stateClass = stateClassOf(entityClass);
    }

    /**
     * Returns the type of the state for the entities, which records are stored.
     */
    public final Class<S> stateClass() {
        return stateClass;
    }

    /**
     * Returns the iterator over all active entity records.
     *
     * @throws IllegalStateException
     *         if the storage is already closed
     */
    @Override
    public Iterator<I> index() {
        return index(findActiveRecordsQuery);
    }

    /**
     * Returns the iterator over identifiers of the records which match the passed query.
     *
     * <p>Unless the query specifies otherwise, only non-archived and non-deleted entity records
     * are queried.
     *
     * @throws IllegalStateException
     *         if the storage is already closed
     */
    public Iterator<I> index(EntityQuery<I, S, ?> query) {
        var recordQuery = transform(query);
        return index(recordQuery);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns the records for both active and non-active entities.
     */
    @Override
    public Iterator<EntityRecord> readAll(Iterable<I> ids, FieldMask mask) {
        return super.readAll(ids, mask);
    }

    /**
     * Reads the entity records according to the passed query.
     *
     * <p>If the passed query has no ID parameter set, only the records of the active entities
     * are returned. If the query includes the ID parameter, includes non-active entities as well.
     */
    @Override
    public Iterator<EntityRecord> readAll(RecordQuery<I, EntityRecord> query) {
        var toExecute = onlyActive(query);
        return super.readAll(toExecute);
    }

    /**
     * Returns the iterator over all stored non-archived and non-deleted entity records.
     *
     * @throws IllegalStateException
     *         if the storage is already closed
     */
    @Override
    public Iterator<EntityRecord> readAll() {
        return super.readAll(findActiveRecordsQuery);
    }

    /**
     * Finds all records which match the given query.
     *
     * <p>Only the records of active entities are returned.
     */
    public final Iterator<EntityRecord> findAll(EntityQuery<I, S, ?> query) {
        var result = transform(query);
        return readAll(result);
    }

    /**
     * Reads the entity records by the passed record identifiers.
     *
     * <p>The results include the records of both active and non-active entities.
     *
     * @param ids
     *         identifiers of the records of interest
     * @return iterator over the matching entity records
     * @throws IllegalStateException
     *         if the storage is already closed
     */
    @Override
    public Iterator<EntityRecord> readAll(Iterable<I> ids) {
        return super.readAll(ids);
    }

    /**
     * Writes the given entity record under the given identifier.
     *
     * <p>Completely ignores the entity columns. Use {@link #write(RecordWithColumns)
     * write(RecordWithColumns)} to write the value with the columns.
     *
     * @param id
     *         the ID for the record
     * @param record
     *         the record to store
     * @throws IllegalStateException
     *         if the storage is already closed
     */
    @Override
    public synchronized void write(I id, EntityRecord record) {
        var wrapped = RecordWithColumns.create(record, recordSpec());
        checkState(id.equals(wrapped.id()),
                   "Identifier `%s` passed into `write(id, record)` " +
                           "does not match the one in the record (`%s`).",
                   id, wrapped.id());
        write(wrapped);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Overrides to expose as a part of the public API.
     */
    @Override
    public void write(RecordWithColumns<I, EntityRecord> record) {
        super.write(record);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Overrides to expose as a part of the public API.
     */
    @Override
    public void writeAll(Iterable<? extends RecordWithColumns<I, EntityRecord>> records) {
        super.writeAll(records);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Overrides to expose as a part of the public API.
     */
    @Override
    @CanIgnoreReturnValue
    public boolean delete(I id) {
        return super.delete(id);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Overrides the parent method in sake of the covariance of the returned value.
     */
    @Internal
    @Override
    public final RecordSpec<I, EntityRecord> recordSpec() {
        return super.recordSpec();
    }

    private Class<I> idType() {
        return recordSpec().idType();
    }

    private static boolean hasNoIds(Query<?, ?> query) {
        return query.subject()
                    .id()
                    .values()
                    .isEmpty();
    }

    private RecordQuery<I, EntityRecord> onlyActive(RecordQuery<I, EntityRecord> query) {
        var result = query;
        if (hasNoIds(query) && hasNoLifecycleCols(query)) {
            result = query.and((r) -> r.where(archived).is(false)
                                       .where(deleted).is(false));
        }
        return result;
    }

    private static <I> boolean hasNoLifecycleCols(Query<I, EntityRecord> query) {
        Subject<I, ?> subject = query.subject();
        QueryPredicate<?> predicate = subject.predicate();
        var result = !hasLifecycleColumn(predicate);
        return result;
    }

    private static boolean hasLifecycleColumn(QueryPredicate<?> predicate) {
        var params = predicate.allParams();
        var result = params.stream()
                .anyMatch((param) -> isLifecycleColumn(param.column()));
        if (!result) {
            ImmutableList<? extends QueryPredicate<?>> children = predicate.children();
            for (QueryPredicate<?> child : children) {
                var childResult = hasLifecycleColumn(child);
                if (childResult) {
                    return true;
                }
            }
        }
        return result;
    }

    private RecordQuery<I, EntityRecord> findActiveRecords() {
        var result = RecordQuery.newBuilder(idType(), EntityRecord.class)
                .where(archived).is(false)
                .where(deleted).is(false)
                .build();
        return result;
    }
}
