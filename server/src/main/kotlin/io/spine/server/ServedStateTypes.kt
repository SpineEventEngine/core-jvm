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

import io.spine.type.TypeUrl

/**
 * The record of which open Bounded Context serves which entity state type.
 *
 * The record lets only one open context serve a state type.
 *
 * Contexts are told apart by identity, not by [name][BoundedContext.equals], because in tests
 * many contexts have the same name: [BoundedContextBuilder.assumingTests], which `BlackBox`
 * also uses, names each of them `AssumingTests`. Comparing by name would take the second
 * of them for the first one, and a duplicate state type would go unreported. The same holds
 * in an application, where two contexts with one name would be a mistake.
 */
internal class ServedStateTypes {

    private val contexts = mutableMapOf<TypeUrl, BoundedContext>()

    /**
     * Records that the given context serves entities with the given state type.
     *
     * @param stateType The state type of the entities of the repository.
     * @param context The context to which a repository is being added.
     * @return `true` if this call recorded the context for the type,
     *   `false` if the context had been recorded for it before.
     * @throws DuplicateEntityStateException If another open context already serves the type.
     */
    @Synchronized
    fun serve(stateType: TypeUrl, context: BoundedContext): Boolean {
        val serving = contexts[stateType]
        if (serving === context) {
            return false
        }
        if (serving != null) {
            throw DuplicateEntityStateException(stateType, listOf(serving.name(), context.name()))
        }
        contexts[stateType] = context
        return true
    }

    /**
     * Removes the given context from the record of the given state type.
     *
     * Does nothing if the context is not recorded for the type.
     */
    @Synchronized
    fun forget(stateType: TypeUrl, context: BoundedContext) {
        if (contexts[stateType] === context) {
            contexts.remove(stateType)
        }
    }

    /**
     * Removes the given context from the record.
     *
     * Does nothing if the context is not recorded.
     */
    @Synchronized
    fun forget(context: BoundedContext) {
        contexts.values.removeAll { it === context }
    }

    /**
     * Obtains the open context that serves entities with the given state type,
     * or `null` if no open context serves it.
     */
    @Synchronized
    fun contextServing(stateType: TypeUrl): BoundedContext? = contexts[stateType]
}
