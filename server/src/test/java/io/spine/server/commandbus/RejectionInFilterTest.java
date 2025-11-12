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

package io.spine.server.commandbus;

import io.spine.server.BoundedContextBuilder;
import io.spine.server.commandbus.given.caffetteria.BeachCustomerFilter;
import io.spine.server.commandbus.given.caffetteria.CaffetteriaStats;
import io.spine.server.commandbus.given.caffetteria.CaffetteriaStatsRepository;
import io.spine.server.commandbus.given.caffetteria.OrderAggregate;
import io.spine.test.commandbus.CmdBusCaffetteriaId;
import io.spine.test.commandbus.CmdBusCaffetteriaStats;
import io.spine.test.commandbus.CmdBusOrderId;
import io.spine.test.commandbus.command.CmdBusAllocateTable;
import io.spine.test.commandbus.command.Visitors;
import io.spine.testing.server.blackbox.BlackBox;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("`CommandBus`, when a `Rejection` is thrown from a bus filter, should")
class RejectionInFilterTest {

    @Test
    @DisplayName("post this rejection to `EventBus`")
    void postRejection() {
        var context = BlackBox.from(
                BoundedContextBuilder.assumingTests()
                                     .add(OrderAggregate.class)
                                     .add(new CaffetteriaStatsRepository())
                                     .addCommandFilter(new BeachCustomerFilter())
        );
        var caffetteria = CmdBusCaffetteriaId.generate();
        var allowedCount = 2;
        var visitorsAllowedToEnter = Visitors.newBuilder()
                .setCount(allowedCount)
                .setBringOwnFood(false)
                .build();
        var deniedCount = 4;
        var visitorsDeniedEntrance = Visitors.newBuilder()
                .setCount(deniedCount)
                .setBringOwnFood(true)
                .build();
        context
                .receivesCommand(
                        allocateTableForVisitors(caffetteria, visitorsAllowedToEnter)
                )
                .receivesCommand(
                        allocateTableForVisitors(caffetteria, visitorsDeniedEntrance)
                );
        context.assertEntity(caffetteria, CaffetteriaStats.class)
               .hasStateThat()
               .comparingExpectedFieldsOnly()
               .isEqualTo(
                       CmdBusCaffetteriaStats.newBuilder()
                               .setVisitorCount(allowedCount)
                               .setEntryDenied(deniedCount)
                               .buildPartial()
               );
    }

    private static CmdBusAllocateTable allocateTableForVisitors(CmdBusCaffetteriaId caffetteria,
                                                                Visitors visitorsDenied) {
        return CmdBusAllocateTable.newBuilder()
                .setId(CmdBusOrderId.generate())
                .setCaffetteria(caffetteria)
                .setVisitors(visitorsDenied)
                .build();
    }
}
