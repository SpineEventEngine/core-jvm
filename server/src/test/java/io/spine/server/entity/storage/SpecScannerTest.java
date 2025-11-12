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

import io.spine.server.entity.storage.given.TaskListViewProjection;
import io.spine.server.entity.storage.given.TaskViewProjection;
import io.spine.test.storage.StgProject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static io.spine.server.entity.storage.AssertColumns.assertContains;
import static io.spine.server.entity.storage.AssertColumns.assertHasLifecycleColumns;
import static io.spine.server.storage.given.EntityRecordStorageTestEnv.newEntity;
import static io.spine.server.storage.given.GivenStorageProject.newId;
import static io.spine.test.entity.TaskListView.Column.description;

@DisplayName("`SpecScanner` should")
class SpecScannerTest {

    @Nested
    @DisplayName("scan `Entity` state class")
    class ScanEntityState {
        @Test
        @DisplayName("and include `Entity` lifecycle columns into the scan results")
        void lifecycleCols() {
            var spec = SpecScanner.scan(TaskViewProjection.class);
            assertHasLifecycleColumns(spec);
        }

        @Test
        @DisplayName("extract the columns declared with `(column)` option in the Protobuf message")
        void stateCols() {
            var spec = SpecScanner.scan(TaskListViewProjection.class);
            var columns = spec.columns();
            assertContains(columns, description().name().value());
        }
    }

    @Nested
    @DisplayName("scan `Entity` class")
    class ScanEntityClass {
        @Test
        @DisplayName("and have `Entity` lifecycle columns among the scan results")
        void lifecycleCols() {
            var entity = newEntity(newId());
            var spec = SpecScanner.scan(entity);
            assertHasLifecycleColumns(spec);
        }

        @Test
        @DisplayName("extract the columns marked with `(column)` option in the Protobuf message")
        void stateCols() {
            var entity = newEntity(newId());
            var spec = SpecScanner.scan(entity);
            var columns = spec.columns();

            for (var expected : StgProject.Column.definitions()) {
                assertContains(columns, expected.name().value());
            }
        }
    }
}
