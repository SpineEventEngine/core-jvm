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
import io.spine.server.delivery.event.ShardProcessingRequested
import io.spine.server.procman.ProcessManagerRepository
import io.spine.server.route.EventRouting

/**
 * The repository of the shard maintenance processes of a System context.
 *
 * Every System context registers one.
 */
@Internal
public class ShardMaintenanceRepository :
    ProcessManagerRepository<ShardMaintenanceId, ShardMaintenanceProcess, ShardMaintenance>() {

    /**
     * Routes each request to process a shard to the process of the shard
     * and the catch-up job that asked for it.
     */
    override fun setupEventRouting(routing: EventRouting<ShardMaintenanceId>) {
        super.setupEventRouting(routing)
        routing.unicast<ShardProcessingRequested> { idOf(it) }
    }

    /**
     * Returns `false`, as the processes should not be available for querying,
     * and their events should not be available for subscribing.
     */
    override fun isTypeSupplier(): Boolean = false

    private companion object {

        fun idOf(event: ShardProcessingRequested): ShardMaintenanceId = shardMaintenanceId {
            shard = event.index
            job = event.process
        }
    }
}
