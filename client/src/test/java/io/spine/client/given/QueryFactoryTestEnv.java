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

package io.spine.client.given;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.Any;
import com.google.protobuf.FieldMask;
import io.spine.client.Query;
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

    public static void checkFieldMaskEmpty(Query query) {
        var fieldMask = query.getFormat().getFieldMask();
        assertNotNull(fieldMask);
        assertEquals(FieldMask.getDefaultInstance(), fieldMask);
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

    public static void verifySinglePathInQuery(String expectedEntityPath, Query query) {
        var fieldMask = query.getFormat().getFieldMask();
        assertEquals(1, fieldMask.getPathsCount()); // As we set the only path value.

        var firstPath = fieldMask.getPaths(0);
        assertEquals(expectedEntityPath, firstPath);
    }

    public static String singleTestEntityPath() {
        return TestEntity.getDescriptor()
                         .getFields()
                         .get(1)
                         .getFullName();
    }

    public static void verifyMultiplePathsInQuery(String[] paths,
                                                  Query readAllWithPathFilteringQuery) {
        var fieldMask = readAllWithPathFilteringQuery.getFormat().getFieldMask();
        assertEquals(paths.length, fieldMask.getPathsCount());
        var pathsList = fieldMask.getPathsList();
        for (var expectedPath : paths) {
            assertTrue(pathsList.contains(expectedPath));
        }
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
