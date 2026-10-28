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

package io.spine.server.model.contexts;

import io.spine.server.BoundedContext;
import io.spine.server.model.contexts.orders.OrdersContext;
import io.spine.server.model.contexts.tasks.TasksContext;
import io.spine.testing.server.model.ModelTests;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("Two `BoundedContext`s")
class TwoBoundedContextsTest {

    private BoundedContext tasksContext;
    private BoundedContext ordersContext;

    @BeforeEach
    void clearModel() {
        ModelTests.dropAllModels();
        tasksContext = TasksContext.newInstance();
        ordersContext = OrdersContext.newInstance();
    }

    @AfterEach
    void closeContexts() {
        tasksContext.close();
        ordersContext.close();
    }

    @Test
    @DisplayName("can be created")
    void bothContextsCreated() {
        assertNotNull(tasksContext);
        assertNotNull(ordersContext);
    }
}
