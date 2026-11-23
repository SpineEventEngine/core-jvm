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

package io.spine.server.command;

import io.spine.server.event.EventReceiver;

/**
 * An interface common for objects posting one or more commands in response to an incoming message.
 *
 * <p>Examples of use case scenarios:
 * <ul>
 *     <li>Converting a command into another (e.g. because of command API changes).
 *     <li>Splitting a command that holds data for several aggregates into several commands,
 *     one for each aggregate.
 *     <li>Issuing a command in response to an event.
 *     <li>Posting a command to handle a rejection using a command posted on behalf of another user.
 * </ul>
 *
 * @see Command @Command
 */
public interface Commander extends CommandReceiver, EventReceiver {

    /**
     * Obtains the {@link DoNothing} command message.
     *
     * <p>This command should be returned if there is no value for the domain to produce an actual
     * command in response to an event.
     *
     * <p>This command is never posted into the {@link io.spine.server.commandbus.CommandBus
     * CommandBus}.
     *
     * @return the default instance of {@link DoNothing}
     */
    default DoNothing doNothing() {
        return DoNothing.getDefaultInstance();
    }
}
