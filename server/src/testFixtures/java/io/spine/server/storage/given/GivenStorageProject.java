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

package io.spine.server.storage.given;

import com.google.protobuf.Any;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Durations;
import com.google.protobuf.util.Timestamps;
import io.spine.base.Identifier;
import io.spine.protobuf.AnyPacker;
import io.spine.query.Columns;
import io.spine.query.RecordColumn;
import io.spine.query.RecordColumns;
import io.spine.server.entity.EntityRecord;
import io.spine.server.entity.storage.SpecScanner;
import io.spine.server.storage.RecordSpec;
import io.spine.server.storage.RecordWithColumns;
import io.spine.test.storage.StgProject;
import io.spine.test.storage.StgProject.Status;
import io.spine.test.storage.StgProjectId;
import io.spine.test.storage.StgTask;
import io.spine.testing.core.given.GivenVersion;

import static io.spine.base.Identifier.newUuid;
import static io.spine.base.Time.currentTime;
import static java.lang.String.format;
import static java.lang.System.nanoTime;

public final class GivenStorageProject {

    private static final RecordSpec<StgProjectId, StgProject> messageSpec =
            new RecordSpec<>(StgProjectId.class,
                             StgProject.class,
                             StgProject::getId,
                             StgProjectColumns.definitions());

    private static final RecordSpec<StgProjectId, EntityRecord> entityRecordSpec =
            SpecScanner.scan(StgProjectId.class, StgProject.class);

    /**
     * Prevents this utility from instantiation.
     */
    private GivenStorageProject() {
    }

    /**
     * Creates a unique {@code EntityState} with the specified ID.
     *
     * <p>Two calls for the same ID should return messages, which are not equal, as the project
     * name is generated using the {@link System#nanoTime() System.nanoTime()}.
     *
     * <p>The status of the returned project is set to {@code CREATED}.
     *
     * <p>The due date for the returned project is set to one day ahead of the current time.
     *
     * @param id
     *         the ID for the message
     * @return the unique {@code EntityState}
     */
    public static StgProject newState(StgProjectId id) {
        var status = Status.CREATED;
        var dueDate = Timestamps.add(currentTime(), Durations.fromDays(1));
        return newState(id, status, dueDate);
    }

    /**
     * Creates an {@code StgProject} state with the specified ID, project status and due date.
     *
     * @param id
     *         the ID for the message
     * @param status
     *         the status for the created project
     * @param dueDate
     *         the due date of the project
     * @return a new instance of {@code StgProject}
     */
    public static StgProject newState(StgProjectId id, Status status, Timestamp dueDate) {
        var uniqueName = format("record-storage-test-%s-%s", id.getId(), nanoTime());
        var project = StgProject.newBuilder()
                .setId(id)
                .setStatus(status)
                .setName(uniqueName)
                .setDueDate(dueDate)
                .addTask(StgTask.getDefaultInstance())
                .build();
        return project;
    }

    /**
     * Generates new identifier of {@code StgProjectId} type.
     */
    public static StgProjectId newId() {
        return StgProjectId.newBuilder()
                .setId(newUuid())
                .build();
    }

    /**
     * Creates a randomly generated {@code StgProject}.
     *
     * @see #newState(StgProjectId)
     */
    public static StgProject newState() {
        var id = newId();
        return newState(id);
    }

    /**
     * Returns the record specification for {@code StgProject}.
     */
    public static RecordSpec<StgProjectId, StgProject> messageSpec() {
        return messageSpec;
    }

    /**
     * Generates a new {@code StgProject} as an Entity state,
     * and wraps it into a {@code RecordWithColumns}.
     */
    public static RecordWithColumns<StgProjectId, EntityRecord> newEntityRecordWithCols() {
        var project = newState();
        var record = EntityRecord.newBuilder()
                .setEntityId(Identifier.pack(project.getId()))
                .setVersion(GivenVersion.withNumber(7))
                .setState(AnyPacker.pack(project))
                .build();
        var result = RecordWithColumns.create(record, entityRecordSpec);
        return result;
    }

    /**
     * Generates a new {@code StgProject} and turns it
     * into a new {@code RecordWithColumns}, filling the columns
     * with the respective values, as per {@linkplain #messageSpec() specification}.
     */
    public static RecordWithColumns<StgProjectId, StgProject> withCols() {
        return withNoCols(newState());
    }

    /**
     * Transforms a passed project into a new {@code RecordWithColumns},
     * filling the columns with the respective values,
     * as per {@linkplain #messageSpec() specification}.
     */
    public static
    RecordWithColumns<StgProjectId, StgProject> withCols(StgProject project) {
        return RecordWithColumns.create(project, messageSpec());
    }

    /**
     * Transforms a passed project into a new {@code RecordWithColumns},
     * but not configuring any columns at all.
     */
    public static
    RecordWithColumns<StgProjectId, StgProject> withNoCols(StgProject project) {
        return RecordWithColumns.of(project.getId(), project);
    }

    /**
     * Columns of {@code StgProject} stored as record.
     */
    @RecordColumns(ofType = StgProject.class)
    public static final class StgProjectColumns {

        private StgProjectColumns() {
        }

        public static final RecordColumn<StgProject, String> name =
                RecordColumn.create("name", String.class, StgProject::getName);

        public static final RecordColumn<StgProject, Timestamp> due_date =
                RecordColumn.create("due_date", Timestamp.class, StgProject::getDueDate);

        public static final RecordColumn<StgProject, String>
                status = RecordColumn.create("status", String.class, (r) -> r.getStatus().name());

        public static final RecordColumn<StgProject, Any> state_as_any =
                RecordColumn.create("state_as_any", Any.class, AnyPacker::pack);

        public static final RecordColumn<StgProject, String> random_non_stored_column =
                RecordColumn.create("random_non_stored_column", String.class, (p) -> "31415926");

        /**
         * Returns all the column definitions.
         */
        public static Columns<StgProject> definitions() {
            return Columns.of(name, due_date, status, state_as_any);
        }
    }
}
