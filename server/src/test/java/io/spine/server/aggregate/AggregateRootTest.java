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

package io.spine.server.aggregate;

import com.google.common.testing.NullPointerTester;
import io.spine.server.BoundedContext;
import io.spine.server.BoundedContextBuilder;
import io.spine.server.aggregate.given.AggregateRootTestEnv;
import io.spine.server.aggregate.given.AggregateRootTestEnv.AnAggregateRoot;
import io.spine.server.aggregate.given.AggregateRootTestEnv.ProjectDefinitionRepository;
import io.spine.server.aggregate.given.AggregateRootTestEnv.ProjectLifeCycleRepository;
import io.spine.test.aggregate.ProjectDefinition;
import io.spine.test.aggregate.ProjectId;
import io.spine.test.aggregate.ProjectLifecycle;
import io.spine.testing.server.model.ModelTests;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static io.spine.testing.DisplayNames.NOT_ACCEPT_NULLS;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("`AggregateRoot` should")
@SuppressWarnings("deprecation") // Tests the deprecated aggregate parts API.
class AggregateRootTest {

    private AggregateRootTestEnv.ProjectRoot aggregateRoot;
    private BoundedContext context;

    @BeforeEach
    void setUp() {
        ModelTests.dropAllModels();
        context = BoundedContextBuilder.assumingTests().build();
        var projectId = ProjectId.generate();
        aggregateRoot = new AggregateRootTestEnv.ProjectRoot(context, projectId);
        var contextAccess = context.internalAccess();
        contextAccess.register(new ProjectDefinitionRepository());
        contextAccess.register(new ProjectLifeCycleRepository());
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    @DisplayName(NOT_ACCEPT_NULLS)
    void passNullToleranceCheck() throws NoSuchMethodException {
        var ctor =
                AnAggregateRoot.class.getDeclaredConstructor(BoundedContext.class, String.class);
        new NullPointerTester()
                .setDefault(Constructor.class, ctor)
                .setDefault(BoundedContext.class, context)
                .testStaticMethods(AggregateRoot.class, NullPointerTester.Visibility.PACKAGE);
    }

    @Test
    @DisplayName("obtain part state by its class")
    void returnPartStateByClass() {
        var definitionPart = aggregateRoot.partState(ProjectDefinition.class);
        assertNotNull(definitionPart);

        var lifeCyclePart = aggregateRoot.partState(ProjectLifecycle.class);
        assertNotNull(lifeCyclePart);
    }
}
