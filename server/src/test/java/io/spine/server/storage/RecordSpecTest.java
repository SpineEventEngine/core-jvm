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
import com.google.common.testing.NullPointerTester;
import io.spine.base.Identifier;
import io.spine.client.ArchivedColumn;
import io.spine.client.DeletedColumn;
import io.spine.client.VersionColumn;
import io.spine.core.Version;
import io.spine.protobuf.AnyPacker;
import io.spine.query.Column;
import io.spine.query.ColumnName;
import io.spine.query.EntityColumn;
import io.spine.query.RecordColumn;
import io.spine.server.entity.EntityRecord;
import io.spine.server.entity.LifecycleFlags;
import io.spine.server.entity.storage.EntityRecordColumn;
import io.spine.server.storage.given.GivenStorageProject.StgProjectColumns;
import io.spine.test.entity.TaskView;
import io.spine.test.storage.StgProject;
import io.spine.test.storage.StgProjectId;
import io.spine.testdata.Sample;
import io.spine.testing.core.given.GivenVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.truth.Truth.assertThat;
import static io.spine.server.storage.given.EntityRecordStorageTestEnv.declaredColumns;
import static io.spine.server.storage.given.EntityRecordStorageTestEnv.taskViewSpec;
import static io.spine.server.storage.given.GivenStorageProject.messageSpec;
import static io.spine.server.storage.given.GivenStorageProject.newId;
import static io.spine.server.storage.given.GivenStorageProject.newState;
import static io.spine.test.entity.TaskView.Column.dueDate;
import static io.spine.test.entity.TaskView.Column.estimateInDays;
import static io.spine.test.entity.TaskView.Column.name;
import static io.spine.test.entity.TaskView.Column.status;
import static io.spine.testing.DisplayNames.NOT_ACCEPT_NULLS;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("`RecordSpec` should")
@SuppressWarnings("DuplicateStringLiteralInspection")   /* Similar tests have similar names. */
class RecordSpecTest {

    @Test
    @DisplayName(NOT_ACCEPT_NULLS)
    void passNullToleranceCheck() {
        new NullPointerTester()
                .testAllPublicInstanceMethods(messageSpec());
    }

    @Nested
    @DisplayName("describe storage configuration of plain Proto message, and")
    class PlainMessage {

        @Test
        @DisplayName("obtain a column by name")
        void obtainByName() {
            var spec = messageSpec();
            StgProjectColumns.definitions()
                             .forEach(column -> assertColumn(spec, column));
        }

        @SuppressWarnings("OptionalGetWithoutIsPresent" /* Checked via an assertion. */)
        private void assertColumn(RecordSpec<StgProjectId, StgProject> spec,
                                  RecordColumn<StgProject, ?> column) {
            var found = spec.findColumn(column.name());
            assertThat(found).isPresent();
            assertThat(found.get()
                            .type()).isEqualTo(column.type());
        }

        @Test
        @DisplayName("return all definitions of the columns")
        void returnAllColumns() {
            var actualColumns = messageSpec().columns();
            assertThat(actualColumns).containsExactlyElementsIn(StgProjectColumns.definitions());
        }

        @Test
        @DisplayName("return ID value by the record")
        void returnIdValue() {
            var id = newId();
            var project = newState(id);
            var actual = messageSpec().idValueIn(project);
            assertThat(actual).isEqualTo(id);
        }
    }

    @Nested
    @DisplayName("describe storage configuration of `EntityRecord` containing Entity state, and")
    class WithEntityRecord {

        @Test
        @DisplayName("return the type of Entity state")
        void typeOfEntityState() {
            var actual = taskViewSpec().sourceType();
            assertThat(actual)
                    .isEqualTo(TaskView.class);
        }

        @Test
        @DisplayName("return all definitions of the columns")
        void allColumns() {
            var columns = taskViewSpec().columns();
            var actualNames = toNames(columns);

            var expected = ImmutableSet.<Column<?, ?>>builder()
                    .addAll(declaredColumns())
                    .add(ArchivedColumn.instance(),
                         DeletedColumn.instance(),
                         VersionColumn.instance())
                    .build();
            var expectedNames = toNames(expected);

            assertThat(actualNames).containsExactlyElementsIn(expectedNames);
        }

        private ImmutableSet<ColumnName> toNames(ImmutableSet<? extends Column<?, ?>> columns) {
            return columns.stream()
                    .map(Column::name)
                    .collect(toImmutableSet());
        }

        @Test
        @DisplayName("return the number of columns")
        void countColumns() {
            var lifecycleColumnCount = EntityRecordColumn.names()
                                                         .size();
            var protoColumnCount = 4; // See `task_view.proto`.

            var expectedSize = lifecycleColumnCount + protoColumnCount;
            assertThat(taskViewSpec().columnCount()).isEqualTo(expectedSize);
        }

        @Test
        @DisplayName("obtain a column by name")
        void obtainByName() {
            declaredColumns().forEach(this::testGetAndCompare);
        }

        private void testGetAndCompare(EntityColumn<TaskView, ?> column) {
            var columnName = column.name();
            var type = column.type();

            assertThat(taskViewSpec().get(columnName)
                                     .type()).isEqualTo(type);
        }

        @Test
        @DisplayName("search for a column by name")
        void searchByName() {
            var existingColumn = name();
            var column = taskViewSpec().findColumn(existingColumn.name());
            assertThat(column).isPresent();
        }

        @Test
        @DisplayName("throw `IAE` when the column with the specified name is not found")
        @SuppressWarnings("CheckReturnValue")
        void throwOnColumnNotFound() {
            var nonExistent = ColumnName.of("non-existent-column");

            assertThrows(IllegalArgumentException.class, () -> taskViewSpec().get(nonExistent));
        }

        @Test
        @DisplayName("return empty `Optional` when searching for a non-existent column")
        void emptyOptionalForMissingColumn() {
            var nonExistent = ColumnName.of("non-existent-column");
            var result = taskViewSpec().findColumn(nonExistent);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("extract values from `EntityRecord`")
        void extractColumnValues() {

            var state = Sample.messageOfType(TaskView.class);
            var archived = true;
            var deleted = false;
            var version = GivenVersion.withNumber(117);
            var record = newEntityRecord(state, archived, deleted, version);
            var spec = taskViewSpec();
            var values = spec.valuesIn(record);

            assertThat(values).containsExactly(
                    EntityRecordColumn.archived.name(), archived,
                    EntityRecordColumn.deleted.name(), deleted,
                    EntityRecordColumn.version.name(), version,
                    name().name(), state.getName(),
                    estimateInDays().name(), state.getEstimateInDays(),
                    status().name(), state.getStatus(),
                    dueDate().name(), state.getDueDate()
            );
            var actual = spec.idValueIn(record);
            assertThat(actual)
                    .isEqualTo(state.getId());
        }

        private EntityRecord newEntityRecord(TaskView state,
                                             boolean archived, boolean deleted,
                                             Version version) {
            var flags = LifecycleFlags.newBuilder()
                    .setArchived(archived)
                    .setDeleted(deleted)
                    .build();
            var record = io.spine.server.entity.EntityRecord.newBuilder()
                    .setEntityId(Identifier.pack(state.getId()))
                    .setState(AnyPacker.pack(state))
                    .setVersion(version)
                    .setLifecycleFlags(flags)
                    .build();
            return record;
        }
    }
}
