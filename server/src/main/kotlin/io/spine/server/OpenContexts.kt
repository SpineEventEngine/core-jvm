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

/**
 * The open domain Bounded Contexts of the JVM and the guard of their creation.
 *
 * The Bounded Contexts of a JVM work together: they share one `Delivery`, one transport,
 * and one storage factory. So an open domain context must have a name of its own and serve
 * entity state types of its own. See [DuplicateContextNameException] for why names matter.
 *
 * The object enforces both rules in three steps:
 *
 * - [checkNameFree] throws [DuplicateContextNameException] if an open context has the name
 *   of a context about to be built. It runs before anything is built: a System context,
 *   for example, registers the inboxes of its repositories as soon as it is built.
 * - [opened] records the new context before its repositories are registered, so that its name
 *   is taken while they register.
 * - [serve] records the state type of a repository added to a context. It throws
 *   [DuplicateEntityStateException] if another open context already serves the type.
 *
 * Checking the name and recording the context are two steps. Two threads that build contexts
 * with one name at the same time may both pass the check. Building contexts is rarely that
 * concurrent, so this object does not guard against it.
 *
 * A closed context is no longer open, so another context may have its name and serve its
 * entity state types. See [BoundedContext] on what this means for tests.
 *
 * System contexts are not recorded. Their names follow the names of their domain contexts,
 * so [checkNameFree] checks them through their domain contexts. All of them serve the same
 * entity state types.
 *
 * Contexts are told apart by identity, not by [name][BoundedContext.equals].
 */
internal object OpenContexts {

    /**
     * The open domain contexts.
     *
     * A list rather than a set, because a set would compare the contexts by
     * [name][BoundedContext.equals], and this object tells them apart by identity.
     */
    private val contexts = mutableListOf<BoundedContext>()

    /**
     * The record of which open domain context serves which entity state type.
     */
    private val servedStateTypes = ServedStateTypes()

    /**
     * Checks that no open context has the given name, or the name of the System context
     * that a domain context with the given name gets.
     *
     * The System contexts of the open domain contexts count too.
     *
     * @throws DuplicateContextNameException If an open context has one of the names.
     */
    @JvmStatic
    fun checkNameFree(name: BoundedContextName) {
        synchronized(contexts) {
            val taken = contexts.flatMap { listOf(it.name(), it.name().toSystem()) }
            listOf(name, name.toSystem())
                .firstOrNull { it in taken }
                ?.let { throw DuplicateContextNameException(it) }
        }
    }

    /**
     * Records that the given context, which has just been built, is open.
     */
    @JvmStatic
    fun opened(context: BoundedContext) {
        synchronized(contexts) {
            contexts.add(context)
        }
    }

    /**
     * Records that the given context serves entities with the given state type.
     *
     * @param stateType The state type of the entities of the repository.
     * @param context The context to which a repository is being added.
     * @return `true` if this call recorded the context for the type,
     *   `false` if the context had been recorded for it before.
     * @throws DuplicateEntityStateException If another open context already serves the type.
     */
    @JvmStatic
    fun serve(stateType: TypeUrl, context: BoundedContext): Boolean =
        servedStateTypes.serve(stateType, context)

    /**
     * Records that the given context no longer serves entities with the given state type.
     *
     * Does nothing if the context is not recorded for the type.
     */
    @JvmStatic
    fun forget(stateType: TypeUrl, context: BoundedContext) =
        servedStateTypes.forget(stateType, context)

    /**
     * Records that the given context, which is closing, is no longer open
     * and no longer serves any entities.
     *
     * Does nothing for a context that is not recorded.
     */
    @JvmStatic
    fun closed(context: BoundedContext) {
        servedStateTypes.forget(context)
        synchronized(contexts) {
            contexts.removeAll { it === context }
        }
    }

    /**
     * Obtains the open context that serves entities with the given state type,
     * or `null` if no open context serves it.
     */
    @JvmStatic
    fun findContextServing(stateType: TypeUrl): BoundedContext? =
        servedStateTypes.contextServing(stateType)
}
