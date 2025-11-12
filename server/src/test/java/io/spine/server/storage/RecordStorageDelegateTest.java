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
import com.google.common.collect.Sets;
import com.google.protobuf.util.Timestamps;
import io.spine.query.RecordQuery;
import io.spine.query.RecordQueryBuilder;
import io.spine.server.ContextSpec;
import io.spine.server.ServerEnvironment;
import io.spine.server.storage.given.RecordStorageDelegateTestEnv;
import io.spine.server.storage.given.StgProjectStorage;
import io.spine.test.storage.StgProject;
import io.spine.test.storage.StgProjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static com.google.protobuf.util.Durations.fromDays;
import static com.google.protobuf.util.Durations.fromMinutes;
import static com.google.protobuf.util.Timestamps.add;
import static com.google.protobuf.util.Timestamps.subtract;
import static io.spine.base.Time.currentTime;
import static io.spine.server.storage.given.GivenStorageProject.StgProjectColumns.due_date;
import static io.spine.server.storage.given.GivenStorageProject.StgProjectColumns.status;
import static io.spine.server.storage.given.GivenStorageProject.newState;
import static io.spine.server.storage.given.RecordStorageDelegateTestEnv.assertOnlyIdAndDueDate;
import static io.spine.server.storage.given.RecordStorageDelegateTestEnv.coupleOfDone;
import static io.spine.server.storage.given.RecordStorageDelegateTestEnv.dozenOfRecords;
import static io.spine.server.storage.given.RecordStorageDelegateTestEnv.idAndDueDate;
import static io.spine.server.storage.given.RecordStorageDelegateTestEnv.toIds;
import static io.spine.test.storage.StgProject.Status.CREATED;
import static io.spine.test.storage.StgProject.Status.DONE;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests of the API provided by {@link RecordStorageDelegate} to the descendant classes.
 *
 * <p>Sample storage implementation used in the test is {@link StgProjectStorage}.
 *
 * <p>The aim of this test is to ensure that any storage implementations built on top of
 * the {@code RecordStorageDelegate} is able to utilize the API with the expected results.
 *
 * <p>This type is made {@code public}, so that it could be re-used in testing of
 * Spine libraries sitting on top of real-world storage engines, such as Google Datastore.
 *
 * <p>In order to use this type in this manner, one should configure the desired
 * {@code StorageFactory} implementation before this test suite:
 *
 * <pre>
 *     ServerEnvironment.when(Tests.class)
 *                      .useStorageFactory(myRealStorageFactory);
 * </pre>
 *
 * {@code StgProjectStorage} used in these tests delegates all of its actions to the underlying
 * {@code RecordStorage}, which in turn is produced by the pre-configured
 * {@linkplain ServerEnvironment#storageFactory() current} {@code StorageFactory}.
 * Therefore all tests will run against the desired storage engine.
 */
@DisplayName("A `RecordStorageDelegate` descendant should")
public class RecordStorageDelegateTest
        extends AbstractStorageTest<StgProjectId, StgProject, StgProjectStorage> {

    @Override
    protected StgProjectStorage newStorage() {
        var factory = ServerEnvironment.instance()
                                       .storageFactory();
        var ctxSpec = ContextSpec.singleTenant(getClass().getName());
        return new StgProjectStorage(ctxSpec, factory);
    }

    @Override
    protected StgProject newStorageRecord(StgProjectId id) {
        return newState(id);
    }

    @Override
    protected StgProjectId newId() {
        return RecordStorageDelegateTestEnv.generateId();
    }

    @Nested
    @DisplayName("write and read")
    class WriteAndRead {

        @Test
        @DisplayName("batch of records")
        void manyRecords() {
            Iterable<StgProject> records = dozenOfRecords()
                    .values();
            storage().writeBatch(records);

            var actualIterator = storage().readAll();
            var actualRecords = ImmutableList.copyOf(actualIterator);
            assertThat(actualRecords).containsExactlyElementsIn(records);
        }

        @Test
        @DisplayName("batch of records, recalling them by their IDs")
        void allByIds() {
            var recordMap = dozenOfRecords();
            storage().writeBatch(recordMap.values());

            var ids = recordMap.keySet();
            var partOfIds = RecordStorageDelegateTestEnv.halfDozenOf(ids);
            var actualIterator = storage().readAll(partOfIds);
            var actualRecords = ImmutableList.copyOf(actualIterator);
            RecordStorageDelegateTestEnv.assertHaveIds(actualRecords, partOfIds);
        }
    }

    @Nested
    @DisplayName("read")
    class Query {

        @Test
        @DisplayName("a single record with the particular `FieldMask`")
        void singleRecordWithMask() {
            var record = newState(newId());
            storage().write(record);

            var result = storage().read(record.getId(), idAndDueDate());
            assertThat(result).isPresent();
            @SuppressWarnings("OptionalGetWithoutIsPresent") // checked on the prev. line.
            var actual = result.get();
            assertOnlyIdAndDueDate(actual);
        }

        @Test
        @DisplayName("several records according to the query with the `FieldMask` set")
        void allByMask() {
            var records = dozenOfRecords().values();
            storage().writeBatch(records);

            var query = queryBuilder().withMask(idAndDueDate()).build();
            var iterator = storage().readAll(query);
            var actualResults = ImmutableList.copyOf(iterator);
            for (var result : actualResults) {
                assertOnlyIdAndDueDate(result);
            }
        }

        @Test
        @DisplayName("several records according to the given limit and ordering")
        void allRecordsWithLimitAndOrdering() {
            var oldest = newState(newId(), DONE, add(currentTime(), fromMinutes(0)));
            var older = newState(newId(), DONE, add(currentTime(), fromMinutes(1)));
            var almostNew = newState(newId(), DONE, add(currentTime(), fromMinutes(2)));
            var newest = newState(newId(), DONE, add(currentTime(), fromMinutes(3)));

            storage().writeBatch(ImmutableList.of(newest, older, oldest, almostNew));

            var limit = 2;
            var query = queryBuilder().sortAscendingBy(due_date)
                                      .limit(limit)
                                      .build();
            var iterator = storage().readAll(query);
            var actualRecords = ImmutableList.copyOf(iterator);
            assertThat(actualRecords).hasSize(limit);
            assertThat(actualRecords.get(0)).isEqualTo(oldest);
            assertThat(actualRecords.get(1)).isEqualTo(older);
        }

        @Test
        @DisplayName("several records by their IDs and the `FieldMask`")
        void allByIdsAndMask() {
            var recordMap = dozenOfRecords();
            storage().writeBatch(recordMap.values());

            var iterator = storage().readAll(recordMap.keySet(), idAndDueDate());
            var actualResults = ImmutableList.copyOf(iterator);
            for (var result : actualResults) {
                assertOnlyIdAndDueDate(result);
            }
        }

        @Test
        @DisplayName("many records by a single column value only")
        void manyRecordsBySingleColumnWithDefaultResponseFormat() {
            var createdProjects = dozenOfRecords().values();
            storage().writeBatch(createdProjects);

            var doneProjects = coupleOfDone(currentTime());
            storage().writeBatch(doneProjects);

            var query = queryDoneProjects().build();
            var iterator = storage().readAll(query);
            var actualProjects = ImmutableList.copyOf(iterator);

            assertThat(actualProjects).containsExactlyElementsIn(doneProjects);
        }

        @Test
        @DisplayName("many records by several columns only")
        void manyRecordsBySeveralColumnsWithDefaultResponseFormat() {
            var createdProjects = dozenOfRecords().values();
            storage().writeBatch(createdProjects);

            var now = currentTime();
            var doneDueToday = coupleOfDone(now);
            storage().writeBatch(doneDueToday);

            var doneDueYesterday = coupleOfDone(subtract(now, fromDays(1)));
            storage().writeBatch(doneDueYesterday);

            var aMinuteAgo = subtract(now, fromMinutes(1));

            var query = queryDoneProjects()
                    .where(due_date).isLessThan(aMinuteAgo)
                    .build();

            var iterator = storage().readAll(query);
            var actualProjects = ImmutableList.copyOf(iterator);

            assertThat(actualProjects).containsExactlyElementsIn(doneDueYesterday);
        }

        private RecordQueryBuilder<StgProjectId, StgProject> queryDoneProjects() {
            return queryBuilder().where(status).is(DONE.name());
        }

        @Test
        @DisplayName("many records by a single column with the limit and ordering")
        void manyRecordsBySingleColumnAndLimit() {
            var createdProjects = dozenOfRecords().values();
            storage().writeBatch(createdProjects);

            var queryDone = queryDoneProjects()
                    .sortAscendingBy(due_date)
                    .limit(10)
                    .build();

            var iterator = storage().readAll(queryDone);
            assertThat(iterator.hasNext()).isFalse();

            List<StgProject> sortedByDueDate = new ArrayList<>(createdProjects);
            sortedByDueDate.sort((r1, r2) -> Timestamps.compare(r1.getDueDate(), r2.getDueDate()));
            var limit = 2;
            var expected = sortedByDueDate.subList(0, limit);

            var queryCreated = queryBuilder()
                    .where(status).is(CREATED.name())
                    .sortAscendingBy(due_date)
                    .limit(limit)
                    .build();
            var limitedIterator = storage().readAll(queryCreated);
            var actual = ImmutableList.copyOf(limitedIterator);
            assertThat(actual).containsExactlyElementsIn(expected);
        }

        @Test
        @DisplayName("many records by several columns with the limit and ordering")
        void manyRecordsBySeveralColumnsAndLimit() {
            var now = currentTime();

            var doneLongAgo = coupleOfDone(subtract(now, fromDays(10)));
            var records = ImmutableList.<StgProject>builder()
                    .addAll(doneLongAgo)
                    .addAll(coupleOfDone(subtract(now, fromDays(1))))
                    .addAll(coupleOfDone(now))
                    .addAll(dozenOfRecords().values())  // in `CREATED` status.
                    .build();
            storage().writeBatch(records);

            var query = queryDoneProjects()
                    .where(due_date).isLessThan(now)
                    .sortAscendingBy(due_date)
                    .limit(2)
                    .build();

            var iterator = storage().readAll(query);
            var actual = ImmutableList.copyOf(iterator);
            assertThat(actual).containsExactlyElementsIn(doneLongAgo);
        }

        @Test
        @DisplayName("many records by several columns with the limit and the field mask")
        void manyRecordsBySeveralColumnsWithLimitAndMask() {
            var now = currentTime();

            var doneDueYesterday = coupleOfDone(subtract(now, fromDays(1)));
            var records = ImmutableList.<StgProject>builder()
                    .addAll(coupleOfDone(subtract(now, fromDays(10))))
                    .addAll(doneDueYesterday)
                    .addAll(coupleOfDone(now))
                    .addAll(dozenOfRecords().values())  // in `CREATED` status.
                    .build();
            storage().writeBatch(records);

            var doneAndDueBeforeNow = queryDoneProjects()
                    .where(due_date).isLessThan(now)
                    .withMask(idAndDueDate())
                    .sortDescendingBy(due_date)
                    .limit(2)
                    .build();

            var iterator = storage().readAll(doneAndDueBeforeNow);
            var actual = ImmutableList.copyOf(iterator);
            RecordStorageDelegateTestEnv.assertHaveIds(actual, toIds(doneDueYesterday));

            for (var readResult : actual) {
                assertOnlyIdAndDueDate(readResult);
            }
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("a single record by its ID")
        void recordById() {
            var record = randomRecord();
            storage().write(record);
            var readResult = storage().read(record.getId());
            assertThat(readResult).isPresent();

            var deleted = storage().delete(record.getId());
            assertThat(deleted).isTrue();
            var anotherReadResult = storage().read(record.getId());
            assertThat(anotherReadResult).isEmpty();
        }

        @Test
        @DisplayName("several records by their IDs at once")
        void manyRecordByIds() {
            var recordMap = dozenOfRecords();
            storage().writeBatch(recordMap.values());

            var ids = recordMap.keySet();
            var partOfIds = RecordStorageDelegateTestEnv.halfDozenOf(ids);
            var actualIterator = storage().readAll(partOfIds);
            var actualRecords = ImmutableList.copyOf(actualIterator);

            RecordStorageDelegateTestEnv.assertHaveIds(actualRecords, partOfIds);

            storage().deleteAll(partOfIds);
            var afterDeletion = storage().readAll(partOfIds);
            assertThat(afterDeletion.hasNext()).isFalse();

            var iterator = storage().readAll();
            var remainder = ImmutableList.copyOf(iterator);
            var expectedRemainedIds =
                    Sets.symmetricDifference(ids, ImmutableSet.copyOf(partOfIds));
            RecordStorageDelegateTestEnv.assertHaveIds(remainder, expectedRemainedIds);
        }
    }

    @Nested
    @DisplayName("throw an `IllegalStateException` if it is closed and the user invokes")
    @SuppressWarnings("ResultOfMethodCallIgnored")
            // as we just call the method!
    class ThrowIseIfClosed {

        @BeforeEach
        void closeStorage() {
            storage().close();
        }

        private void assertISE(Executable executable) {
            assertThrows(IllegalStateException.class, executable);
        }

        @Test
        @DisplayName("`write(record)` method")
        void write() {
            assertISE(() -> storage().write(randomRecord()));
        }

        @Test
        @DisplayName("`writeAll(Iterable)` method")
        void writeAll() {
            assertISE(() -> storage().writeBatch(ImmutableSet.of(randomRecord())));
        }

        @Test
        @DisplayName("`write(id, record)` method")
        void writeIdRecord() {
            var record = randomRecord();
            assertISE(() -> storage().write(record.getId(), record));
        }

        @Test
        @DisplayName("`read(id, FieldMask)` method")
        void readIdFieldMask() {
            assertISE(() -> storage().read(newId(), idAndDueDate()));
        }

        @Test
        @DisplayName("`readAll()` method")
        void readAll() {
            assertISE(() -> storage().readAll());
        }

        @Test
        @DisplayName("`readAll(RecordQuery)` method")
        void readAllByQuery() {
            var query = queryBuilder().build();
            assertISE(() -> storage().readAll(query));
        }

        @Test
        @DisplayName("`readAll(IDs)` method")
        @SuppressWarnings("DistinctVarargsChecker")
        void readAllByIds() {
            assertISE(() -> storage().readAll(ImmutableSet.of(newId(), newId())));
        }

        @Test
        @DisplayName("`readAll(IDs, FieldMask)` method")
        void readAllByIdsAndMask() {
            assertISE(() -> storage().readAll(ImmutableSet.of(newId()), idAndDueDate()));
        }

        @Test
        @DisplayName("`delete(ID)` method")
        void delete() {
            assertISE(() -> storage().delete(newId()));
        }

        @Test
        @DisplayName("`deleteAll(IDs)` method")
        void deleteAll() {
            assertISE(() -> storage().deleteAll(ImmutableList.of(newId(), newId())));
        }
    }

    private StgProject randomRecord() {
        return newStorageRecord(RecordStorageDelegateTestEnv.generateId());
    }

    private static RecordQueryBuilder<StgProjectId, StgProject> queryBuilder() {
        return RecordQuery.newBuilder(StgProjectId.class, StgProject.class);
    }
}
