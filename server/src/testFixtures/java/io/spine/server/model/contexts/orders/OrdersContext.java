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

package io.spine.server.model.contexts.orders;

import io.spine.server.BoundedContext;
import io.spine.server.BoundedContextBuilder;

/**
 * Creates Orders Bounded Context.
 */
public final class OrdersContext {

    private OrdersContext() {
    }

    public static BoundedContext newInstance() {
        return BoundedContextBuilder.assumingTests("Orders")
                .add(OrderAggregate.class)
                .build();
    }
}
