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

package io.spine.server.commandbus;

import io.spine.server.bus.DispatcherRegistry;
import io.spine.server.type.CommandClass;
import io.spine.server.type.CommandEnvelope;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static io.spine.util.Exceptions.newIllegalArgumentException;

/**
 * The registry of objects dispatching command request to where they are processed.
 *
 * <p>There can be only one dispatcher per command class.
 */
public final class CommandDispatcherRegistry
        extends DispatcherRegistry<CommandClass, CommandEnvelope, CommandDispatcher> {

    /**
     * {@inheritDoc}
     *
     * <p>If an instance of {@link DelegatingCommandDispatcher} is passed to registration
     * and it does not {@linkplain DelegatingCommandDispatcher#messageClasses() expose}
     * command classes (because the underlying
     * {@link CommandDispatcherDelegate delegate} does not handle
     * commands), the repository is not registered.
     *
     * <p>No exceptions or log messages will be produced in this case.
     *
     * @param dispatcher the dispatcher to register
     */
    @Override
    public void register(CommandDispatcher dispatcher) {
        if (dispatcher instanceof DelegatingCommandDispatcher
                && dispatcher.messageClasses().isEmpty()) {
            return;
        }
        super.register(dispatcher);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Ensures that there are not dispatchers already registered for
     * the commands of this dispatcher.
     *
     * @param dispatcher
     *         the dispatcher to check
     * @throws IllegalStateException
     *          if there is at least one command of the passed dispatcher that already
     *          has a registered dispatcher
     */
    @Override
    protected void checkDispatcher(CommandDispatcher dispatcher)
            throws IllegalArgumentException {
        super.checkDispatcher(dispatcher);
        checkNotAlreadyRegistered(dispatcher);
    }

    /**
     * Ensures that all of the commands of the passed dispatcher are not
     * already registered for dispatched in this command bus.
     *
     * @throws IllegalArgumentException
     *          if at least one command class already has a registered dispatcher
     */
    private void checkNotAlreadyRegistered(CommandDispatcher dispatcher) {
        Set<CommandClass> commandClasses = dispatcher.messageClasses();
        Map<CommandClass, CommandDispatcher> alreadyRegistered = new HashMap<>();
        // Gather command classes from this dispatcher that are registered.
        for (var commandClass : commandClasses) {
            var registeredDispatcher  = getDispatcherForType(commandClass);
            registeredDispatcher.ifPresent(d -> alreadyRegistered.put(commandClass, d));
        }

        doCheck(alreadyRegistered, dispatcher);
    }

    /**
     * Ensures that the passed set of classes is empty.
     *
     * <p>This is a convenience method for checking registration of handling dispatching.
     *
     * @param alreadyRegistered
     *         the map of already registered classes or an empty set
     * @param registeringObject
     *         the object which tries to register dispatching or handling
     * @throws IllegalArgumentException
     *         if the set is not empty
     */
    private static void doCheck(Map<CommandClass, CommandDispatcher> alreadyRegistered,
                                Object registeringObject) {
        if (!alreadyRegistered.isEmpty()) {
            throw newIllegalArgumentException(
                    "Cannot register dispatcher (%s) because there are " +
                    "already registered dispatchers for the same command classes (%s).",
                    registeringObject,
                    alreadyRegistered);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Overrides to expose the method to
     * {@link CommandBus#registeredCommandClasses() CommandBus}.
     */
    @Override
    protected Set<CommandClass> registeredMessageClasses() {
        return super.registeredMessageClasses();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Overrides to expose the method to {@link CommandBus#close() CommandBus}.
     */
    @Override
    protected void unregisterAll() {
        super.unregisterAll();
    }
}
