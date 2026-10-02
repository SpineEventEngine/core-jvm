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

package io.spine.client.given;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.Any;
import io.spine.client.Query;
import io.spine.client.ResponseFormat;
import io.spine.client.Target;
import io.spine.client.TargetFilters;
import io.spine.protobuf.AnyPacker;
import io.spine.test.client.TestEntity;
import io.spine.test.client.TestEntityId;
import io.spine.type.TypeUrl;
import io.spine.validation.NonValidated;

import java.util.Collection;
import java.util.Set;

import static com.google.common.collect.Sets.newHashSet;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class QueryFactoryTestEnv {

    // See {@code client_requests.proto} for declaration.
    public static final Class<TestEntity> TEST_ENTITY_TYPE = TestEntity.class;
    private static final TypeUrl TEST_ENTITY_TYPE_URL = TypeUrl.of(TEST_ENTITY_TYPE);

    /** Prevents instantiation of this test environment class. */
    private QueryFactoryTestEnv() {
    }

    public static Set<TestEntityId> threeIds() {
        return newHashSet(testId(1), testId(7), testId(15));
    }

    private static TestEntityId testId(int value) {
        return TestEntityId.newBuilder()
                           .setValue(value)
                           .build();
    }

    public static String[] threeRandomParts() {
        return new String[]{"some", "random", "paths"};
    }

    public static void checkFormatEmpty(Query query) {
        assertEquals(ResponseFormat.getDefaultInstance(), query.getFormat());
    }

    public static void checkFiltersEmpty(Query query) {
        var entityTarget = query.getTarget();
        var filters = entityTarget.getFilters();
        assertNotNull(filters);
        assertEquals(TargetFilters.getDefaultInstance(), filters);
    }

    public static void verifyIdFilter(Set<TestEntityId> expectedIds, TargetFilters filters) {
        assertNotNull(filters);
        var idFilter = filters.getIdFilter();
        assertNotNull(idFilter);
        var actualListOfIds = idFilter.getIdList();
        for (var testEntityId : expectedIds) {
            var expectedEntityId = AnyPacker.pack(testEntityId);
            assertTrue(actualListOfIds.contains(expectedEntityId));
        }
    }

    @CanIgnoreReturnValue
    public static Target checkTargetIsTestEntity(Query query) {
        var entityTarget = query.getTarget();
        assertNotNull(entityTarget);

        assertEquals(TEST_ENTITY_TYPE_URL.value(), entityTarget.getType());
        return entityTarget;
    }

    private static @NonValidated TargetFilters stripIdFilter(TargetFilters filters) {
        return filters.toBuilder()
                      .clearIdFilter()
                      .buildPartial();
    }

    private static @NonValidated Target stripFilters(Target target) {
        return target.toBuilder()
                     .clearFilters()
                     .buildPartial();
    }

    public static void checkIdQueriesEqual(Query query1, Query query2) {
        assertNotEquals(query1.getId(), query2.getId());

        var factoryTarget = query1.getTarget();
        var builderTarget = query2.getTarget();

        var factoryFilters = factoryTarget.getFilters();
        var builderFilters = builderTarget.getFilters();

        // Everything except filters is the same
        assertEquals(stripFilters(factoryTarget), stripFilters(builderTarget));

        var factoryIdFilter = factoryFilters.getIdFilter();
        var builderIdFilter = builderFilters.getIdFilter();

        // Everything except ID filter is the same
        assertEquals(stripIdFilter(factoryFilters), stripIdFilter(builderFilters));

        Collection<Any> factoryEntityIds = factoryIdFilter.getIdList();
        Collection<Any> builderEntityIds = builderIdFilter.getIdList();

        // Order may differ but all the elements are the same
        assertThat(builderEntityIds).hasSize(factoryEntityIds.size());
        assertThat(builderEntityIds).containsAtLeastElementsIn(factoryEntityIds);
    }
}
