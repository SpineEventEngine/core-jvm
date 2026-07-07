/*
 * Copyright 2026, TeamDev. All rights reserved.
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

package io.spine.server.entity.given.tryalter

import io.spine.server.command.Assign
import io.spine.server.event.NoReaction
import io.spine.server.event.React
import io.spine.server.procman.ProcessManager
import io.spine.server.procman.ProcessManagerRepository
import io.spine.server.tuple.EitherOf2
import io.spine.test.tryalter.Stock
import io.spine.test.tryalter.command.ReplenishStock
import io.spine.test.tryalter.event.BulkOrderPlaced
import io.spine.test.tryalter.event.StockReplenished
import io.spine.test.tryalter.event.StockReserved
import io.spine.test.tryalter.event.stockReplenished
import io.spine.test.tryalter.event.stockReserved

/**
 * A process manager maintaining a [Stock] — a state with declared constraints:
 * the number of items cannot go negative, and the vendor name is assigned only once.
 *
 * The reaction to [BulkOrderPlaced] guards the constraints preventively with
 * [tryAlter]: an order exceeding the stock is not applied, and no event is emitted.
 */
class StockKeeper(id: String) : ProcessManager<String, Stock, Stock.Builder>(id) {

    @Assign
    internal fun handle(command: ReplenishStock): StockReplenished {
        alter {
            inStock += command.quantity
        }
        return stockReplenished {
            stock = command.stock
            quantity = command.quantity
        }
    }

    @React
    internal fun on(event: BulkOrderPlaced): EitherOf2<StockReserved, NoReaction> {
        val violations = tryAlter {
            inStock -= event.quantity
        }
        return if (violations.isEmpty()) {
            EitherOf2.withA(
                stockReserved {
                    stock = id()
                    quantity = event.quantity
                }
            )
        } else {
            EitherOf2.withB(noReaction())
        }
    }
}

/**
 * The repository of [StockKeeper] process managers.
 */
class StockKeeperRepo : ProcessManagerRepository<String, StockKeeper, Stock>()
