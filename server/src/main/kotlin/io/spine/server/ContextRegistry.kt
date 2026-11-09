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
 * The registry of the domain Bounded Contexts of an [Application].
 *
 * A context is registered when it is built, and unregistered when it closes. A registered
 * context must have a name of its own, and serve entity state types of its own.
 * The registry enforces both rules in three steps:
 *
 * - [checkNameFree] throws [DuplicateContextNameException] if a registered context has
 *   the name of a context about to be built. It runs before anything is built: a System
 *   context, for example, registers the inboxes of its repositories as soon as it is built.
 * - [register] records the new context before its repositories are added, so that its name
 *   is taken while they are added.
 * - [serve] records the state type of a repository added to a context. It throws
 *   [DuplicateEntityStateException] if another registered context already serves the type.
 *
 * Checking the name and registering the context are two steps. Two threads that build
 * contexts with one name at the same time may both pass the check. Building contexts is
 * rarely that concurrent, so the registry does not guard against it.
 *
 * System contexts are not registered. Their names follow the names of their domain contexts,
 * so [checkNameFree] checks them through their domain contexts. All of them serve the same
 * entity state types. A domain context closes its System context.
 *
 * Contexts are told apart by identity, not by [name][BoundedContext.equals].
 */
internal class ContextRegistry {

    /**
     * The registered contexts, in the order of their registration.
     *
     * A list rather than a set, because a set would compare the contexts by
     * [name][BoundedContext.equals], and the registry tells them apart by identity.
     */
    private val contexts = mutableListOf<BoundedContext>()

    /**
     * The record of which registered context serves which entity state type.
     */
    private val servedStateTypes = ServedStateTypes()

    /**
     * Tells if new contexts may be registered.
     */
    @Volatile
    private var accepting = true

    /**
     * Checks that no registered context has the given name, or the name of the System context
     * that a domain context with the given name gets.
     *
     * The System contexts of the registered contexts count too.
     *
     * @throws IllegalStateException If the application is closed.
     * @throws DuplicateContextNameException If a registered context has one of the names.
     */
    fun checkNameFree(name: BoundedContextName) {
        check(accepting) {
            "The application is closed, so the context `${name.value}` cannot be built."
        }
        synchronized(contexts) {
            val taken = contexts.flatMap { listOf(it.name(), it.name().toSystem()) }
            listOf(name, name.toSystem())
                .firstOrNull { it in taken }
                ?.let { throw DuplicateContextNameException(it) }
        }
    }

    /**
     * Registers the given context, which has just been built.
     */
    fun register(context: BoundedContext) {
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
     * @throws DuplicateEntityStateException If another registered context already serves
     *   the type.
     */
    fun serve(stateType: TypeUrl, context: BoundedContext): Boolean =
        servedStateTypes.serve(stateType, context)

    /**
     * Records that the given context no longer serves entities with the given state type.
     *
     * Does nothing if the context is not recorded for the type.
     */
    fun forget(stateType: TypeUrl, context: BoundedContext) =
        servedStateTypes.forget(stateType, context)

    /**
     * Unregisters the given context, which is closing, together with the entity state types
     * it serves.
     *
     * Does nothing for a context that is not registered.
     */
    fun unregister(context: BoundedContext) {
        servedStateTypes.forget(context)
        synchronized(contexts) {
            contexts.removeAll { it === context }
        }
    }

    /**
     * Obtains the registered context that serves entities with the given state type,
     * or `null` if no registered context serves it.
     */
    fun contextServing(stateType: TypeUrl): BoundedContext? =
        servedStateTypes.contextServing(stateType)

    /**
     * Obtains the registered contexts, in the order of their registration.
     */
    fun all(): List<BoundedContext> =
        synchronized(contexts) {
            contexts.toList()
        }

    /**
     * Closes the registered contexts, in the reverse order of their registration,
     * and refuses to register new ones afterwards.
     *
     * Closes each context even if closing another one fails; then throws the first failure,
     * with the later ones suppressed.
     */
    @Suppress("TooGenericExceptionCaught") // Accumulates any failure of closing a context.
    fun closeAll() {
        accepting = false
        var failure: RuntimeException? = null
        for (context in all().asReversed()) {
            try {
                context.closeIfOpen()
            } catch (e: RuntimeException) {
                failure = failure.withSuppressed(e)
            }
        }
        failure?.let { throw it }
    }
}

/**
 * Returns this failure with the given one added to it as suppressed,
 * or the given failure if this one is `null`.
 */
internal fun RuntimeException?.withSuppressed(next: RuntimeException): RuntimeException {
    if (this == null) {
        return next
    }
    addSuppressed(next)
    return this
}
