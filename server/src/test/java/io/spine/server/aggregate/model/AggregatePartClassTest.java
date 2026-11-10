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

package io.spine.server.aggregate.model;

import io.spine.server.BoundedContextBuilder;
import io.spine.server.aggregate.given.part.AnAggregatePart;
import io.spine.server.aggregate.given.part.AnAggregateRoot;
import io.spine.server.aggregate.given.part.WrongAggregatePart;
import io.spine.server.model.ModelError;
import io.spine.test.aggregate.ProjectId;
import io.spine.testing.server.model.ModelTests;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.spine.testing.server.TestContexts.buildForTests;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("`AggregatePartClass` should")
@SuppressWarnings("deprecation") // Tests the deprecated aggregate parts API.
class AggregatePartClassTest {

    private final AggregatePartClass<AnAggregatePart> partClass =
            AggregatePartClass.asAggregatePartClass(AnAggregatePart.class);
    private AnAggregateRoot root;

    @BeforeEach
    void setUp() {
        ModelTests.dropAllModels();
        var boundedContext = buildForTests(BoundedContextBuilder.assumingTests());
        var projectId = ProjectId.generate();
        root = new AnAggregateRoot(boundedContext, projectId);
    }

    @Test
    @DisplayName("obtain aggregate part constructor")
    void getAggregatePartConstructor() {
        assertNotNull(partClass.constructor());
    }

    @Test
    @DisplayName("throw exception when aggregate part does not have appropriate constructor")
    void throwOnNoProperCtorAvailable() {
        var wrongPartClass = AggregatePartClass.asAggregatePartClass(WrongAggregatePart.class);
        assertThrows(ModelError.class, wrongPartClass::constructor);
    }

    @Test
    @DisplayName("create aggregate part entity")
    void createAggregatePartEntity() {
        var part = partClass.create(root);

        assertNotNull(part);
        assertEquals(root.id(), part.id());
    }
}
