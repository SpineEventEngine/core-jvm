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

import io.spine.server.bus.DeadMessageHandler;
import io.spine.server.type.CommandEnvelope;
import io.spine.system.server.WriteSideFunction;
import io.spine.system.server.event.UnsupportedCommandReceived;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Records a dead command as {@link UnsupportedCommandReceived} in the System context.
 */
final class DeadCommandHandler implements DeadMessageHandler<CommandEnvelope> {

    private final WriteSideFunction writeSideFunction;

    DeadCommandHandler(WriteSideFunction writeSideFunction) {
        this.writeSideFunction = checkNotNull(writeSideFunction);
    }

    @Override
    public void handle(CommandEnvelope command) {
        var unsupported = UnsupportedCommandReceived.newBuilder()
                .setId(command.id())
                .setTypeUrl(command.command().enclosedTypeUrl().value())
                .build();
        writeSideFunction.get(command.tenantId())
                         .postEvent(unsupported, command.asMessageOrigin());
    }
}
