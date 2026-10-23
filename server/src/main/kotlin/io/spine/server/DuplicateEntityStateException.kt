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

package io.spine.server

import io.spine.core.BoundedContextName
import io.spine.type.TypeUrl
import java.io.Serial

/**
 * An exception thrown when more than one open domain Bounded Context of a JVM serves
 * entities with the same state type.
 *
 * All Bounded Contexts of a JVM share one [Delivery][io.spine.server.delivery.Delivery],
 * which finds the entities that receive a message by their state type only. So two contexts
 * serving one state type would receive the messages of each other.
 *
 * Creates a new exception reporting the given state type and the contexts that serve it,
 * or are about to serve it.
 *
 * @param stateType The state type of the entities.
 * @param contexts The names of the contexts that serve the type, or are about to serve it.
 * @see ServerEnvironment.TypeConfigurator.checkDuplicateEntityStates
 */
public class DuplicateEntityStateException(
    stateType: TypeUrl,
    contexts: Collection<BoundedContextName>
) : RuntimeException(messageOf(stateType, contexts)) {

    private companion object {

        @Serial
        private const val serialVersionUID: Long = 0L

        private fun messageOf(stateType: TypeUrl, contexts: Collection<BoundedContextName>) =
            "The entity state type `${stateType.typeName()}` cannot be served by more than" +
                    " one open domain Bounded Context at a time: ${quoted(contexts)}." +
                    " Serve each entity state type in one context only, or close the context" +
                    " that serves it before adding the type to another one."

        private fun quoted(contexts: Collection<BoundedContextName>) =
            contexts.joinToString { "`${it.value}`" }
    }
}
