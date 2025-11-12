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

import com.google.common.collect.ImmutableSet;
import io.spine.annotation.Internal;
import io.spine.annotation.VisibleForTesting;
import io.spine.client.ArchivedColumn;
import io.spine.client.DeletedColumn;
import io.spine.client.VersionColumn;
import io.spine.core.Version;
import io.spine.query.Column;
import io.spine.query.ColumnName;
import io.spine.query.RecordColumn;
import io.spine.query.RecordColumns;
import io.spine.server.entity.EntityRecord;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

/**
 * Defines the columns to store along with the {@link EntityRecord}.
 *
 * <p>Includes the columns for the {@code Entity} lifecycle and version.
 *
 * <p>Primary usage scenario of the definitions is a low-level querying of records
 * of {@code Entity} states.
 *
 * @implNote The columns are defined on top of the column types declared as a part of
 *         client-side query language. Since {@code Entity} and {@code EntityRecord} types
 *         are not available on the client, the enumeration of columns declared here is,
 *         in some way, a wiring between a generic declaration of the column
 *         (e.g. {@link ArchivedColumn}) and its {@code EntityRecord}-based counterpart
 *         (i.e. {@link EntityRecordColumn#archived EntityRecordColumn.archived}).
 */
@Internal
@RecordColumns(ofType = EntityRecord.class)
public final class EntityRecordColumn {

    /**
     * An {@link ArchivedColumn} which uses {@link EntityRecord} as a source for its values.
     */
    public static final RecordColumn<EntityRecord, Boolean> archived =
            AsEntityRecordColumn.apply(ArchivedColumn.instance(),
                                       Boolean.class,
                                       EntityRecord::isArchived);

    /**
     * A {@link DeletedColumn} which uses {@link EntityRecord} as a source for its values.
     */
    public static final RecordColumn<EntityRecord, Boolean> deleted =
            AsEntityRecordColumn.apply(DeletedColumn.instance(),
                                       Boolean.class,
                                       EntityRecord::isDeleted);

    /**
     * A {@link VersionColumn} which uses {@link EntityRecord} as a source for its values.
     */
    public static final RecordColumn<EntityRecord, Version> version =
            AsEntityRecordColumn.apply(VersionColumn.instance(),
                                       Version.class,
                                       EntityRecord::getVersion);

    /**
     * Set of all columns declared for {@code EntityRecord}.
     *
     * <p>Serves as a performance improvement to avoid creating extra collections.
     */
    private static final ImmutableSet<RecordColumn<EntityRecord, ?>> all =
            ImmutableSet.of(archived, deleted, version);

    /**
     * Prevents instantiation of this column holder type.
     */
    private EntityRecordColumn() {
    }

    /**
     * Returns the names of all columns defined for storing of {@link EntityRecord}.
     */
    @VisibleForTesting
    public static ImmutableSet<ColumnName> names() {
        return all.stream()
                  .map(Column::name)
                  .collect(toImmutableSet());
    }

    /**
     * Tells whether the passed column is either {@linkplain ArchivedColumn archived}
     * or {@linkplain DeletedColumn deleted}.
     */
    static boolean isLifecycleColumn(Column<?, ?> column) {
        checkNotNull(column);
        var name = column.name();
        return name.equals(archived.name()) || name.equals(deleted.name());
    }
}
