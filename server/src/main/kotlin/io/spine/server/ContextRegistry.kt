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
import io.spine.util.closeAll

/**
 * The registry of the domain Bounded Contexts of an [Application].
 *
 * Holds the contexts of the application, in the order of their building, and closes them.
 * The contexts are given when the application is built, and stay in the registry when
 * they close.
 *
 * System contexts are not registered: a domain context closes its System context.
 *
 * @param contexts The contexts of the application, in the order of their building.
 */
internal class ContextRegistry(contexts: List<BoundedContext>) {

    /**
     * The registered contexts, in the order of their building.
     */
    private val contexts: List<BoundedContext> = ImmutableList.copyOf(contexts)

    /**
     * Obtains the registered contexts, in the order of their building.
     *
     * The returned list cannot be changed, Java callers included.
     */
    fun all(): List<BoundedContext> = contexts

    /**
     * Closes the registered contexts that are open, in the reverse order of their building.
     *
     * Closes each context even if closing another one fails, even with an [Error];
     * then throws the first failure, with the later ones suppressed.
     */
    fun closeAll() {
        contexts.asReversed()
            .map { context -> AutoCloseable { context.closeIfOpen() } }
            .closeAll()
    }
}

/**
 * Checks that the contexts of the given builders can work together in one application.
 *
 * The contexts share one `Delivery`, one transport, and one storage factory, so:
 *
 * - no two contexts may have one name, and no context may have the name of the System
 *   context of another one;
 * - no two contexts may have repositories of one entity state type.
 *
 * The builders hold all the repositories of the domain contexts: the framework adds none
 * to a context after it is built. The repositories of System contexts are not compared:
 * every System context has the same repositories of the framework.
 *
 * @throws DuplicateContextNameException If two contexts have one name, or a context has
 *   the name of the System context of another one.
 * @throws DuplicateEntityStateException If two contexts have repositories of one entity
 *   state type.
 */
internal fun checkUnique(builders: List<BoundedContextBuilder>) {
    val names = mutableSetOf<BoundedContextName>()
    builders.forEach { builder ->
        val name = builder.name()
        listOf(name, name.toSystem())
            .firstOrNull { it in names }
            ?.let { throw DuplicateContextNameException(it) }
        names.add(name)
        names.add(name.toSystem())
    }
    val serving = mutableMapOf<TypeUrl, BoundedContextName>()
    builders.forEach { builder ->
        val name = builder.name()
        builder.repositories()
            .map { it.entityStateType() }
            .forEach { type ->
                val other = serving.putIfAbsent(type, name)
                if (other != null && other != name) {
                    throw DuplicateEntityStateException(type, listOf(other, name))
                }
            }
    }
}
