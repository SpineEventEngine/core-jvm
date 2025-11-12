/*
 * Copyright 2025, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
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

package io.spine.server.procman.model;

import com.google.common.collect.ImmutableSet;
import io.spine.server.command.model.CommandSubstituter;
import io.spine.server.command.model.CommanderClass;
import io.spine.server.command.model.CommandingClass;
import io.spine.server.command.model.CommandingReaction;
import io.spine.server.entity.model.AssigneeEntityClass;
import io.spine.server.event.model.EventReactorMethod;
import io.spine.server.event.model.ReactingClass;
import io.spine.server.event.model.ReactorClassDelegate;
import io.spine.server.procman.ProcessManager;
import io.spine.server.type.CommandClass;
import io.spine.server.type.CommandEnvelope;
import io.spine.server.type.EventClass;
import io.spine.server.type.EventEnvelope;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Sets.union;

/**
 * Provides message-handling information on a {@code ProcessManager} class.
 *
 * @param <P>
 *         the type of process managers
 */
public final class ProcessManagerClass<P extends ProcessManager<?, ?, ?>>
        extends AssigneeEntityClass<P>
        implements ReactingClass, CommandingClass {

    private final ReactorClassDelegate<P> reactorDelegate;
    private final CommanderClass<P> commanderDelegate;

    private ProcessManagerClass(Class<P> cls) {
        super(cls);
        this.reactorDelegate = new ReactorClassDelegate<>(cls);
        this.commanderDelegate = CommanderClass.delegateFor(cls);
    }

    /**
     * Obtains the process manager class for the passed raw class.
     */
    public static <P extends ProcessManager<?, ?, ?>>
    ProcessManagerClass<P> asProcessManagerClass(Class<P> cls) {
        checkNotNull(cls);
        @SuppressWarnings("unchecked")
        var result = (ProcessManagerClass<P>)
                get(cls, ProcessManagerClass.class, () -> new ProcessManagerClass<>(cls));
        return result;
    }

    @Override
    public ImmutableSet<CommandClass> commands() {
        var result = union(super.commands(), commanderDelegate.commands());
        return result.immutableCopy();
    }

    @Override
    public ImmutableSet<EventClass> events() {
        var result = union(reactorDelegate.events(), commanderDelegate.events());
        return result.immutableCopy();
    }

    @Override
    public ImmutableSet<EventClass> domesticEvents() {
        var result =
                union(reactorDelegate.domesticEvents(), commanderDelegate.domesticEvents());
        return result.immutableCopy();
    }

    @Override
    public ImmutableSet<EventClass> externalEvents() {
        var result = union(reactorDelegate.externalEvents(), commanderDelegate.externalEvents());
        return result.immutableCopy();
    }

    /**
     * Obtains event classes produced by this process manager class.
     */
    public ImmutableSet<EventClass> outgoingEvents() {
        var methodResults = union(commandOutput(), reactionOutput());
        var result = union(methodResults, rejections());
        return result.immutableCopy();
    }

    @Override
    public Optional<EventReactorMethod> reactorOf(EventEnvelope event) {
        return reactorDelegate.reactorOf(event);
    }

    @Override
    public ImmutableSet<EventClass> reactionOutput() {
        return reactorDelegate.reactionOutput();
    }

    @Override
    public ImmutableSet<CommandClass> outgoingCommands() {
        return commanderDelegate.outgoingCommands();
    }

    /**
     * Obtains a method which handles the passed class of commands by producing
     * one or more other commands.
     */
    public CommandSubstituter commanderOf(CommandEnvelope command) {
        return commanderDelegate.receptorOf(command);
    }

    /**
     * Obtains a method which may generate one or more commands in response to incoming
     * event with the passed class.
     */
    public Optional<CommandingReaction> commanderOf(EventEnvelope event) {
        return commanderDelegate.commanderOn(event);
    }

    /**
     * Verifies if the process manager class has a method which generates one or more
     * commands in response to a command of the passed class.
     */
    public boolean substitutesCommand(CommandClass commandClass) {
        return commanderDelegate.substitutesCommand(commandClass);
    }

    /**
     * Verifies if the class of process managers react on an event of the passed class.
     */
    public boolean reactsOnEvent(EventClass eventClass) {
        return reactorDelegate.contains(eventClass);
    }

    /**
     * Verifies if the process manager class generates a command in response to
     * an event of the passed class.
     */
    public boolean producesCommandsOn(EventClass eventClass) {
        return commanderDelegate.producesCommandsOn(eventClass);
    }
}
