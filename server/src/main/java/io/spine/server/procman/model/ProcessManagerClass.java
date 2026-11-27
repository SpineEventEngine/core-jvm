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
import io.spine.server.model.ModelClassValue;
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

    /**
     * Associates a {@code ProcessManagerClass} with a Java class of process managers.
     *
     * @see #asProcessManagerClass(Class)
     */
    @SuppressWarnings("unchecked") // `asProcessManagerClass()` accepts only process managers.
    private static final ModelClassValue<ProcessManagerClass<?>> classValue =
            new ModelClassValue<>(cls ->
                    new ProcessManagerClass<>((Class<ProcessManager<?, ?, ?>>) cls));

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
        @SuppressWarnings("unchecked") // The class value creates the model class of `cls`.
        var result = (ProcessManagerClass<P>) classValue.get(cls);
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

    @Override
    public Optional<EventReactorMethod> reactorOf(EventEnvelope event) {
        return reactorDelegate.reactorOf(event);
    }

    @Override
    public ImmutableSet<CommandClass> outgoingCommands() {
        return commanderDelegate.outgoingCommands();
    }

    /**
     * Obtains a method that handles the passed class of commands by producing
     * one or more other commands.
     */
    public CommandSubstituter commanderOf(CommandEnvelope command) {
        return commanderDelegate.receptorOf(command);
    }

    /**
     * Obtains a method that may generate one or more commands in response to an incoming
     * event with the passed class.
     */
    public Optional<CommandingReaction> commanderOf(EventEnvelope event) {
        return commanderDelegate.commanderOn(event);
    }

    /**
     * Verifies if the process manager class has a method that generates one or more
     * commands in response to a command of the passed class.
     */
    public boolean substitutesCommand(CommandClass commandClass) {
        return commanderDelegate.substitutesCommand(commandClass);
    }

    /**
     * Verifies if the class of process managers reacts on an event of the passed class.
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
