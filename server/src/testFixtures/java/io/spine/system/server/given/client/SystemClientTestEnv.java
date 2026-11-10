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

package io.spine.system.server.given.client;

import io.spine.server.BoundedContext;
import io.spine.server.BoundedContextBuilder;
import io.spine.test.system.server.MealOrder;
import io.spine.test.system.server.OrderId;

import static io.spine.system.server.SystemBoundedContexts.systemOf;
import static io.spine.testing.server.TestContexts.buildForTests;
import static org.junit.jupiter.api.Assertions.fail;

public class SystemClientTestEnv {

    /**
     * Prevents the utility class instantiation.
     */
    private SystemClientTestEnv() {
    }

    public static BoundedContext contextWithSystemProjection() {
        var context = buildForTests(BoundedContextBuilder.assumingTests());
        var systemContext = systemOf(context);
        systemContext.internalAccess()
                     .register(new MealOrderRepository());
        return context;
    }

    public static MealOrderProjection findProjection(OrderId projectionId, BoundedContext context) {
        var repository = (MealOrderRepository)
                context.internalAccess()
                       .findRepository(MealOrder.class)
                       .orElseGet(() -> fail("Projection repository should be visible."));
        var projection =
                repository.find(projectionId)
                          .orElseGet(() -> fail("Projection should be present."));
        return projection;
    }
}
