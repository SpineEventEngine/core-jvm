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

import com.google.common.collect.ImmutableList
import io.spine.core.BoundedContextName
import io.spine.type.TypeUrl

/**
 * The record of which open Bounded Context serves which entity state type.
 *
 * When the check is on, the record lets only one open context serve a state type.
 * When the check is off, the record still keeps every context that serves the type,
 * so that [contextsServing] can tell about all of them.
 *
 * Contexts are told apart by identity, not by [name][BoundedContext.equals], because in tests
 * many contexts have the same name: [BoundedContextBuilder.assumingTests], which `BlackBox`
 * also uses, names each of them `AssumingTests`. Each of these contexts must be recorded.
 * In an application, two contexts with one name would be a mistake. Comparing by identity
 * lets the check report it, instead of taking the second context for the first one.
 */
internal class ServedStateTypes {

    private val contexts = mutableMapOf<TypeUrl, MutableList<BoundedContext>>()

    /**
     * Records that the given context serves entities with the given state type.
     *
     * @param context The context to which a repository is being added.
     * @param stateType The state type of the entities of the repository.
     * @param check If `true`, the context is not recorded when another open context
     *   already serves the type, and the method throws instead.
     * @return `true` if this call recorded the context for the type,
     *   `false` if the context had been recorded for it before.
     * @throws DuplicateEntityStateException If [check] is `true`, and another open context
     *   already serves the type.
     */
    @Synchronized
    fun serve(context: BoundedContext, stateType: TypeUrl, check: Boolean): Boolean {
        val serving = contexts.getOrPut(stateType) { mutableListOf() }
        if (serving.any { it === context }) {
            return false
        }
        if (check && serving.isNotEmpty()) {
            val names = serving.map { it.name() } + context.name()
            throw DuplicateEntityStateException(stateType, names)
        }
        serving.add(context)
        return true
    }

    /**
     * Removes the given context from the contexts that serve the given state type.
     *
     * Does nothing if the context is not recorded for the type.
     */
    @Synchronized
    fun forget(context: BoundedContext, stateType: TypeUrl) {
        val serving = contexts[stateType] ?: return
        serving.removeAll { it === context }
        if (serving.isEmpty()) {
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
        contexts.values.forEach { serving -> serving.removeAll { it === context } }
        contexts.values.removeAll { it.isEmpty() }
    }

    /**
     * Obtains the names of the open contexts that serve entities with the given state type,
     * in the order they were recorded.
     */
    @Synchronized
    fun contextsServing(stateType: TypeUrl): ImmutableList<BoundedContextName> =
        ImmutableList.copyOf(contexts[stateType].orEmpty().map { it.name() })
}
