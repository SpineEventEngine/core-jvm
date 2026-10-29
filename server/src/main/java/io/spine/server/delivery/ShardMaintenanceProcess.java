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

package io.spine.server.delivery;

import io.spine.annotation.Internal;
import io.spine.server.delivery.event.ShardProcessed;
import io.spine.server.delivery.event.ShardProcessingRequested;
import io.spine.server.event.React;
import io.spine.server.procman.ProcessManager;
import io.spine.type.TypeUrl;

/**
 * The framework-internal process performing the maintenance of delivery shards.
 *
 * <p>Every System context has a repository of these processes. There is a process for each
 * pair of a shard and a catch-up job that asked for that shard to be processed.
 * The job in the ID tells the {@link Delivery} which System context handles the request.
 *
 * <p>The state of the process holds only its ID.
 */
@Internal
public final class ShardMaintenanceProcess
        extends ProcessManager<ShardMaintenanceId, ShardMaintenance, ShardMaintenance.Builder> {

    static final TypeUrl TYPE = TypeUrl.of(ShardMaintenance.getDefaultInstance());

    /**
     * Creates a new instance of the process with the given ID.
     */
    ShardMaintenanceProcess(ShardMaintenanceId id) {
        super(id);
    }

    /**
     * By handling this event, guarantees that the messages in the
     * {@linkplain ShardProcessingRequested#getIndex() specified shard} have been processed
     * by the {@link Delivery}.
     */
    @React
    ShardProcessed on(ShardProcessingRequested event) {
        var processed = ShardProcessed.newBuilder()
                .setIndex(event.getIndex())
                .setProcess(event.getProcess())
                .setRunInfo(event.getRunInfo())
                .build();
        return processed;
    }
}
