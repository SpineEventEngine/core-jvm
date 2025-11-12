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

package io.spine.server.command.model;

import com.google.common.collect.ImmutableSet;
import io.spine.server.command.AbstractCommander;
import io.spine.server.command.Commander;
import io.spine.server.event.model.EventReceiverClass;
import io.spine.server.event.model.EventReceivingClassDelegate;
import io.spine.server.model.ExternalCommandReceiverMethodError;
import io.spine.server.model.Receptor;
import io.spine.server.type.CommandClass;
import io.spine.server.type.EventClass;
import io.spine.server.type.EventEnvelope;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Sets.union;
import static java.util.stream.Collectors.toSet;

/**
 * Provides information on message handling for a class of {@link Commander}s.
 *
 * @param <C>
 *         the type of commanders
 */
public final class CommanderClass<C extends Commander>
        extends AbstractCommandHandlingClass<C, CommandClass, CommandSubstituter>
        implements EventReceiverClass, CommandingClass {

    private final EventReceivingClassDelegate<C, CommandClass, CommandingReaction> delegate;

    private CommanderClass(Class<C> cls) {
        super(cls, new CommandSubstituteSignature());
        this.delegate = new EventReceivingClassDelegate<>(cls, new CommandReactionSignature());
        validateExternalMethods();
    }

    public static <C extends Commander> CommanderClass<C> delegateFor(Class<C> cls) {
        checkNotNull(cls);
        var result = new CommanderClass<>(cls);
        return result;
    }

    public static <C extends AbstractCommander>
    CommanderClass<C> asCommanderClass(Class<C> cls) {
        checkNotNull(cls);
        @SuppressWarnings("unchecked")
        var result = (CommanderClass<C>)
                get(cls, CommanderClass.class, () -> new CommanderClass<>(cls));
        return result;
    }

    @Override
    public ImmutableSet<EventClass> events() {
        return delegate.events();
    }

    @Override
    public ImmutableSet<EventClass> domesticEvents() {
        return delegate.domesticEvents();
    }

    @Override
    public ImmutableSet<EventClass> externalEvents() {
        return delegate.externalEvents();
    }

    /**
     * Obtains the method which reacts on the passed event class.
     */
    public Optional<CommandingReaction> commanderOn(EventEnvelope event) {
        return delegate.findReceptorOf(event);
    }

    /**
     * Tells if instances of this commander class substitute the commands of the passed class.
     */
    public boolean substitutesCommand(CommandClass commandClass) {
        return hasReceptor(commandClass);
    }

    /**
     * Tells if instances of this commander class produce commands in response to
     * the passed event class.
     */
    public boolean producesCommandsOn(EventClass eventClass) {
        return delegate.contains(eventClass);
    }

    @Override
    public ImmutableSet<CommandClass> outgoingCommands() {
        var result = union(commandOutput(), delegate.producedTypes());
        return result.immutableCopy();
    }

    /**
     * Ensures no {@linkplain io.spine.core.External external} command substitution methods in
     * the class.
     *
     * <p>Command substitution methods accept {@linkplain io.spine.base.CommandMessage commands} as
     * input and there is no notion of "external" commands in the system. Thus, such method
     * declarations, although technically possible, should be avoided to prevent confusion.
     *
     * @throws ExternalCommandReceiverMethodError
     *         in case external command substitution methods are found within the class
     */
    private void validateExternalMethods() {
        var methods = commands().stream()
                .flatMap(type -> receptorsForType(type).stream())
                .filter(Receptor::isExternal)
                .collect(toSet());
        if (!methods.isEmpty()) {
            throw new ExternalCommandReceiverMethodError(this, methods);
        }
    }
}
