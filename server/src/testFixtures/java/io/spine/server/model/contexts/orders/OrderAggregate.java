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

package io.spine.server.model.contexts.orders;

import io.spine.server.aggregate.Aggregate;
import io.spine.server.aggregate.Apply;
import io.spine.server.command.Assign;
import io.spine.test.model.contexts.orders.Order;
import io.spine.test.model.contexts.orders.OrderId;
import io.spine.test.model.contexts.orders.command.AddItem;
import io.spine.test.model.contexts.orders.command.CreateOrder;
import io.spine.test.model.contexts.orders.event.ItemAdded;
import io.spine.test.model.contexts.orders.event.OrderCreated;

class OrderAggregate extends Aggregate<OrderId, Order, Order.Builder> {

    private OrderAggregate(OrderId id) {
        super(id);
    }

    @Assign
    OrderCreated handle(CreateOrder cmd) {
        return OrderCreated
                .newBuilder()
                .setId(cmd.getId())
                .setOrder(Order.newBuilder()
                               .setId(cmd.getId())
                               .setName(cmd.getName()))
                .build();
    }

    @Apply
    private void event(OrderCreated event) {
        builder().mergeFrom(event.getOrder());
    }

    @Assign
    ItemAdded handle(AddItem cmd) {
        return ItemAdded
                .newBuilder()
                .setId(cmd.getId())
                .setItem(cmd.getItem())
                .build();
    }

    @Apply
    private void event(ItemAdded event) {
        builder().addItem(event.getItem());
    }
}
