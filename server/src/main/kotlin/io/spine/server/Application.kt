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
import io.spine.server.tenant.TenantIndex

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
 *
 * @constructor Creates an application with no contexts and no tenant index. The framework
 *   uses the [instance] of the JVM; tests create applications of their own.
 */
public class Application internal constructor() : Closeable {

    /**
     * The open domain contexts of the application.
     */
    @get:JvmName("openContexts")
    internal val openContexts: OpenContexts = OpenContexts()

    /**
     * The index of tenants of the multitenant contexts, or `null` until it is created or set.
     */
    private var index: TenantIndex? = null

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
    @Suppress("TooGenericExceptionCaught") // Closes the index even if closing a context fails.
    override fun close() {
        synchronized(this) {
            open = false
        }
        var failure: RuntimeException? = null
        try {
            openContexts.closeAll()
        } catch (e: RuntimeException) {
            failure = e
        }
        try {
            closeIndex()
        } catch (e: RuntimeException) {
            failure = failure.withSuppressed(e)
        }
        failure?.let { throw it }
    }

    /**
     * Forgets the tenant index, and then closes it.
     *
     * The index is forgotten even if closing it fails. The application stays as it is:
     * an open application creates a new default index on the next call to [tenantIndex].
     *
     * Besides [close], tests call this method, for example, to give the application
     * an index of their own, or to make the index keep the tenants with another storage
     * factory of [ServerEnvironment].
     */
    @Synchronized
    @JvmName("closeIndex")
    internal fun closeIndex() {
        val closing = index
        index = null
        closing?.closeIfOpen()
    }

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
