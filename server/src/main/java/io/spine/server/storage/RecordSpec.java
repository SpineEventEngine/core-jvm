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

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.Message;
import io.spine.annotation.Internal;
import io.spine.annotation.VisibleForTesting;
import io.spine.query.Column;
import io.spine.query.ColumnName;
import io.spine.query.RecordColumn;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static com.google.common.collect.Streams.stream;
import static io.spine.util.Exceptions.newIllegalArgumentException;

/**
 * Defines the specification of a record in a storage.
 *
 * <p>Defines the identifier column and the collection of the data columns to store along with
 * the message record for further querying. Each column defines a way to calculate the stored value
 * basing on the passed message.
 *
 * <p>In case an instance of {@code EntityRecord} is described by this spec,
 * a special set of accessors for ID and Entity state columns is set.
 * This is done via {@link io.spine.server.entity.storage.SpecScanner SpecScanner}
 * which perform analysis of Entity state, and creates getters for properties,
 * considering that an instance of {@code Any} (being {@code EntityRecord.state} field)
 * must be unpacked first.
 *
 * @param <I>
 *         the type of the record identifier
 * @param <R>
 *         the type of the stored record
 */
public final class RecordSpec<I, R extends Message> {

    /**
     * Type of stored record.
     */
    private final Class<R> recordType;

    /**
     * Type of origin Proto message, which served as a source
     * prior to potential transforming to a record of {@code recordType}.
     *
     * <p>If this {@code RecordSpec} describes a storage configuration
     * of {@code EntityRecord}, this field is a type of corresponding Entity state.
     *
     * <p>In all other cases so far, this value equals to {@code recordType}.
     */
    private final Class<? extends Message> sourceType;

    /**
     * Type of record identifier.
     */
    private final Class<I> idType;

    /**
     * A method object to extract the record identifier, once such a record is passed.
     */
    private final ExtractId<R, I> extractId;

    /**
     * The columns to store along with the record itself.
     */
    private final ImmutableMap<ColumnName, RecordColumn<R, ?>> columns;

    /**
     * Creates a new record specification listing the columns to store along with the record.
     *
     * @param idType
     *         the type of the record identifier
     * @param recordType
     *         the type of the record
     * @param sourceType
     *         the type of origin Proto message, which served as a source
     *         prior to potential transforming to a record of {@code recordType}
     * @param extractId
     *         a method object to extract the value of an identifier given an instance of a record
     * @param columns
     *         the definitions of the columns to store along with the record
     * @apiNote This ctor is internal to framework, and used to create a record
     *         specification for Entity states stored as {@code EntityRecord}s.
     */
    @Internal
    public RecordSpec(Class<I> idType,
                      Class<R> recordType,
                      Class<? extends Message> sourceType,
                      ExtractId<R, I> extractId,
                      Iterable<RecordColumn<R, ?>> columns) {
        this.idType = checkNotNull(idType);
        this.recordType = checkNotNull(recordType);
        this.sourceType = checkNotNull(sourceType);
        this.extractId = checkNotNull(extractId);
        checkNotNull(columns);
        this.columns =
                stream(columns).collect(
                        toImmutableMap(RecordColumn::name, (c) -> c)
                );
    }

    /**
     * Creates a new record specification listing the columns to store along with the record.
     *
     * @param idType
     *         the type of the record identifier
     * @param recordType
     *         the type of the record
     * @param extractId
     *         a method object to extract the value of an identifier given an instance of a record
     * @param columns
     *         the definitions of the columns to store along with the record
     */
    public RecordSpec(Class<I> idType,
                      Class<R> recordType,
                      ExtractId<R, I> extractId,
                      Iterable<RecordColumn<R, ?>> columns) {
        this(idType, recordType, recordType, extractId, columns);
    }

    /**
     * Creates a new record specification.
     *
     * <p>The specifications created implies that no columns are stored for the record.
     * To define the stored columns,
     * please use {@linkplain #RecordSpec(Class, Class, ExtractId, Iterable)
     * another ctor}.
     *
     * @param idType
     *         the type of the record identifier
     * @param recordType
     *         the type of the record
     * @param extractId
     *         a method object to extract the value of an identifier given an instance of a record
     */
    public RecordSpec(Class<I> idType, Class<R> recordType, ExtractId<R, I> extractId) {
        this(idType, recordType, extractId, ImmutableList.of());
    }

    /**
     * Returns the type of the stored record.
     */
    public Class<R> recordType() {
        return recordType;
    }

    /**
     * Returns the type of origin Proto message, which served as a source
     * prior to potential transforming to a record of {@linkplain #recordType() record type}.
     *
     * <p>In case if {@code recordType()} is {@code EntityRecord},
     * this method returns the type of Entity state.
     */
    public Class<? extends Message> sourceType() {
        return sourceType;
    }

    /**
     * Returns the type of the record identifiers.
     */
    public Class<I> idType() {
        return idType;
    }

    /**
     * Reads the identifier value of the record.
     *
     * @param source
     *         the object providing the ID value
     * @return the value of the identifier
     */
    public I idValueIn(R source) {
        checkNotNull(source);
        return extractId.apply(source);
    }

    /**
     * Returns the definitions of the record columns set by this specification.
     */
    public ImmutableSet<Column<?, ?>> columns() {
        return ImmutableSet.copyOf(columns.values());
    }

    /**
     * Returns the total number of columns in this specification.
     */
    @VisibleForTesting
    public int columnCount() {
        return columns.size();
    }

    /**
     * Reads the values of all columns specified for the record from the passed source.
     *
     * @param record
     *         the object from which the column values are read
     * @return {@code Map} of column names and their respective values
     */
    public Map<ColumnName, @Nullable Object> valuesIn(R record) {
        checkNotNull(record);
        Map<ColumnName, @Nullable Object> result = new HashMap<>();
        columns.forEach(
                (name, column) -> result.put(name, column.valueIn(record))
        );
        return result;
    }

    /**
     * Finds the column in this specification by the column name.
     *
     * @param name
     *         the name of the column to search for
     * @return the column wrapped into {@code Optional},
     *         or {@code Optional.empty()} if no column is found
     */
    public Optional<Column<?, ?>> findColumn(ColumnName name) {
        checkNotNull(name);
        var result = columns.get(name);
        return Optional.ofNullable(result);
    }

    /**
     * Finds the column in this specification by the column name.
     *
     * <p>Throws {@link IllegalArgumentException} if no such column exists.
     *
     * @param name
     *         the name of the column to search for
     * @return the column
     * @throws IllegalArgumentException
     *         if the column is not found
     */
    public Column<?, ?> get(ColumnName name) throws IllegalArgumentException {
        return findColumn(name)
                .orElseThrow(() -> newIllegalArgumentException(
                        "Cannot find the column `%s` in the record specification of type `%s`.",
                        name, recordType));
    }

    /**
     * A method object to extract the value of a record identifier given an instance of a record.
     *
     * <p>Once some storage is passed a record to store, the value of the record identifier has
     * to be determined. To avoid passing the ID value for each record, one defines a way
     * to obtain the identifier value from the record instance itself — by defining
     * an {@code ExtractId} as a part of the record specification for the storage.
     *
     * @param <R>
     *         the type of records from which to extract the ID value
     * @param <I>
     *         the type of the record identifiers to retrieve
     */
    @Immutable
    @FunctionalInterface
    public interface ExtractId<R extends Message, I> extends Function<R, I> {

        /**
         * {@inheritDoc}
         *
         * This method differs from its parent by the fact it never returns {@code null}.
         */
        @Override
        @CanIgnoreReturnValue
        I apply(@Nullable R input);
    }
}
