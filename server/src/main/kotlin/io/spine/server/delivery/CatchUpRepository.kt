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

package io.spine.server.delivery

import io.spine.annotation.Internal
import io.spine.server.ServerEnvironment
import io.spine.server.delivery.event.ShardProcessed
import io.spine.server.procman.ProcessManagerRepository
import io.spine.server.route.EventRouting

/**
 * The repository of the catch-up processes of a System context.
 *
 * Every System context registers one. All of them store the processes in the same
 * [CatchUpStorage], that of the [Delivery].
 */
@Internal
public class CatchUpRepository :
    ProcessManagerRepository<CatchUpId, CatchUpProcess, CatchUp>() {

    /**
     * Returns the [CatchUpStorage] of the `Delivery` of the current environment.
     */
    override fun createStorage(): CatchUpStorage =
        ServerEnvironment.instance()
            .delivery()
            .catchUpStorage()

    /**
     * Routes the report that a shard is processed to the process that asked for it.
     */
    override fun setupEventRouting(routing: EventRouting<CatchUpId>) {
        super.setupEventRouting(routing)
        routing.unicast<ShardProcessed> { it.process }
    }

    /**
     * Returns `false`, as the processes should not be available for querying,
     * and their events should not be available for subscribing.
     */
    override fun isTypeSupplier(): Boolean = false
}
