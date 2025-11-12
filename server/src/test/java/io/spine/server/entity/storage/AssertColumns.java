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
import io.spine.client.ArchivedColumn;
import io.spine.client.DeletedColumn;
import io.spine.client.VersionColumn;
import io.spine.query.Column;
import io.spine.query.ColumnName;
import io.spine.query.EntityColumn;
import io.spine.server.entity.EntityRecord;
import io.spine.server.storage.RecordSpec;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.truth.Truth.assertThat;

/**
 * An assertion utility for {@link EntityColumn} tests.
 */
final class AssertColumns {

    /**
     * Prevents this utility from instantiating.
     */
    private AssertColumns() {
    }

    static void assertContains(Iterable<? extends Column<?, ?>> columns, String columnName) {
        var expectedName = ColumnName.of(columnName);
        var contains = false;
        for (Column<?, ?> column : columns) {
            contains = contains || column.name().equals(expectedName);
        }
        assertThat(contains).isTrue();
    }

    static void assertHasLifecycleColumns(RecordSpec<?, EntityRecord> spec) {
        var columns = spec.columns();
        var names = columns.stream()
                .map(Column::name)
                .collect(toImmutableSet());

        assertThat(names).containsAtLeastElementsIn(
                ImmutableSet.of(
                        ArchivedColumn.instance().name(),
                        DeletedColumn.instance().name(),
                        VersionColumn.instance().name()
                )
        );
    }
}
