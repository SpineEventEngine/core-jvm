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
package io.spine.client;

import io.spine.testing.core.given.GivenUserId;
import io.spine.time.ZoneIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static io.spine.base.Identifier.newUuid;
import static io.spine.client.given.QueryFactoryTestEnv.TEST_ENTITY_TYPE;
import static io.spine.client.given.QueryFactoryTestEnv.checkFieldMaskEmpty;
import static io.spine.client.given.QueryFactoryTestEnv.checkFiltersEmpty;
import static io.spine.client.given.QueryFactoryTestEnv.checkIdQueriesEqual;
import static io.spine.client.given.QueryFactoryTestEnv.checkTargetIsTestEntity;
import static io.spine.client.given.QueryFactoryTestEnv.singleTestEntityPath;
import static io.spine.client.given.QueryFactoryTestEnv.threeIds;
import static io.spine.client.given.QueryFactoryTestEnv.threeRandomParts;
import static io.spine.client.given.QueryFactoryTestEnv.verifyIdFilter;
import static io.spine.client.given.QueryFactoryTestEnv.verifyMultiplePathsInQuery;
import static io.spine.client.given.QueryFactoryTestEnv.verifySinglePathInQuery;
import static java.util.Collections.emptySet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SuppressWarnings({"LocalVariableNamingConvention",
                   "DuplicateStringLiteralInspection"}) // A lot of similar test display names.
@DisplayName("`QueryFactory` should")
class QueryFactoryTest {

    private QueryFactory factory;

    private static ActorRequestFactory requestFactory() {
        return ActorRequestFactory.newBuilder()
                .setZoneId(ZoneIds.systemDefault())
                .setActor(GivenUserId.of(newUuid()))
                .build();
    }

    @BeforeEach
    void createFactory() {
        factory = requestFactory().query();
    }

    @Nested
    @DisplayName("compose query of type")
    class ComposeQuery {

        @Test
        @DisplayName("`read all`")
        void readAll() {
            var readAllQuery = factory.all(TEST_ENTITY_TYPE);
            assertNotNull(readAllQuery);

            checkFiltersEmpty(readAllQuery);
            checkTargetIsTestEntity(readAllQuery);

            checkFieldMaskEmpty(readAllQuery);
        }

        @Test
        @DisplayName("`read by IDs`")
        void readByIds() {
            var testEntityIds = threeIds();
            var readByIdsQuery = factory.byIds(TEST_ENTITY_TYPE, testEntityIds);
            assertNotNull(readByIdsQuery);

            checkFieldMaskEmpty(readByIdsQuery);

            var target = checkTargetIsTestEntity(readByIdsQuery);

            verifyIdFilter(testEntityIds, target.getFilters());
        }

        @Test
        @DisplayName("`read all` with single path mask")
        void readAllWithSinglePath() {
            var expectedEntityPath = singleTestEntityPath();
            var readAllWithPathFilteringQuery = factory.allWithMask(TEST_ENTITY_TYPE,
                                                                    expectedEntityPath);
            assertNotNull(readAllWithPathFilteringQuery);

            checkFiltersEmpty(readAllWithPathFilteringQuery);
            checkTargetIsTestEntity(readAllWithPathFilteringQuery);
            verifySinglePathInQuery(expectedEntityPath, readAllWithPathFilteringQuery);
        }

        @Test
        @DisplayName("`read by IDs` with single path mask")
        void readByIdsWitSinglePath() {
            var testEntityIds = threeIds();
            var expectedPath = singleTestEntityPath();
            var readByIdsWithSinglePathQuery = factory.byIdsWithMask(TEST_ENTITY_TYPE,
                                                                     testEntityIds, expectedPath);
            assertNotNull(readByIdsWithSinglePathQuery);

            var target = checkTargetIsTestEntity(readByIdsWithSinglePathQuery);

            verifyIdFilter(testEntityIds, target.getFilters());
            verifySinglePathInQuery(expectedPath, readByIdsWithSinglePathQuery);
        }

        @Test
        @DisplayName("`read all` with multiple paths mask")
        void readAllWithMultiplePaths() {
            var paths = threeRandomParts();
            var readAllWithPathFilteringQuery = factory.allWithMask(TEST_ENTITY_TYPE, paths);
            assertNotNull(readAllWithPathFilteringQuery);

            checkFiltersEmpty(readAllWithPathFilteringQuery);
            checkTargetIsTestEntity(readAllWithPathFilteringQuery);
            verifyMultiplePathsInQuery(paths, readAllWithPathFilteringQuery);
        }

        @Test
        @DisplayName("`read by IDs` with multiple paths mask")
        void readByIdsWithMultiplePaths() {
            var testEntityIds = threeIds();
            var paths = threeRandomParts();
            var readByIdsWithSinglePathQuery = factory.byIdsWithMask(TEST_ENTITY_TYPE,
                                                                     testEntityIds, paths);
            assertNotNull(readByIdsWithSinglePathQuery);

            var target = checkTargetIsTestEntity(readByIdsWithSinglePathQuery);

            verifyIdFilter(testEntityIds, target.getFilters());
            verifyMultiplePathsInQuery(paths, readByIdsWithSinglePathQuery);
        }
    }

    @Test
    @DisplayName("fail to create query with mask when id list is empty")
    void failForEmptyIds() {
        assertThrows(IllegalArgumentException.class,
                     () -> factory.byIdsWithMask(TEST_ENTITY_TYPE, emptySet(), "", ""));
    }

    @Nested
    @DisplayName("be consistent with `QueryBuilder` when creating query")
    class CreateQuery {

        @Test
        @DisplayName("by IDs")
        void byIdConsistently() {
            var ids = threeIds();
            var fromFactory = factory.byIds(TEST_ENTITY_TYPE, ids);
            var fromBuilder = factory.select(TEST_ENTITY_TYPE)
                                     .byId(ids)
                                     .build();
            checkIdQueriesEqual(fromFactory, fromBuilder);
        }

        @Test
        @DisplayName("by mask")
        void byMaskConsistently() {
            var field1 = "TestEntity.firstField";
            var field2 = "TesEntity.barField";

            var fromFactory = factory.allWithMask(TEST_ENTITY_TYPE, field1, field2);
            var fromBuilder = factory.select(TEST_ENTITY_TYPE)
                                     .withMask(field1, field2)
                                     .build();
            assertNotEquals(fromBuilder.getId(), fromFactory.getId());
            var targetFromFactory = fromFactory.getTarget();
            var targetFromBuilder = fromBuilder.getTarget();
            assertEquals(targetFromFactory, targetFromBuilder);
        }

        @Test
        @DisplayName("`read all`")
        void readAllConsistently() {
            var fromFactory = factory.all(TEST_ENTITY_TYPE);
            var fromBuilder = factory.select(TEST_ENTITY_TYPE)
                                     .build();
            assertEquals(fromFactory.getTarget(), fromBuilder.getTarget());
            assertNotEquals(fromFactory.getId(), fromBuilder.getId());
        }

        @Test
        @DisplayName("by IDs with mask")
        void byIdsWithMaskConsistently() {
            var field1 = "TestEntity.secondField";
            var field2 = "TesEntity.fooField";

            var ids = threeIds();
            var fromFactory = factory.byIdsWithMask(TEST_ENTITY_TYPE, ids, field1, field2);
            var fromBuilder = factory.select(TEST_ENTITY_TYPE)
                                     .byId(ids)
                                     .withMask(field1, field2)
                                     .build();
            checkIdQueriesEqual(fromFactory, fromBuilder);
        }
    }
}
