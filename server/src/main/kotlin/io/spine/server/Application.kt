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

import com.google.errorprone.annotations.CanIgnoreReturnValue
import io.spine.annotation.VisibleForTesting
import io.spine.core.BoundedContextName
import io.spine.server.tenant.TenantIndex
import io.spine.type.TypeUrl

/**
 * The application made of the Bounded Contexts that run in this JVM.
 *
 * Holds what the contexts of the application share: the record of the contexts that are open,
 * and the index of the tenants that the application serves. The technologies that the contexts
 * run on, such as storage and transport, are configured by [ServerEnvironment].
 *
 * ## Open contexts
 *
 * The Bounded Contexts of a JVM work together: they share one `Delivery`, one transport,
 * and one storage factory. So an open domain context must have a name of its own, and serve
 * entity state types of its own. Building a context with the name of an open one throws
 * [DuplicateContextNameException], which also tells why names matter. Adding a repository
 * of a state type that another open context serves throws [DuplicateEntityStateException].
 *
 * A closed context is no longer open, so another context may have its name and serve its
 * entity state types. See [BoundedContext] on what this means for tests.
 *
 * ## Closing
 *
 * [Closing][close] the application closes its open contexts, and then its tenant index.
 * Close the application before [ServerEnvironment], whose storage factory keeps the tenants
 * of the default index.
 */
public class Application private constructor() : Closeable {

    /**
     * The index of tenants of the multitenant contexts, or `null` until it is created or set.
     */
    private var index: TenantIndex? = null

    /**
     * The open domain contexts, in the order in which they were opened.
     *
     * A list rather than a set, because a set would compare the contexts by
     * [name][BoundedContext.equals], and the application tells them apart by identity.
     *
     * System contexts are not recorded. Their names follow the names of their domain contexts,
     * so [checkNameFree] checks them through their domain contexts. All of them serve the same
     * entity state types. A domain context closes its System context.
     */
    private val contexts = mutableListOf<BoundedContext>()

    /**
     * The record of which open domain context serves which entity state type.
     */
    private val servedStateTypes = ServedStateTypes()

    /**
     * Tells if the application is open.
     */
    @Volatile
    private var open = true

    /**
     * Obtains the index of tenants served by the multitenant Bounded Contexts
     * of the application.
     *
     * Unless another index is [set][use], the default one is created on the first call.
     * It keeps the tenants with the storage factory of [ServerEnvironment], so the storage
     * factory must be configured before the first multitenant context is built.
     *
     * @throws IllegalStateException If the application is closed.
     */
    @Synchronized
    public fun tenantIndex(): TenantIndex {
        checkOpen()
        return index ?: TenantIndex.defaultMultitenant().also { index = it }
    }

    /**
     * Sets the index of tenants served by the multitenant Bounded Contexts
     * of the application.
     *
     * The application closes the index when the application is closed.
     *
     * @return This instance, for call chaining.
     * @throws IllegalStateException If the application already has an index, for example,
     *   because a multitenant context was built before this call, or if the application
     *   is closed.
     */
    @Synchronized
    @CanIgnoreReturnValue
    public fun use(index: TenantIndex): Application {
        checkOpen()
        check(this.index == null) {
            "The application already has a tenant index."
        }
        this.index = index
        return this
    }

    override fun isOpen(): Boolean = open

    /**
     * Closes the open Bounded Contexts of the application, and then its tenant index.
     *
     * The contexts are closed in the reverse order of their building. Each part is closed even
     * if closing another one fails; then the first failure is thrown, with the later ones
     * suppressed.
     *
     * The application stays closed: no Bounded Context can be built afterwards,
     * and [tenantIndex] and [use] throw.
     *
     * Call this method before closing [ServerEnvironment], whose storage factory keeps
     * the tenants of the default index.
     */
    override fun close() {
        synchronized(this) {
            open = false
        }
        var failure: RuntimeException? = null
        for (context in openContexts().asReversed()) {
            try {
                context.closeIfOpen()
            } catch (e: RuntimeException) {
                failure = failure.withSuppressed(e)
            }
        }
        try {
            synchronized(this) {
                closeIndex()
            }
        } catch (e: RuntimeException) {
            failure = failure.withSuppressed(e)
        }
        failure?.let { throw it }
    }

    /**
     * Closes the tenant index and opens the application again, with no index.
     *
     * The next multitenant context gets a new default index. Tests that change the storage
     * factory of [ServerEnvironment] call this method, so that the index keeps the tenants
     * with the new storage factory.
     *
     * The contexts that are open stay open, and keep their names and entity state types.
     */
    @VisibleForTesting
    @Synchronized
    public fun reset() {
        try {
            closeIndex()
        } finally {
            open = true
        }
    }

    /**
     * Forgets the tenant index, and then closes it.
     *
     * The index is forgotten even if closing it fails.
     */
    private fun closeIndex() {
        val closing = index
        index = null
        closing?.closeIfOpen()
    }

    /**
     * Obtains the open domain contexts, in the order in which they were opened.
     */
    private fun openContexts(): List<BoundedContext> =
        synchronized(contexts) {
            contexts.toList()
        }

    /**
     * Checks that no open context has the given name, or the name of the System context
     * that a domain context with the given name gets.
     *
     * The System contexts of the open domain contexts count too.
     *
     * Runs before anything of the context is built: a System context, for example, registers
     * the inboxes of its repositories as soon as it is built.
     *
     * Checking the name and [recording the context][contextOpened] are two steps. Two threads
     * that build contexts with one name at the same time may both pass the check. Building
     * contexts is rarely that concurrent, so the application does not guard against it.
     *
     * @throws IllegalStateException If the application is closed.
     * @throws DuplicateContextNameException If an open context has one of the names.
     */
    @JvmName("checkNameFree")
    internal fun checkNameFree(name: BoundedContextName) {
        checkOpen()
        synchronized(contexts) {
            val taken = contexts.flatMap { listOf(it.name(), it.name().toSystem()) }
            listOf(name, name.toSystem())
                .firstOrNull { it in taken }
                ?.let { throw DuplicateContextNameException(it) }
        }
    }

    /**
     * Records that the given domain context, which has just been built, is open.
     *
     * Runs before the repositories of the context are registered, so that its name is taken
     * while they register.
     */
    @JvmName("contextOpened")
    internal fun contextOpened(context: BoundedContext) {
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
    @JvmName("serve")
    internal fun serve(stateType: TypeUrl, context: BoundedContext): Boolean =
        servedStateTypes.serve(stateType, context)

    /**
     * Records that the given context no longer serves entities with the given state type.
     *
     * Does nothing if the context is not recorded for the type.
     */
    @JvmName("forget")
    internal fun forget(stateType: TypeUrl, context: BoundedContext) =
        servedStateTypes.forget(stateType, context)

    /**
     * Records that the given context, which is closing, is no longer open
     * and no longer serves any entities.
     *
     * Does nothing for a context that is not recorded.
     */
    @JvmName("contextClosed")
    internal fun contextClosed(context: BoundedContext) {
        servedStateTypes.forget(context)
        synchronized(contexts) {
            contexts.removeAll { it === context }
        }
    }

    /**
     * Obtains the open context that serves entities with the given state type,
     * or `null` if no open context serves it.
     */
    @JvmName("findContextServing")
    internal fun findContextServing(stateType: TypeUrl): BoundedContext? =
        servedStateTypes.contextServing(stateType)

    override fun toString(): String = "Application"

    public companion object {

        private val instance = Application()

        /**
         * Obtains the application of this JVM.
         */
        @JvmStatic
        public fun instance(): Application = instance
    }
}

/**
 * Returns this failure with the given one added to it as suppressed,
 * or the given failure if this one is `null`.
 */
private fun RuntimeException?.withSuppressed(next: RuntimeException): RuntimeException {
    if (this == null) {
        return next
    }
    addSuppressed(next)
    return this
}
