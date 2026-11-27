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
import io.spine.server.type.CommandClass;
import io.spine.server.type.CommandEnvelope;

/**
 * A common interface for classes that handle commands.
 *
 * @param <R>
 *         the type of receptors that accept commands
 */
public interface CommandHandlingClass<R extends CommandReceptor<?>> {

    /**
     * Obtains classes of commands handled by the class.
     */
    ImmutableSet<CommandClass> commands();

    /**
     * Obtains the receptor for the type of the passed command.
     */
    R receptorOf(CommandEnvelope command);
}
