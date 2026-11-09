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
import io.spine.server.tenant.TenantIndex

/**
 * The application made of the Bounded Contexts that run in this JVM.
 *
 * Holds what the contexts of the application share, such as the index of the tenants
 * that the application serves. The technologies that the contexts run on, such as storage
 * and transport, are configured by [ServerEnvironment].
 */
public class Application private constructor() : Closeable {

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
     * A context given an index of its own with [BoundedContextBuilder.setTenantIndex]
     * uses that index instead.
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
     * Closes the application and its tenant index.
     *
     * The application stays closed: [tenantIndex] and [use] throw after this call,
     * so no multitenant context can be built afterwards.
     *
     * Call this method after closing the Bounded Contexts of the application,
     * and before closing [ServerEnvironment], whose storage factory keeps the tenants
     * of the default index.
     */
    @Synchronized
    override fun close() {
        open = false
        closeIndex()
    }

    /**
     * Closes the tenant index and opens the application again, with no index.
     *
     * The next multitenant context gets a new default index. Tests that change the storage
     * factory of [ServerEnvironment] call this method, so that the index keeps the tenants
     * with the new storage factory.
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
