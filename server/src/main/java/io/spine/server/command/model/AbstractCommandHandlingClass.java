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

package io.spine.server.command.model;

import com.google.common.collect.ImmutableSet;
import io.spine.server.model.ModelClass;
import io.spine.server.model.ReceptorMap;
import io.spine.server.type.CommandClass;
import io.spine.server.type.CommandEnvelope;


/**
 * Abstract base for classes providing message handling information on classes that handle commands.
 *
 * @param <C>
 *         the type of command handling class
 * @param <R>
 *         the type of the command receptors
 */
public abstract class AbstractCommandHandlingClass<C, R extends CommandReceptor<?>>
        extends ModelClass<C>
        implements CommandHandlingClass<R> {

    private final ReceptorMap<CommandClass, R> commands;

    AbstractCommandHandlingClass(Class<? extends C> cls,
                                 CommandAcceptingSignature<R> signature) {
        super(cls);
        this.commands = ReceptorMap.create(cls, signature);
    }

    @Override
    public ImmutableSet<CommandClass> commands() {
        return commands.messageClasses();
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
