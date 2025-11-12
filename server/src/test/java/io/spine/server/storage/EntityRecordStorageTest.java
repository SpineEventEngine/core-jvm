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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.FieldMask;
import io.spine.base.EntityState;
import io.spine.base.Identifier;
import io.spine.client.ArchivedColumn;
import io.spine.client.DeletedColumn;
import io.spine.core.Version;
import io.spine.server.ServerEnvironment;
import io.spine.server.entity.Entity;
import io.spine.server.entity.EntityRecord;
import io.spine.server.entity.TransactionalEntity;
import io.spine.server.entity.storage.EntityRecordStorage;
import io.spine.server.storage.given.EntityRecordStorageTestEnv.TestCounterEntity;
import io.spine.server.storage.given.GivenStorageProject;
import io.spine.test.storage.StgProject;
import io.spine.test.storage.StgProjectId;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Sets.newHashSet;
import static com.google.common.truth.Truth.assertThat;
import static com.google.protobuf.util.FieldMaskUtil.fromFieldNumbers;
import static io.spine.protobuf.AnyPacker.unpack;
import static io.spine.protobuf.Messages.isDefault;
import static io.spine.server.ContextSpec.singleTenant;
import static io.spine.server.storage.given.EntityRecordStorageTestEnv.TestCounterEntity.PROJECT_VERSION_TIMESTAMP;
import static io.spine.server.storage.given.EntityRecordStorageTestEnv.archive;
import static io.spine.server.storage.given.EntityRecordStorageTestEnv.assertIteratorsEqual;
import static io.spine.server.storage.given.EntityRecordStorageTestEnv.assertQueryHasSingleResult;
import static io.spine.server.storage.given.EntityRecordStorageTestEnv.assertSingleRecord;
import static io.spine.server.storage.given.EntityRecordStorageTestEnv.delete;
import static io.spine.server.storage.given.EntityRecordStorageTestEnv.newEntity;
import static io.spine.server.storage.given.EntityRecordStorageTestEnv.newRecord;
import static io.spine.server.storage.given.EntityRecordStorageTestEnv.recordWithCols;
import static io.spine.server.storage.given.EntityRecordStorageTestEnv.recordsWithColumnsFrom;
import static io.spine.server.storage.given.GivenStorageProject.newEntityRecordWithCols;
import static io.spine.server.storage.given.GivenStorageProject.newState;
import static io.spine.test.storage.StgProject.Status.CANCELLED;
import static io.spine.test.storage.StgProject.Status.CANCELLED_VALUE;
import static io.spine.test.storage.StgProject.Status.DONE;
import static io.spine.test.storage.StgProject.Status.DONE_VALUE;
import static io.spine.testing.Assertions.assertMatchesMask;
import static io.spine.testing.TestValues.nullRef;
import static java.util.stream.Collectors.toList;
import static java.util.stream.IntStream.range;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests the storage which store data as {@link EntityRecord}s.
 */
@DisplayName("`EntityRecordStorage` should")
public class EntityRecordStorageTest
        extends AbstractStorageTest<StgProjectId,
                                    EntityRecord,
                                    EntityRecordStorage<StgProjectId, StgProject>> {

    @Override
    protected EntityRecordStorage<StgProjectId, StgProject> newStorage() {
        var factory = ServerEnvironment.instance()
                                       .storageFactory();
        var ctxSpec = singleTenant(EntityRecordStorageTest.class.getName());
        return new EntityRecordStorage<>(ctxSpec, factory, TestCounterEntity.class);
    }

    @Override
    protected EntityRecord newStorageRecord(StgProjectId id) {
        return newRecord(id, newState(id));
    }

    @Override
    protected final StgProjectId newId() {
        return GivenStorageProject.newId();
    }

    @Test
    @DisplayName("retrieve empty iterator if storage is empty")
    void retrieveEmptyIterator() {
        var nonEmptyFieldMask = FieldMask.newBuilder()
                .addPaths("invalid-path")
                .build();
        EntityRecordStorage<StgProjectId, ?> storage = storage();
        var query = storage.queryBuilder()
                           .withMask(nonEmptyFieldMask)
                           .build();
        Iterator<?> empty = storage.readAll(query);

        assertNotNull(empty);
        assertFalse(empty.hasNext(), "Iterator is not empty!");
    }

    @Test
    @DisplayName("delete record")
    void deleteRecord() {
        var storage = storage();
        var id = newId();
        var record = newStorageRecord(id);

        // Write the record.
        storage.write(id, record);

        // Delete the record.
        assertTrue(storage.delete(id));

        // There's no record with such ID.
        assertFalse(storage.read(id)
                           .isPresent());
    }

    @Test
    @DisplayName("create unique states for same ID")
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection" /* Storing of generated objects and
                                                                checking via #contains(Object). */)
    void createUniqueStatesForSameId() {
        var checkCount = 10;
        var id = newId();
        Set<EntityState<StgProjectId>> states = newHashSet();
        for (var i = 0; i < checkCount; i++) {
            EntityState<StgProjectId> newState = newState(id);
            if (states.contains(newState)) {
                fail("`RecordStorageTest.newState()` should return unique messages.");
            }
        }
    }

    @Nested
    @DisplayName("read")
    class Read {

        @Test
        @DisplayName("single record according to the specific field mask")
        void singleRecord() {
            var id = newId();
            var record = newStorageRecord(id);
            var storage = storage();
            storage.write(id, record);

            EntityState<StgProjectId> state = newState(id);
            var idMask = fromFieldNumbers(state.getClass(), 1);

            var optional = storage.read(id, idMask);
            assertTrue(optional.isPresent());
            var entityRecord = optional.get();

            var unpacked = unpack(entityRecord.getState());
            assertFalse(isDefault(unpacked));
        }

        @Test
        @DisplayName("multiple records according to the given field mask")
        void multipleRecords() {
            var storage = storage();
            var recordCount = 10;
            var ids =
                    range(0, recordCount).mapToObj(i -> newId())
                                         .collect(toImmutableList());
            var stateClass = writeRandomRecords(storage, ids);

            var halfSize = recordCount / 2;
            List<StgProjectId> halfOfIds = ids.subList(0, halfSize);

            var fieldMask = fromFieldNumbers(stateClass, 2);
            var iterator = storage.readAll(halfOfIds, fieldMask);
            List<EntityRecord> actualRecords = ImmutableList.copyOf(iterator);

            assertThat(actualRecords).hasSize(halfSize);
            for (var record : actualRecords) {
                var state = unpack(record.getState());
                assertMatchesMask(state, fieldMask);
            }
        }

        @SuppressWarnings("rawtypes")
        private Class<? extends EntityState>
        writeRandomRecords(EntityRecordStorage<StgProjectId, StgProject> storage,
                           List<StgProjectId> ids) {
            Class<? extends EntityState> stateClass = null;
            for (var id : ids) {
                EntityState<StgProjectId> state = newState(id);
                if (stateClass == null) {
                    stateClass = state.getClass();
                }
                var record = newRecord(id, state);
                storage.write(id, record);
            }
            return stateClass;
        }

        @Test
        @DisplayName("archived records if specified")
        void archivedRecords() {
            var activeRecordId = newId();
            var archivedRecordId = newId();

            TransactionalEntity<StgProjectId, ?, ?> activeEntity = newEntity(activeRecordId);
            TransactionalEntity<StgProjectId, ?, ?> archivedEntity = newEntity(archivedRecordId);

            archive(archivedEntity);

            var storage = storage();

            storage.write(recordWithCols(activeEntity));
            var archivedWithCols = recordWithCols(archivedEntity);
            storage.write(archivedWithCols);

            var query = StgProject.query().where(ArchivedColumn.is(), true).build();
            assertQueryHasSingleResult(query, archivedWithCols.record(), storage);
        }
    }

    @Nested
    @DisplayName("filter records")
    class Filtering {

        @Test
        @DisplayName("by columns")
        void byColumns() {
            var done = DONE;
            var storage = storage();

            var idMatching = newId();
            var idWrong1 = newId();
            var idWrong2 = newId();

            var matchingEntity = newEntity(idMatching);
            var wrong1 = newEntity(idWrong1);
            var wrong2 = newEntity(idWrong2);

            // 2 of 3 have required values

            matchingEntity.assignStatus(done);
            wrong1.assignStatus(done);
            wrong2.assignStatus(CANCELLED);

            // Change internal Entity state
            wrong1.assignCounter(1);

            // After the mutation above the single matching record is the one
            // under the `idMatching` ID
            var fineRecord = writeRecord(storage, matchingEntity);
            writeRecord(storage, wrong1);
            writeRecord(storage, wrong2);

            var query = StgProject.query()
                                  .projectStatusValue().is(DONE_VALUE)
                                  .projectVersion().is(projectVersion())
                                  .build();
            assertQueryHasSingleResult(query, fineRecord, storage);
        }

        @CanIgnoreReturnValue
        private EntityRecord writeRecord(EntityRecordStorage<StgProjectId, StgProject> storage,
                                         TestCounterEntity entity) {
            var withCols = recordWithCols(entity);
            storage.write(withCols);
            return withCols.record();
        }

        private Version projectVersion() {
            var versionValue = Version.newBuilder()
                    .setNumber(0)
                    .setTimestamp(PROJECT_VERSION_TIMESTAMP)
                    .build();
            return versionValue;
        }

        @Test
        @DisplayName("both by columns and IDs")
        void byColumnsAndId() {
            var targetId = newId();
            var targetEntity = newEntity(targetId);
            var noMatchEntity = newEntity(newId());
            var noMatchIdEntity = newEntity(newId());
            var deletedEntity = newEntity(newId());

            targetEntity.assignStatus(CANCELLED);
            deletedEntity.assignStatus(CANCELLED);

            delete(deletedEntity);

            noMatchIdEntity.assignStatus(CANCELLED);

            noMatchEntity.assignStatus(DONE);

            write(targetEntity);
            write(noMatchEntity);
            write(noMatchIdEntity);
            write(deletedEntity);

            var storage = storage();

            var query = StgProject.query()
                                  .id().is(targetId)
                                  .projectStatusValue().is(CANCELLED_VALUE)
                                  .where(ArchivedColumn.is(), false)
                                  .where(DeletedColumn.is(), false)
                                  .build();

            var read = storage.findAll(query);
            List<EntityRecord> readRecords = newArrayList(read);
            assertEquals(1, readRecords.size());
            var readRecord = readRecords.get(0);
            assertEquals(targetEntity.state(), unpack(readRecord.getState()));
            assertEquals(targetId, Identifier.unpack(readRecord.getEntityId()));
        }

        @Test
        @DisplayName("by ID and without using the columns")
        void byIdAndNoColumns() {
            var matchingId = newId();

            var storage = storage();
            var matchingRecord = writeRecord(matchingId, storage);
            writeRecord(newId(), storage);
            writeRecord(newId(), storage);

            var query = StgProject.query()
                                  .id().is(matchingId)
                                  .build();
            assertQueryHasSingleResult(query, matchingRecord, storage);
        }

        @CanIgnoreReturnValue
        private EntityRecord writeRecord(StgProjectId id,
                                         EntityRecordStorage<StgProjectId, StgProject> storage) {
            Entity<StgProjectId, ?> entity = newEntity(id);
            var withCols = recordWithCols(entity);
            storage.write(withCols);
            return withCols.record();
        }

        @Test
        @DisplayName("excluding inactive records by default")
        void emptyQueryExcludesInactive() {
            var storage = storage();
            var activeId = newId();

            var activeRecord = writeRecord(activeId, storage);
            writeRecordAndArchive(newId(), storage);
            writeRecordAndDelete(newId(), storage);

            var read = storage.readAll();
            assertSingleRecord(activeRecord, read);
        }

        @CanIgnoreReturnValue
        private EntityRecord writeRecordAndDelete(
                StgProjectId deletedId, EntityRecordStorage<StgProjectId, StgProject> storage) {
            var deletedEntity = newEntity(deletedId);
            delete(deletedEntity);
            var deletedWithCols = recordWithCols(deletedEntity);
            storage.write(deletedWithCols);
            return deletedWithCols.record();
        }

        @CanIgnoreReturnValue
        private EntityRecord writeRecordAndArchive
                (StgProjectId archivedId, EntityRecordStorage<StgProjectId, StgProject> storage) {
            var archivedEntity = newEntity(archivedId);
            archive(archivedEntity);
            var recordWithCols = recordWithCols(archivedEntity);
            storage.write(recordWithCols);
            return recordWithCols.record();
        }

        @Test
        @DisplayName("including inactive records when reading " +
                "by a query with explicit `archived` and `deleted` flags set to true")
        void explicitFlags() {
            var storage = storage();
            // Writing the active record.
            writeRecord(newId(), storage);
            var expectedArchived = writeRecordAndArchive(newId(), storage);
            var expectedDeleted = writeRecordAndDelete(newId(), storage);

            var queryArchived = StgProject.query().where(ArchivedColumn.is(), true).build();
            assertQueryHasSingleResult(queryArchived, expectedArchived, storage);

            var queryDeleted = StgProject.query().where(DeletedColumn.is(), true).build();
            assertQueryHasSingleResult(queryDeleted, expectedDeleted, storage);
        }

        @Test
        @DisplayName("including inactive records on bulk read by IDs by default")
        void filterByIdByDefaultInBulk() {
            var activeId = newId();
            var archivedId = newId();
            var deletedId = newId();

            var storage = storage();
            var activeRecord = writeRecord(activeId, storage);
            var archivedRecord = writeRecordAndArchive(archivedId, storage);
            var deletedRecord = writeRecordAndDelete(deletedId, storage);

            var query = StgProject.query()
                                  .id().in(activeId, archivedId, deletedId)
                                  .build();
            var actual = storage.findAll(query);

            assertThat(ImmutableSet.copyOf(actual))
                    .containsExactly(activeRecord, archivedRecord, deletedRecord);
        }

        private void write(Entity<StgProjectId, ?> entity) {
            var storage = storage();
            storage.write(recordWithCols(entity));
        }
    }

    @Nested
    @DisplayName("write")
    class Write {

        @Test
        @DisplayName("a record with no custom columns")
        void withoutColumns() {
            var storage = storage();
            var id = newId();
            var expected = newStorageRecord(id);
            storage.write(id, expected);

            var optional = storage.read(id);
            assertTrue(optional.isPresent());
            var actual = optional.get();

            assertEquals(expected, actual);
            close(storage);
        }

        @Test
        @DisplayName("a record with custom columns")
        void withColumns() {
            var id = newId();
            Entity<StgProjectId, ?> testEntity = newEntity(id);
            var storage = storage();
            var withCols = recordWithCols(testEntity);
            storage.write(withCols);

            var readRecord = storage.read(id);
            assertTrue(readRecord.isPresent());
            assertEquals(withCols.record(), readRecord.get());
        }

        @Test
        @DisplayName("several records which did not exist in storage")
        void severalNewRecords() {
            var storage = storage();
            var bulkSize = 5;

            Map<StgProjectId, RecordWithColumns<StgProjectId, EntityRecord>> initial =
                    new HashMap<>(bulkSize);

            for (var i = 0; i < bulkSize; i++) {
                var record = newEntityRecordWithCols();
                initial.put(record.id(), record);
            }
            storage.writeAll(initial.values());

            var records = storage.readAll(initial.keySet());
            Collection<@Nullable EntityRecord> actual = newArrayList(records);

            Collection<@Nullable EntityRecord> expected =
                    initial.values()
                           .stream()
                           .map(recordWithColumns -> recordWithColumns != null
                                                     ? recordWithColumns.record()
                                                     : nullRef())
                           .collect(toList());
            assertThat(actual).containsExactlyElementsIn(expected);

            close(storage);
        }

        @Test
        @DisplayName("several records which previously existed in storage and rewrite them")
        void rewritingExisting() {
            var recordCount = 3;
            var storage = storage();

            Map<StgProjectId, EntityRecord> v1Records = new HashMap<>(recordCount);
            Map<StgProjectId, EntityRecord> v2Records = new HashMap<>(recordCount);

            for (var i = 0; i < recordCount; i++) {
                var id = newId();
                var record = newStorageRecord(id);

                // Some records are changed and some are not.
                var alternateRecord = (i % 2 == 0)
                                               ? record
                                               : newStorageRecord(id);
                v1Records.put(id, record);
                v2Records.put(id, alternateRecord);
            }

            storage.writeAll(recordsWithColumnsFrom(v1Records));
            var firstRevision = storage.readAll();
            assertIteratorsEqual(v1Records.values()
                                          .iterator(), firstRevision);
            storage.writeAll(recordsWithColumnsFrom(v2Records));
            var secondRevision = storage.readAll();
            assertIteratorsEqual(v2Records.values()
                                          .iterator(), secondRevision);
        }

        @Test
        @DisplayName("a record and update its column values")
        void updateColumnValues() {
            var storage = storage();
            var id = newId();
            var entity = newEntity(id);

            // Write with `DONE` status at first.
            var initialStatus = DONE;
            var record = writeWithStatus(entity, initialStatus, storage);
            var query =
                    StgProject.query()
                              .projectStatusValue().is(initialStatus.getNumber())
                              .build();
            var recordsBefore = storage.findAll(query);
            assertSingleRecord(record, recordsBefore);

            // Update the column status with `CANCELLED` value.
            var statusAfterUpdate = CANCELLED;
            writeWithStatus(entity, statusAfterUpdate, storage);

            var recordsAfter = storage.findAll(query);
            assertFalse(recordsAfter.hasNext());
        }

        @CanIgnoreReturnValue
        private EntityRecord
        writeWithStatus(TestCounterEntity entity,
                        StgProject.Status status,
                        EntityRecordStorage<StgProjectId, StgProject> storage) {
            entity.assignStatus(status);
            var withCols = recordWithCols(entity);
            storage.write(withCols);
            return withCols.record();
        }
    }
}
