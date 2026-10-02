/*
 * Copyright 2026 CodeMatters, Lda.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
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
import static io.spine.client.given.QueryFactoryTestEnv.checkFiltersEmpty;
import static io.spine.client.given.QueryFactoryTestEnv.checkFormatEmpty;
import static io.spine.client.given.QueryFactoryTestEnv.checkIdQueriesEqual;
import static io.spine.client.given.QueryFactoryTestEnv.checkTargetIsTestEntity;
import static io.spine.client.given.QueryFactoryTestEnv.threeIds;
import static io.spine.client.given.QueryFactoryTestEnv.threeRandomParts;
import static io.spine.client.given.QueryFactoryTestEnv.verifyIdFilter;
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

            checkFormatEmpty(readAllQuery);
        }

        @Test
        @DisplayName("`read by IDs`")
        void readByIds() {
            var testEntityIds = threeIds();
            var readByIdsQuery = factory.byIds(TEST_ENTITY_TYPE, testEntityIds);
            assertNotNull(readByIdsQuery);

            checkFormatEmpty(readByIdsQuery);

            var target = checkTargetIsTestEntity(readByIdsQuery);

            verifyIdFilter(testEntityIds, target.getFilters());
        }

        @Test
        @DisplayName("`read all` ignoring the mask")
        @SuppressWarnings("deprecation") // Checking that the deprecated API ignores the mask.
        void readAllIgnoringMask() {
            var readAllQuery = factory.allWithMask(TEST_ENTITY_TYPE, threeRandomParts());
            assertNotNull(readAllQuery);

            checkFiltersEmpty(readAllQuery);
            checkTargetIsTestEntity(readAllQuery);
            checkFormatEmpty(readAllQuery);
        }

        @Test
        @DisplayName("`read by IDs` ignoring the mask")
        @SuppressWarnings("deprecation") // Checking that the deprecated API ignores the mask.
        void readByIdsIgnoringMask() {
            var testEntityIds = threeIds();
            var readByIdsQuery = factory.byIdsWithMask(TEST_ENTITY_TYPE,
                                                       testEntityIds, threeRandomParts());
            assertNotNull(readByIdsQuery);

            var target = checkTargetIsTestEntity(readByIdsQuery);

            verifyIdFilter(testEntityIds, target.getFilters());
            checkFormatEmpty(readByIdsQuery);
        }
    }

    @Test
    @DisplayName("fail to create query with mask when id list is empty")
    @SuppressWarnings("deprecation") // The deprecated API keeps its argument checks.
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
        @DisplayName("`read all`")
        void readAllConsistently() {
            var fromFactory = factory.all(TEST_ENTITY_TYPE);
            var fromBuilder = factory.select(TEST_ENTITY_TYPE)
                                     .build();
            assertEquals(fromFactory.getTarget(), fromBuilder.getTarget());
            assertNotEquals(fromFactory.getId(), fromBuilder.getId());
        }
    }
}
