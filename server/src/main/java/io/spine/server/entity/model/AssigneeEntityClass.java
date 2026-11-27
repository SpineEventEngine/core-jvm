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

    public boolean handlesCommand(CommandClass commandClass) {
        return commands.containsClass(commandClass);
    }

    @Override
    public AssigneeReceptor receptorOf(CommandEnvelope cmd) {
        return commands.receptorFor(cmd);
    }
}
