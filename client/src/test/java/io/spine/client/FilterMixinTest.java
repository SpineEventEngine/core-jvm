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

package io.spine.client;

import com.google.common.testing.NullPointerTester;
import com.google.protobuf.Any;
import io.spine.base.FieldPath;
import io.spine.test.client.ClProjectCreated;
import io.spine.test.client.TestEntity;
import io.spine.test.client.TestEntityName;
import io.spine.type.TypeName;
import io.spine.type.TypeUrl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static io.spine.testing.DisplayNames.NOT_ACCEPT_NULLS;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("`FilterMixin` should")
class FilterMixinTest {

    private static final TypeUrl TEST_ENTITY_TYPE = TypeUrl.of(TestEntity.class);
    private static final TypeUrl PROJECT_CREATED_TYPE = TypeUrl.of(ClProjectCreated.class);

    @Test
    @DisplayName(NOT_ACCEPT_NULLS)
    void passNullToleranceCheck() {
        var filter = Filters.eq("first_field", "column value");
        new NullPointerTester()
                .setDefault(FieldPath.class, FieldPath.getDefaultInstance())
                .setDefault(TypeName.class, TypeName.of(Any.class))
                .testAllPublicInstanceMethods(filter);
    }

    @Test
    @DisplayName("check the filter can be applied to the given target")
    void validateFilter() {
        var filter = Filters.eq("first_field", "some entity column value");
        filter.checkCanApplyTo(testEntityTarget());
    }

    @Test
    @DisplayName("consider the filter targeting a nested field of an event message valid")
    void validateFilterWithNested() {
        var filter = Filters.eq("name.value", "a project name");
        filter.checkCanApplyTo(createProjectTarget());
    }

    @Nested
    @DisplayName("deem the filter non-applicable to the given target")
    class CheckInvalid {

        @Test
        @DisplayName("when the field is not present in the target type")
        void whenFieldNotPresent() {
            var filter = Filters.eq("non_existing_field", "some entity column value");
            assertNotApplies(filter, testEntityTarget());
        }

        @Nested
        @DisplayName("when the filter is targeting entity state")
        class TargetingEntityState {

            @Test
            @DisplayName("and the target field is not top-level")
            void whenFieldNotTopLevel() {
                var filter = Filters.eq("name.value", "a test entity name");
                assertNotApplies(filter, testEntityTarget());
            }

            @Test
            @DisplayName("and the target field is not an entity column")
            void whenFieldIsNotColumn() {
                var name = TestEntityName.newBuilder()
                        .setValue("the entity name")
                        .build();
                var filter = Filters.eq("name", name);
                assertNotApplies(filter, testEntityTarget());
            }
        }

        void assertNotApplies(Filter filter, Target target) {
            assertThrows(IllegalStateException.class,
                         () -> filter.checkCanApplyTo(target));
        }
    }

    /**
     * Returns a target enclosing the {@link TestEntity} type.
     */
    private static Target testEntityTarget() {
        var target = Target.newBuilder()
                .setType(TEST_ENTITY_TYPE.value())
                .setIncludeAll(true)
                .build();
        return target;
    }

    /**
     * Returns a target enclosing the {@link ClCreateProject} type.
     */
    private static Target createProjectTarget() {
        var target = Target.newBuilder()
                .setType(PROJECT_CREATED_TYPE.value())
                .setIncludeAll(true)
                .build();
        return target;
    }
}
