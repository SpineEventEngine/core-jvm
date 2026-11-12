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

package io.spine.system.server;

import io.spine.server.BoundedContext;
import io.spine.test.system.server.MealOrder;
import io.spine.test.system.server.OrderId;
import io.spine.test.system.server.OrderPlaced;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static io.spine.system.server.given.client.SystemClientTestEnv.contextWithMealOrders;
import static io.spine.system.server.given.client.SystemClientTestEnv.findProjection;

@DisplayName("Default implementation of `SystemWriteSide` should")
class DefaultSystemWriteSideTest {

    private SystemWriteSide systemWriteSide;
    private OrderId projectionId;

    @BeforeEach
    void setUp() {
        projectionId = OrderId.generate();
    }

    @Nested
    @DisplayName("post system")
    class PostMessages {

        private BoundedContext domainContext;

        @BeforeEach
        void setUp() {
            domainContext = contextWithMealOrders();
            systemWriteSide = domainContext.systemClient()
                                           .writeSide();
        }

        @AfterEach
        void tearDown() throws Exception {
            domainContext.close();
        }

        @Test
        @DisplayName("events")
        void events() {
            var event = OrderPlaced.newBuilder()
                    .setId(projectionId)
                    .addItem("Pizza")
                    .build();
            systemWriteSide.postEvent(event);

            var order = projectionState();
            assertThat(order.getWhenPlaced())
                    .isNotEqualToDefaultInstance();
            assertThat(order.getItemList())
                    .isEqualTo(event.getItemList());
        }

        private MealOrder projectionState() {
            var aggregate = findProjection(projectionId, domainContext);
            return aggregate.state();
        }
    }
}
