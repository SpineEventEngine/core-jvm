/*
 * Copyright 2022, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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

package io.spine.server.command;

import io.spine.server.event.EventReceiver;

/**
 * An interface common for objects posting one or more commands in response to an incoming message.
 *
 * <p>Example of use case scenarios:
 * <ul>
 *     <li>Converting a command into another (e.g. because of command API changes).
 *     <li>Splitting a command which holds data for a bigger aggregate into several commands
 *     set to corresponding aggregate parts.
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
