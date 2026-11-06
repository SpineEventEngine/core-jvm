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
import io.spine.server.projection.ProjectionRepository;
import io.spine.server.route.EventRouting;

import java.util.Set;

import static io.spine.server.route.EventRoute.noTargets;
import static io.spine.server.route.EventRoute.withId;

/**
 * The repository for {@link CommandLogProjection}s.
 */
final class CommandLogRepository
        extends ProjectionRepository<CommandId, CommandLogProjection, CommandLog> {

    /**
     * Routes the unexpected failure of a handler to the log of the handled command.
     *
     * <p>The first field of {@link HandlerFailedUnexpectedly} is the ID of the failed entity,
     * so the default routing cannot find the log. A failure that happened while handling
     * an event is not routed to any log.
     */
    @Override
    protected void setupEventRouting(EventRouting<CommandId> routing) {
        super.setupEventRouting(routing);
        routing.route(HandlerFailedUnexpectedly.class,
                      (event, context) -> handledCommand(event));
    }

    private static Set<CommandId> handledCommand(HandlerFailedUnexpectedly event) {
        var signal = event.getHandledSignal();
        return signal.isCommand()
               ? withId(signal.asCommandId())
               : noTargets();
    }
}
