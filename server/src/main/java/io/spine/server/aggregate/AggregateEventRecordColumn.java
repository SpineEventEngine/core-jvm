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

package io.spine.server.aggregate;

import com.google.errorprone.annotations.Immutable;
import com.google.protobuf.Any;
import com.google.protobuf.Timestamp;
import io.spine.query.Column;
import io.spine.query.Columns;
import io.spine.query.RecordColumn;
import io.spine.query.RecordColumns;

import static io.spine.query.RecordColumn.create;

/**
 * Columns stored along with an {@link AggregateEventRecord}.
 */
@RecordColumns(ofType = AggregateEventRecord.class)
@SuppressWarnings({
        "DuplicateStringLiteralInspection"  /* Column names may repeat across records. */,
        "BadImport" /* `create` looks fine in this context. */,
        "WeakerAccess" /* This type is used in downstream Spine libraries. */})
public final class AggregateEventRecordColumn {

    /**
     * Stores the identifier of an aggregate.
     */
    public static final RecordColumn<AggregateEventRecord, Any>
            aggregate_id = create("aggregate_id", Any.class, AggregateEventRecord::getAggregateId);

    /**
     * Stores the time when the event record was created.
     */
    public static final RecordColumn<AggregateEventRecord, Timestamp>
            created = create("created", Timestamp.class, AggregateEventRecord::getTimestamp);

    /**
     * Stores the version of the record, either of the stored event, or the snapshot.
     */
    public static final RecordColumn<AggregateEventRecord, Integer>
            version = create("version", Integer.class, new GetVersion());

    /**
     * Stores {@code true} for the records which hold snapshots, {@code false} otherwise.
     */
    public static final RecordColumn<AggregateEventRecord, Boolean>
            snapshot = create("snapshot", Boolean.class, AggregateEventRecord::hasSnapshot);

    /**
     * Prevents this type from instantiation.
     *
     * <p>This class exists exclusively as a container of the column definitions. Thus, it isn't
     * expected to be instantiated at all. See the {@link RecordColumns} docs for more details on
     * this approach.
     */
    private AggregateEventRecordColumn() {
    }

    /**
     * Returns all the column definitions.
     */
    public static Columns<AggregateEventRecord> definitions() {
        return Columns.of(aggregate_id, created, version, snapshot);
    }

    /**
     * Obtains the version of {@link AggregateEventRecord}.
     */
    @Immutable
    private static final class GetVersion implements Column.Getter<AggregateEventRecord, Integer> {

        @Override
        public Integer apply(AggregateEventRecord record) {
            return record.hasEvent() ? versionOfEvent(record)
                                     : versionOfSnapshot(record);
        }

        private static int versionOfSnapshot(AggregateEventRecord rawRecord) {
            return rawRecord.getSnapshot()
                            .getVersion()
                            .getNumber();
        }

        private static int versionOfEvent(AggregateEventRecord rawRecord) {
            return rawRecord.getEvent()
                            .getContext()
                            .getVersion()
                            .getNumber();
        }
    }
}
