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

import com.google.common.collect.ImmutableSet;
import com.google.protobuf.Any;
import com.google.protobuf.Timestamp;
import io.spine.base.EntityState;
import io.spine.base.Identifier;
import io.spine.core.Version;
import io.spine.core.Versions;
import io.spine.query.EntityColumn;
import io.spine.server.entity.Entity;
import io.spine.server.entity.EntityRecord;
import io.spine.server.entity.HasLifecycleColumns;
import io.spine.server.entity.StorageConverter;
import io.spine.server.entity.TestTransaction;
import io.spine.server.entity.TransactionalEntity;
import io.spine.server.entity.storage.EntityRecordStorage;
import io.spine.server.entity.storage.SpecScanner;
import io.spine.server.entity.storage.given.TaskViewProjection;
import io.spine.server.storage.RecordSpec;
import io.spine.server.storage.RecordWithColumns;
import io.spine.test.entity.TaskView;
import io.spine.test.entity.TaskViewId;
import io.spine.test.storage.StgProject;
import io.spine.test.storage.StgProjectId;
import io.spine.testing.core.given.GivenVersion;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.truth.Truth.assertThat;
import static io.spine.protobuf.AnyPacker.pack;
import static io.spine.server.entity.TestTransaction.injectState;
import static io.spine.test.entity.TaskView.Column.dueDate;
import static io.spine.test.entity.TaskView.Column.estimateInDays;
import static io.spine.test.entity.TaskView.Column.name;
import static io.spine.test.entity.TaskView.Column.status;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class EntityRecordStorageTestEnv {

    /** Prevents instantiation of this utility class. */
    private EntityRecordStorageTestEnv() {
    }

    private static final RecordSpec<StgProjectId, EntityRecord> spec =
            SpecScanner.scan(TestCounterEntity.class);

    /**
     * Creates new instance of the test entity.
     */
    public static TestCounterEntity newEntity(StgProjectId id) {
        var entity = new TestCounterEntity(id);
        injectState(entity, StgProject.newBuilder()
                .setId(id)
                .build(), Versions.zero());
        return entity;
    }

    public static void archive(TransactionalEntity<StgProjectId, ?, ?> entity) {
        TestTransaction.archive(entity);
    }

    public static void delete(TransactionalEntity<StgProjectId, ?, ?> entity) {
        TestTransaction.delete(entity);
    }

    public static RecordWithColumns<StgProjectId, EntityRecord>
    withLifecycleColumns(EntityRecord record) {
        var result = RecordWithColumns.create(record, spec);
        return result;
    }

    public static List<RecordWithColumns<StgProjectId, EntityRecord>>
    recordsWithColumnsFrom(Map<StgProjectId, EntityRecord> recordMap) {
        return recordMap.values()
                .stream()
                .map(EntityRecordStorageTestEnv::withLifecycleColumns)
                .collect(toList());
    }

    public static void assertSingleRecord(EntityRecord expected, Iterator<EntityRecord> actual) {
        assertTrue(actual.hasNext());
        var singleRecord = actual.next();
        assertFalse(actual.hasNext());
        assertEquals(expected, singleRecord);
    }

    public static <E> void assertIteratorsEqual(Iterator<? extends E> first,
                                                Iterator<? extends E> second) {
        Collection<? extends E> firstCollection = newArrayList(first);
        Collection<? extends E> secondCollection = newArrayList(second);
        assertEquals(firstCollection.size(), secondCollection.size());
        assertThat(firstCollection).containsExactlyElementsIn(secondCollection);
    }

    public static EntityRecord newRecord(StgProjectId id, EntityState<StgProjectId> state) {
        var wrappedState = pack(state);
        var record = EntityRecord.newBuilder()
                .setEntityId(Identifier.pack(id))
                .setState(wrappedState)
                .setVersion(GivenVersion.withNumber(0))
                .build();
        return record;
    }

    public static RecordWithColumns<StgProjectId, EntityRecord>
    recordWithCols(Entity<StgProjectId, ?> entity, EntityRecord record) {
        return RecordWithColumns.create(record, SpecScanner.scan(entity));
    }

    public static RecordWithColumns<StgProjectId, EntityRecord>
    recordWithCols(Entity<StgProjectId, ?> entity) {
        var record = StorageConverter.toEntityRecord(entity)
                                     .build();
        return RecordWithColumns.create(record, SpecScanner.scan(entity));
    }

    public static void assertQueryHasSingleResult(
            StgProject.Query query,
            EntityRecord expected,
            EntityRecordStorage<StgProjectId, StgProject> storage) {
        var actual = storage.findAll(query);
        assertSingleRecord(expected, actual);
    }

    public static ImmutableSet<EntityColumn<TaskView, ?>> declaredColumns() {
        return ImmutableSet.of(name(), estimateInDays(), status(), dueDate());
    }

    public static RecordSpec<TaskViewId, EntityRecord> taskViewSpec() {
        return SpecScanner.scan(TaskViewProjection.class);
    }

    @SuppressWarnings("unused") // Reflective access
    public static class TestCounterEntity
            extends TransactionalEntity<StgProjectId, StgProject, StgProject.Builder>
            implements HasLifecycleColumns<StgProjectId, StgProject> {

        public static final Timestamp PROJECT_VERSION_TIMESTAMP =
                Timestamp.newBuilder()
                         .setSeconds(124565)
                         .setNanos(2434535)
                         .build();

        private int counter = 0;

        private TestCounterEntity(StgProjectId id) {
            super(id);
        }

        @Override
        protected void onBeforeCommit() {
            builder().setIdString(idAsString())
                     .setInternal(false)
                     .setWrappedState(Any.getDefaultInstance())
                     .setProjectStatusValue(builder().getStatusValue())
                     .setProjectVersion(versionWithCounter())
                     .setDueDate(dueDate());

        }

        private static Timestamp dueDate() {
            return Timestamp.newBuilder()
                    .setSeconds(42800)
                    .setNanos(140000)
                    .build();
        }

        private Version versionWithCounter() {
            return Version.newBuilder()
                    .setNumber(counter)
                    .setTimestamp(PROJECT_VERSION_TIMESTAMP)
                    .build();
        }

        public void assignStatus(StgProject.Status status) {
            var newState = StgProject.newBuilder(state())
                    .setId(id())
                    .setStatus(status)
                    .build();
            injectState(this, newState, state().getProjectVersion());
        }

        public void assignCounter(int counter) {
            this.counter = counter;
            // Manually inject state so the `project_version` storage field is populated.
            var newState = StgProject.newBuilder(state())
                    .setId(id())
                    .setProjectVersion(state().getProjectVersion())
                    .build();
            injectState(this, newState, state().getProjectVersion());
        }
    }
}
