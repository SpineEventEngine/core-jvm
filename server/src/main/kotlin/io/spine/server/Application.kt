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
import io.spine.server.integration.ThirdPartyContext
import io.spine.server.tenant.DefaultTenantStorage
import io.spine.server.tenant.TenantIndex

/**
 * The application made of Bounded Contexts that run on the technologies
 * of one [ServerEnvironment].
 *
 * Holds what the contexts of the application share: the environment, the contexts
 * themselves, and the index of the tenants that the application serves.
 *
 * An application is created with a [Builder], which builds its contexts. In Java:
 * ```
 * var application = Application.newBuilder()
 *         .setServerEnvironment(environment)
 *         .add(BoundedContext.singleTenant("Users").add(new UserRepository()))
 *         .add(BoundedContext.singleTenant("Tasks").add(new TaskRepository()))
 *         .build();
 * ```
 * In Kotlin:
 * ```
 * val application = application {
 *     serverEnvironment = environment
 *     add(BoundedContext.singleTenant("Users").add(UserRepository()))
 *     add(BoundedContext.singleTenant("Tasks").add(TaskRepository()))
 * }
 * ```
 *
 * ## Contexts
 *
 * The Bounded Contexts of an application work together: they share one `Delivery`, one
 * transport, and one storage factory. So each context of the application must have a name
 * of its own, and serve entity state types of its own. The [Builder] checks both before
 * building any context: it throws [DuplicateContextNameException] on a repeated name, and
 * [DuplicateEntityStateException] on a state type that two contexts serve.
 *
 * The contexts of an application are the ones given to its builder. A context cannot join
 * an application afterwards.
 *
 * ## Closing
 *
 * [Closing][close] the application closes its contexts, then its tenant index, and then
 * its [ServerEnvironment]. A [Server] that serves the application closes it when the server
 * shuts down.
 */
public class Application private constructor(

    /**
     * The environment that provides the technologies to the contexts of the application.
     */
    private val environment: ServerEnvironment,

    /**
     * The contexts of the application.
     */
    private val registry: ContextRegistry,

    /**
     * The index of tenants [set][Builder.setTenantIndex] when the application was built,
     * or `null` if none was set.
     *
     * See [tenantIndex] for the lifecycle of this property.
     */
    private var index: TenantIndex?
) : Closeable {

    /**
     * Tells if the application is open.
     */
    @Volatile
    private var open = true

    /**
     * Obtains the Bounded Contexts of the application, in the order of their building.
     *
     * The contexts that are closed stay in the list.
     */
    public fun contexts(): List<BoundedContext> = registry.all()

    /**
     * Obtains the index of tenants served by the multitenant Bounded Contexts
     * of the application.
     *
     * Unless the index is [set][Builder.setTenantIndex] when the application is built,
     * the default one is created on the first call to this method, and kept for the later
     * ones, which all return it. It keeps the tenants with the storage factory of
     * the [ServerEnvironment] of the application.
     *
     * The index is created on demand so that an application of single-tenant contexts,
     * which never calls this method, creates no storage for the tenants.
     *
     * [Closing][close] the application closes the index it has, if any, and releases it.
     *
     * @throws IllegalStateException If the application is closed.
     */
    @Synchronized
    public fun tenantIndex(): TenantIndex {
        check(open) { "The application is closed, so it has no tenant index." }
        return index ?: DefaultTenantStorage(environment.storageFactory()).also { index = it }
    }

    override fun isOpen(): Boolean = open

    /**
     * Closes the Bounded Contexts of the application, then its tenant index, and then
     * its [ServerEnvironment].
     *
     * The contexts are closed in the reverse order of their building. Each part is closed even
     * if closing another one fails, even with an [Error]; then the first failure is thrown,
     * with the later ones suppressed.
     *
     * Does nothing if the application is already closed.
     */
    @Suppress("TooGenericExceptionCaught") // Closes every part even if closing another fails.
    override fun close() {
        val closingIndex = synchronized(this) {
            if (!open) {
                return
            }
            open = false
            index.also { index = null }
        }
        var failure: Throwable? = null
        try {
            registry.closeAll()
        } catch (e: Throwable) {
            failure = e
        }
        try {
            closingIndex?.closeIfOpen()
        } catch (e: Throwable) {
            failure = failure.withSuppressed(e)
        }
        try {
            environment.closeIfOpen()
        } catch (e: Throwable) {
            failure = failure.withSuppressed(e)
        }
        failure?.let { throw it }
    }

    override fun toString(): String =
        "Application of " + contexts().joinToString { "`${it.name().value}`" }

    /**
     * The builder of an [Application].
     *
     * An application needs a [ServerEnvironment], and at least one Bounded Context.
     */
    @ServerDsl
    public class Builder internal constructor() {

        private var environment: ServerEnvironment? = null

        private var index: TenantIndex? = null

        /**
         * The builders of the contexts, in the order of adding.
         */
        private val contexts = mutableListOf<BoundedContextBuilder>()

        /**
         * The third-party contexts, to be given the contexts built from their builders.
         */
        private val thirdParty = mutableListOf<ThirdPartyContext>()

        private var built = false

        /**
         * The environment that provides the technologies to the contexts of the application.
         *
         * This property is for the [application] DSL. Java code calls [setServerEnvironment].
         */
        @get:JvmSynthetic
        @set:JvmSynthetic
        @set:JvmName("assignServerEnvironment")
        public var serverEnvironment: ServerEnvironment?
            get() = environment
            set(value) {
                setServerEnvironment(requireNotNull(value) {
                    "The server environment of an application cannot be `null`."
                })
            }

        /**
         * The index of the tenants of the application.
         *
         * This property is for the [application] DSL. Java code calls [setTenantIndex].
         */
        @get:JvmSynthetic
        @set:JvmSynthetic
        @set:JvmName("assignTenantIndex")
        public var tenantIndex: TenantIndex?
            get() = index
            set(value) {
                setTenantIndex(requireNotNull(value) {
                    "The tenant index of an application cannot be `null`."
                })
            }

        /**
         * Sets the environment that provides the technologies to the contexts
         * of the application.
         *
         * The application owns the environment, and closes it when the application is closed.
         *
         * @return This builder, for call chaining.
         */
        @CanIgnoreReturnValue
        public fun setServerEnvironment(environment: ServerEnvironment): Builder {
            checkNotBuilt()
            this.environment = environment
            return this
        }

        /**
         * Sets the index of tenants served by the multitenant Bounded Contexts
         * of the application.
         *
         * If not set, the application creates the default index, which keeps the tenants
         * with the storage factory of the [ServerEnvironment].
         *
         * The application closes the index when the application is closed.
         *
         * @return This builder, for call chaining.
         */
        @CanIgnoreReturnValue
        public fun setTenantIndex(index: TenantIndex): Builder {
            checkNotBuilt()
            this.index = index
            return this
        }

        /**
         * Adds the builder of a Bounded Context to the application.
         *
         * The context is built when the application is [built][build].
         *
         * @return This builder, for call chaining.
         */
        @CanIgnoreReturnValue
        public fun add(context: BoundedContextBuilder): Builder {
            checkNotBuilt()
            contexts.add(context)
            return this
        }

        /**
         * Adds a context that represents a part of a third-party system.
         *
         * The context is built when the application is [built][build]. Then it can emit
         * events into the other contexts of the application.
         *
         * @return This builder, for call chaining.
         * @throws IllegalStateException If another application has built the context.
         */
        @CanIgnoreReturnValue
        public fun add(context: ThirdPartyContext): Builder {
            checkNotAttached(context)
            add(context.builder)
            thirdParty.add(context)
            return this
        }

        /**
         * Creates the application, and builds its Bounded Contexts in the order of adding.
         *
         * Before building any context, checks that no two contexts have one name, or the name
         * of the System context of another one, and that no two contexts serve one entity
         * state type. If building a context fails, closes the contexts built before it.
         *
         * @throws IllegalStateException If the server environment is not set, if no context
         *   is added, if the environment is closed, if another application owns it, or if
         *   another application has built one of the third-party contexts.
         * @throws DuplicateContextNameException If two contexts have one name, or a context
         *   has the name of the System context of another one.
         * @throws DuplicateEntityStateException If two contexts have repositories of one
         *   entity state type.
         */
        public fun build(): Application {
            checkNotBuilt()
            val environment = checkNotNull(environment) {
                "The server environment of the application is not set."
            }
            check(contexts.isNotEmpty()) {
                "The application has no Bounded Contexts. Please add at least one."
            }
            check(environment.isOpen) {
                "The server environment of the application is closed."
            }
            thirdParty.forEach { checkNotAttached(it) }
            checkUnique(contexts)
            check(environment.owned.compareAndSet(false, true)) {
                "The server environment belongs to another application."
            }
            val registry = buildContexts(environment)
            built = true
            thirdParty.forEach { party ->
                party.attach(registry.all().first { it.name() == party.builder.name() })
            }
            return Application(environment, registry, index)
        }

        /**
         * Builds the contexts with the technologies of the given environment.
         *
         * If building a context fails, closes the contexts built before it, and releases
         * the environment, so that another application may own it.
         */
        @Suppress("TooGenericExceptionCaught") // Closes the built contexts, then rethrows.
        private fun buildContexts(environment: ServerEnvironment): ContextRegistry {
            val built = mutableListOf<BoundedContext>()
            try {
                contexts.forEach {
                    built.add(it.build(environment))
                }
            } catch (e: Throwable) {
                environment.owned.set(false)
                try {
                    ContextRegistry(built).closeAll()
                } catch (closing: Throwable) {
                    e.addSuppressed(closing)
                }
                throw e
            }
            return ContextRegistry(built)
        }

        /**
         * Throws if another application has built the given third-party context.
         *
         * Building its context again would leave the contexts of this application open,
         * with no application to close them.
         */
        private fun checkNotAttached(context: ThirdPartyContext) {
            check(!context.isAttached) {
                "The third-party context `${context.builder.name().value}` already belongs" +
                        " to another application."
            }
        }

        /**
         * Throws if this builder has already built an application.
         */
        private fun checkNotBuilt() {
            check(!built) {
                "The application is already built. Please use another builder."
            }
        }
    }

    public companion object {

        /**
         * Creates a new builder of an application.
         */
        @JvmStatic
        public fun newBuilder(): Builder = Builder()
    }
}
