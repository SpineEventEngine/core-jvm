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
 * The object guards both rules:
 *
 * - [open] builds a context only if no other open context has its name. Otherwise, it throws
 *   [DuplicateContextNameException] before anything is built.
 * - [serve] records the state type of a repository added to a context. It throws
 *   [DuplicateEntityStateException] if another open context already serves the type.
 *
 * Closing a context frees its name and its types. So a test should close the contexts
 * it builds. Otherwise, the contexts that later tests build cannot reuse those names and types.
 *
 * System contexts are not recorded. Their names follow the names of their domain contexts,
 * and all of them serve the same entity state types.
 *
 * Contexts are told apart by identity, not by [name][BoundedContext.equals]. A context
 * frees only a name bound to it. A context closed by its failed build holds no name:
 * its name is only reserved, and [open] releases it.
 */
internal object OpenContexts {

    /**
     * The open domain contexts by their names.
     *
     * Guards [building] too.
     */
    private val names = mutableMapOf<BoundedContextName, BoundedContext>()

    /**
     * The names reserved for the contexts being built.
     */
    private val building = mutableSetOf<BoundedContextName>()

    /**
     * The record of which open domain context serves which entity state type.
     */
    private val servedStateTypes = ServedStateTypes()

    /**
     * Builds a domain context with the given name, unless another open context has the name.
     *
     * Reserves the name before [build] runs, so that nothing is built for a name that is
     * taken. For example, a System context registers the inboxes of its repositories as soon
     * as it is built. Binds the name to the built context, or releases it if [build] throws.
     *
     * @param C The type of the context.
     * @param name The name of the context to build.
     * @param build Builds the context.
     * @return The built context.
     * @throws DuplicateContextNameException If another open context has the name,
     *   or is being built with it.
     */
    @JvmStatic
    fun <C : BoundedContext> open(name: BoundedContextName, build: () -> C): C {
        reserve(name)
        var opened = false
        try {
            val context = build()
            bind(name, context)
            opened = true
            return context
        } finally {
            if (!opened) {
                release(name)
            }
        }
    }

    private fun reserve(name: BoundedContextName) {
        synchronized(names) {
            if (name in names || name in building) {
                throw DuplicateContextNameException(name)
            }
            building.add(name)
        }
    }

    /**
     * Binds the reserved name to the built context.
     *
     * Releases the name instead if the context was closed while it was being built,
     * because a closed context never frees a name again.
     */
    private fun bind(name: BoundedContextName, context: BoundedContext) {
        synchronized(names) {
            building.remove(name)
            if (context.isOpen) {
                names[name] = context
            }
        }
    }

    private fun release(name: BoundedContextName) {
        synchronized(names) {
            building.remove(name)
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
     * Records that the given context, which is closing, frees its name
     * and no longer serves any entities.
     *
     * Does nothing for a context that is not recorded, such as a System context.
     *
     * Forgets the types before it frees the name. So a new context that gets the name can
     * also serve the types.
     */
    @JvmStatic
    fun closed(context: BoundedContext) {
        servedStateTypes.forget(context)
        val name = context.name()
        synchronized(names) {
            if (names[name] === context) {
                names.remove(name)
            }
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
