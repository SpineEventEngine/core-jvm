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

package io.spine.server.entity.model;

import com.google.common.collect.ImmutableSet;
import io.spine.server.command.model.AssigneeReceptor;
import io.spine.server.command.model.AssigneeSignature;
import io.spine.server.command.model.CommandHandlingClass;
import io.spine.server.entity.Entity;
import io.spine.server.model.ReceptorMap;
import io.spine.server.type.CommandClass;
import io.spine.server.type.CommandEnvelope;
import io.spine.server.type.EventClass;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

/**
 * Abstract base for entity classes that have methods
 * {@link io.spine.server.command.Assign assigned} to handle commands.
 *
 * @param <E>
 *         the type of entity
 */
public abstract class AssigneeEntityClass<E extends Entity<?, ?>>
        extends EntityClass<E>
        implements CommandHandlingClass<EventClass, AssigneeReceptor> {

    private final ReceptorMap<CommandClass, EventClass, AssigneeReceptor> commands;

    protected AssigneeEntityClass(Class<E> cls) {
        super(cls);
        this.commands = ReceptorMap.create(cls, new AssigneeSignature());
    }

    @Override
    public ImmutableSet<CommandClass> commands() {
        return commands.messageClasses();
    }

    @Override
    public ImmutableSet<EventClass> rejections() {
        var result = commands.methods()
                .stream()
                .flatMap(m -> m.rejections().stream())
                .collect(toImmutableSet());
        return result;
    }

    @Override
    public ImmutableSet<EventClass> commandOutput() {
        return commands.producedTypes();
    }

    public boolean handlesCommand(CommandClass commandClass) {
        return commands.containsClass(commandClass);
    }

    @Override
    public AssigneeReceptor receptorOf(CommandEnvelope cmd) {
        return commands.receptorFor(cmd);
    }
}
