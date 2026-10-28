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
        try (var context = BlackBox.from(
                BoundedContextBuilder.assumingTests()
                                     .add(OrderAggregate.class)
                                     .add(new CaffetteriaStatsRepository())
                                     .addCommandFilter(new BeachCustomerFilter())
        )) {
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
