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

package io.spine.server.storage;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.FieldMask;
import com.google.protobuf.Message;
import io.spine.annotation.Internal;
import io.spine.client.ResponseFormat;
import io.spine.query.RecordQuery;
import io.spine.query.RecordQueryBuilder;
import io.spine.server.ContextSpec;

import java.util.Iterator;
import java.util.Optional;

/**
 * An abstract base for storage implementations, which store the Protobuf messages as records.
 *
 * <p>Each stored record must be identified.
 *
 * <p>Additionally, some attributes may be stored along with the record itself
 * to allow further querying.
 *
 * @param <I>
 *         the type of the record identifiers
 * @param <R>
 *         the type of the stored message records
 */
@SuppressWarnings("ClassWithTooManyMethods")    // This is a centerpiece.
public abstract class RecordStorage<I, R extends Message> extends AbstractStorage<I, R> {

    private final RecordSpec<I, R> recordSpec;

    /**
     * Creates the new storage instance.
     *
     * @param context
     *         specification of the Bounded Context in scope of which the storage will be used
     * @param recordSpec
     *         definitions of the columns to store along with each record
     */
    protected RecordStorage(ContextSpec context, RecordSpec<I, R> recordSpec) {
        super(context.isMultitenant());
        this.recordSpec = recordSpec;
    }

    /**
     * Reads the identifiers of the records selected by the passed query.
     *
     * @param query
     *         the query to execute
     * @return an iterator over the matching record identifiers
     */
    protected abstract Iterator<I> index(RecordQuery<I, R> query);

    /**
     * Writes the record along with its filled-in column values to the storage.
     *
     * @param record
     *         the record and additional columns with their values
     * @throws IllegalStateException
     *         if the storage was closed before
     */
    protected void write(RecordWithColumns<I, R> record) {
        checkNotClosed();
        writeRecord(record);
    }

    /**
     * Writes the batch of the records along with their filled-in columns to the storage.
     *
     * @param records
     *         records and their column values
     * @throws IllegalStateException
     *         if the storage was closed before
     */
    protected void writeAll(Iterable<? extends RecordWithColumns<I, R>> records) {
        checkNotClosed();
        writeAllRecords(records);
    }

    @Override
    public Optional<R> read(I id) {
        checkNotClosed();
        var query = toQuery(id);
        return readSingleRecord(query);
    }

    /**
     * Reads the message record by the passed identifier and applies the given field mask to it.
     *
     * @param id
     *         the identifier of the message record to read
     * @param mask
     *         the field mask to apply
     * @return the record with the given identifier, after the field mask has been applied to it,
     *         or {@code Optional.empty()} if no record is found by the ID
     * @throws IllegalStateException
     *         if the storage was closed before
     */
    protected Optional<R> read(I id, FieldMask mask) {
        checkNotClosed();
        var query = toQuery(id, mask);
        return readSingleRecord(query);
    }

    /**
     * Reads all message records in the storage.
     *
     * <p>This method should be used with the performance and memory considerations in mind.
     *
     * @return iterator over the records
     * @throws IllegalStateException
     *         if the storage was closed before
     */
    protected Iterator<R> readAll() {
        checkNotClosed();
        var query = queryForAll();
        return readAll(query);
    }

    /**
     * Reads all the message records according to the passed identifiers.
     *
     * <p>The response contains only the records which were found.
     *
     * @param ids
     *         the identifiers of the records to read
     * @return iterator over the records with the passed IDs
     * @throws IllegalStateException
     *         if the storage was closed before
     */
    protected Iterator<R> readAll(Iterable<I> ids) {
        checkNotClosed();
        var query = toQuery(ids);
        return readAll(query);
    }

    /**
     * Reads all the message records according to the passed record identifiers and returns
     * each record applying the passed field mask.
     *
     * <p>The response contains only the records which were found.
     *
     * @param ids
     *         the identifiers of the records to read
     * @param mask
     *         the mask to apply to each record
     * @return the iterator over the records
     * @throws IllegalStateException
     *         if the storage was closed before
     */
    protected Iterator<R> readAll(Iterable<I> ids, FieldMask mask) {
        checkNotClosed();
        var query = toQuery(ids, mask);
        return readAll(query);
    }

    /**
     * Reads all message records according to the passed query.
     *
     * <p>The default {@link ResponseFormat} is used.
     *
     * @param query
     *         the query to execute
     * @return iterator over the matching records
     * @throws IllegalStateException
     *         if the storage was closed before
     */
    protected Iterator<R> readAll(RecordQuery<I, R> query) {
        checkNotClosed();
        return readAllRecords(query);
    }

    /**
     * Physically deletes the message record from the storage by the record identifier.
     *
     * <p>In case the record with the specified identifier is not found in the storage,
     * this method does nothing and returns {@code false}.
     *
     * @param id
     *         identifier of the record to delete
     * @return {@code true} if the record was deleted,
     *         or {@code false} if the record with the specified identifier was not found
     * @throws IllegalStateException
     *         if the storage was closed before
     */
    @CanIgnoreReturnValue
    protected boolean delete(I id) {
        checkNotClosed();
        return deleteRecord(id);
    }

    /**
     * Deletes the batch of message records by their identifiers.
     *
     * <p>If for some provided identifiers there is no records in the storage, such identifiers
     * are silently skipped.
     *
     * @param ids
     *         identifiers of the records to delete
     * @throws IllegalStateException
     *         if the storage was closed before
     */
    protected void deleteAll(Iterable<I> ids) {
        for (var id : ids) {
            delete(id);
        }
    }

    /**
     * Creates a new query which targets the single record with the specified ID.
     */
    protected RecordQuery<I, R> toQuery(I id) {
        return queryBuilder().id().is(id).build();
    }

    /**
     * Creates a new query for the target with the specified ID, which, if exists, should be
     * returned according to the specified field mask.
     */
    protected RecordQuery<I, R> toQuery(I id, FieldMask mask) {
        return queryBuilder().id().is(id).withMask(mask).build();
    }

    /**
     * Creates a new query for the targets which have one of the passed identifiers.
     */
    protected RecordQuery<I, R> toQuery(Iterable<I> ids) {
        return queryBuilder().id().in(ids).build();
    }

    /**
     * Creates a new query for the targets which have one of the passed identifiers.
     *
     * <p>The results will contain only the fields specified by the given field mask.
     */
    protected RecordQuery<I, R> toQuery(Iterable<I> ids, FieldMask mask) {
        return queryBuilder().id().in(ids).withMask(mask).build();
    }

    /**
     * Creates a new query targeting all records in the storage.
     */
    protected RecordQuery<I, R> queryForAll() {
        return queryBuilder().build();
    }

    /**
     * Creates a new query builder for the records stored in this storage.
     */
    public RecordQueryBuilder<I, R> queryBuilder() {
        return RecordQuery.newBuilder(recordSpec.idType(), recordSpec().recordType());
    }

    /**
     * Performs writing the record and its column values to the storage.
     *
     * @param record
     *         the record and additional columns with their values
     */
    protected abstract void writeRecord(RecordWithColumns<I, R> record);

    /**
     * Performs writing of the record batch along with records' filled-in columns to the storage.
     *
     * @param records
     *         records and the values of their columns
     */
    @Internal
    protected abstract void writeAllRecords(Iterable<? extends RecordWithColumns<I, R>> records);

    /**
     * Performs reading of the message records by executing the passed query.
     *
     * @param query
     *         the query to execute
     * @return iterator over the matching message records
     */
    protected abstract Iterator<R> readAllRecords(RecordQuery<I, R> query);

    /**
     * Performs the physical removal of the message record from the storage
     * by the identifier of the record.
     *
     * <p>In case the record with the specified identifier is not found in the storage,
     * this method does nothing and returns {@code false}.
     *
     * @param id
     *         identifier of the record to delete
     * @return {@code true} if the record was deleted,
     *         or {@code false} if the record with the specified identifier was not found
     */
    @CanIgnoreReturnValue
    protected abstract boolean deleteRecord(I id);

    /**
     * Returns the specification of the record format, in which the message record should be stored.
     */
    @Internal
    protected RecordSpec<I, R> recordSpec() {
        return recordSpec;
    }

    private Optional<R> readSingleRecord(RecordQuery<I, R> query) {
        var iterator = readAll(query);
        return iterator.hasNext()
               ? Optional.of(iterator.next())
               : Optional.empty();
    }
}

