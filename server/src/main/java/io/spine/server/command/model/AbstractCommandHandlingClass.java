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
import io.spine.server.model.ModelClass;
import io.spine.server.model.ReceptorMap;
import io.spine.server.type.CommandClass;
import io.spine.server.type.CommandEnvelope;
import io.spine.server.type.EventClass;
import io.spine.type.MessageClass;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

/**
 * Abstract base for classes providing message handling information of classes that handle commands.
 *
 * @param <C>
 *         the type of command handling class
 * @param <P>
 *         the type of the class of produced messages
 * @param <R>
 *         the type of the command receptors
 */
public abstract class AbstractCommandHandlingClass<C,
                                                   P extends MessageClass<?>,
                                                   R extends CommandReceptor<?, P>>
        extends ModelClass<C>
        implements CommandHandlingClass<P, R> {

    private final ReceptorMap<CommandClass, P, R> commands;

    AbstractCommandHandlingClass(Class<? extends C> cls,
                                 CommandAcceptingSignature<R> signature) {
        super(cls);
        this.commands = ReceptorMap.create(cls, signature);
    }

    @Override
    public ImmutableSet<CommandClass> commands() {
        return commands.messageClasses();
    }

    @Override
    public ImmutableSet<P> commandOutput() {
        return commands.producedTypes();
    }

    @Override
    public ImmutableSet<EventClass> rejections() {
        var result = commands.methods()
                .stream()
                .map(CommandReceptor::rejections)
                .flatMap(ImmutableSet::stream)
                .collect(toImmutableSet());
        return result;
    }

    /**
     * Obtains the receptor for the passed command.
     */
    @Override
    public R receptorOf(CommandEnvelope command) {
        return commands.receptorFor(command);
    }

    /**
     * Obtains receptors for the given class of commands.
     */
    ImmutableSet<R> receptorsForType(CommandClass cls) {
        return commands.receptorsOf(cls);
    }

    /**
     * Checks if this command handler has a receptor for the given class of commands.
     */
    boolean hasReceptor(CommandClass commandClass) {
        return commands.containsClass(commandClass);
    }
}
