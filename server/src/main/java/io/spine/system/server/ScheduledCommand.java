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

import io.spine.core.CommandId;
import io.spine.core.EventContext;
import io.spine.core.Subscribe;
import io.spine.server.projection.Projection;
import io.spine.system.server.event.CommandDispatched;
import io.spine.system.server.event.CommandScheduled;

/**
 * Information about a scheduled command.
 */
final class ScheduledCommand
        extends Projection<CommandId, ScheduledCommandRecord, ScheduledCommandRecord.Builder> {

    private ScheduledCommand(CommandId id) {
        super(id);
    }

    @Subscribe
    void on(CommandScheduled event, EventContext context) {
        // The record keeps the scheduling time in its own field,
        // so the command is stored as it was posted.
        var command = event.getPayload()
                .toBuilder()
                .clearSystemProperties()
                .build();
        builder().setId(event.getId())
                 .setCommand(command)
                 .setSchedulingTime(context.getTimestamp());
    }

    @Subscribe
    void on(@SuppressWarnings("unused") /* Defines the event type. */ CommandDispatched ignored) {
        setDeleted(true);
    }
}
