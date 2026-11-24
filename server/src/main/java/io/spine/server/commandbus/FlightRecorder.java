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

import io.spine.base.EventMessage;
import io.spine.server.type.CommandEnvelope;
import io.spine.system.server.WriteSideFunction;
import io.spine.system.server.event.CommandDispatched;
import io.spine.system.server.event.CommandScheduled;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Records a command log by posting the corresponding events into the
 * {@link io.spine.system.server.SystemContext system context}.
 */
final class FlightRecorder implements CommandFlowWatcher {

    private final WriteSideFunction function;

    FlightRecorder(WriteSideFunction function) {
        this.function = checkNotNull(function);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Posts the {@link CommandDispatched} system event.
     */
    @Override
    public void onDispatchCommand(CommandEnvelope command) {
        var systemEvent = CommandDispatched.newBuilder()
                .setId(command.id())
                .build();
        postSystemEvent(systemEvent, command);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Posts the {@link CommandScheduled} system event.
     */
    @Override
    public void onScheduled(CommandEnvelope command) {
        var context = command.context();
        var schedule = context.getSchedule();
        var systemEvent = CommandScheduled.newBuilder()
                .setId(command.id())
                .setSchedule(schedule)
                .setPayload(command.outerObject())
                .build();
        postSystemEvent(systemEvent, command);
    }

    private void postSystemEvent(EventMessage systemEvent, CommandEnvelope cmd) {
        var writeSide = function.get(cmd.tenantId());
        writeSide.postEvent(systemEvent, cmd.asMessageOrigin());
    }
}
