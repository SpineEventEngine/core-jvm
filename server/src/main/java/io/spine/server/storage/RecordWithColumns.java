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

import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Message;
import io.spine.query.ColumnName;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.util.Exceptions.newIllegalStateException;

/**
 * A value of some message record along with the values
 * of its {@linkplain io.spine.query.Column columns}.
 *
 * @param <I>
 *         the type of the record identifiers
 * @param <R>
 *         the type of the stored records
 */
public final class RecordWithColumns<I, R extends Message> {

    private final I id;
    private final R record;

    /**
     * A map of column names to the corresponding column values.
     *
     * @implNote It's impossible to use an {@link com.google.common.collect.ImmutableMap
     *         ImmutableMap}, as the values may contain {@code null}s.
     */
    private final Map<ColumnName, @Nullable Object> storageFields;

    private RecordWithColumns(I identifier, R record, Map<ColumnName, Object> storageFields) {
        this.id = checkNotNull(identifier);
        this.record = checkNotNull(record);
        this.storageFields = new HashMap<>(storageFields);
    }

    /**
     * Creates a new record extracting the column values from the passed record,
     * and setting the passed identifier value as the record identifier.
     */
    public static <I, R extends Message>
    RecordWithColumns<I, R> create(I identifier, R record, RecordSpec<I, R> recordSpec) {
        checkNotNull(identifier);
        checkNotNull(record);
        checkNotNull(recordSpec);
        var storageFields = recordSpec.valuesIn(record);
        return of(identifier, record, storageFields);
    }

    /**
     * Creates a new record-with-columns,
     * filling the column values by extracting them from the passed record.
     */
    public static <I, R extends Message>
    RecordWithColumns<I, R> create(R record, RecordSpec<I, R> recordSpec) {
        checkNotNull(record);
        checkNotNull(recordSpec);
        var storageFields = recordSpec.valuesIn(record);
        var identifier = recordSpec.idValueIn(record);
        return of(identifier, record, storageFields);
    }

    /**
     * Wraps a passed record.
     *
     * <p>Such instance of {@code RecordWithColumns} will contain no storage fields.
     */
    public static <I, R extends Message> RecordWithColumns<I, R> of(I id, R record) {
        return new RecordWithColumns<>(id, record, Collections.emptyMap());
    }

    /**
     * Creates a new instance from the passed record and storage fields.
     */
    private static <I, R extends Message>
    RecordWithColumns<I, R> of(I identifier, R record, Map<ColumnName, Object> storageFields) {
        return new RecordWithColumns<>(identifier, record, storageFields);
    }

    /**
     * Returns the identifier of the record.
     */
    public I id() {
        return id;
    }

    /**
     * Returns the message of the record.
     */
    public R record() {
        return record;
    }

    /**
     * Obtains the names of storage fields in the record.
     *
     * @return the storage field names
     */
    public ImmutableSet<ColumnName> columnNames() {
        return ImmutableSet.copyOf(storageFields.keySet());
    }

    /**
     * Obtains the value of the storage field by the specified column name.
     *
     * <p>The {@linkplain DefaultColumnMapping default column mapping} will be used. It is suitable
     * for storages that store values "as-is" or that are willing to do the manual column value
     * conversion.
     *
     * <p>In other cases consider implementing a custom {@link ColumnMapping} and using the
     * {@link #columnValue(ColumnName, ColumnMapping)} overload for convenient column value
     * conversion.
     *
     * @param columnName
     *         the column name
     * @return the storage field value
     * @throws IllegalStateException
     *         if there is no column with the specified name
     */
    public @Nullable Object columnValue(ColumnName columnName) {
        return columnValue(columnName, DefaultColumnMapping.INSTANCE);
    }

    /**
     * Obtains the value of the storage field by the specified column name.
     *
     * <p>The specified column mapping will be used to do the column value conversion.
     */
    public <V> V columnValue(ColumnName columnName, ColumnMapping<V> columnMapping) {
        checkNotNull(columnName);
        checkNotNull(columnMapping);
        if (!storageFields.containsKey(columnName)) {
            throw newIllegalStateException("Column with the name `%s` was not found.",
                                           columnName);
        }
        var columnValue = storageFields.get(columnName);
        if (columnValue == null) {
            var result = columnMapping.ofNull()
                                      .apply(null);
            return result;
        }
        var result = columnMapping.of(columnValue.getClass())
                                  .applyTo(columnValue);
        return result;
    }

    /**
     * Tells if there are any {@linkplain io.spine.query.Column columns}
     * associated with this record.
     */
    public boolean hasColumns() {
        return !storageFields.isEmpty();
    }

    /**
     * Determines if there is a column with the specified name among the storage fields.
     */
    public boolean hasColumn(ColumnName name) {
        var result = storageFields.containsKey(name);
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RecordWithColumns)) {
            return false;
        }
        var columns = (RecordWithColumns<?, ?>) o;
        return Objects.equals(id, columns.id) &&
                Objects.equals(record, columns.record) &&
                Objects.equals(storageFields, columns.storageFields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, record, storageFields);
    }
}
