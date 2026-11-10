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
import io.spine.annotation.Internal
import io.spine.annotation.VisibleForTesting
import io.spine.base.Identifier
import io.spine.environment.CustomEnvironmentType
import io.spine.environment.Environment
import io.spine.environment.EnvironmentType
import io.spine.environment.Tests
import io.spine.server.commandbus.CommandScheduler
import io.spine.server.commandbus.ExecutorCommandScheduler
import io.spine.server.delivery.Delivery
import io.spine.server.storage.StorageFactory
import io.spine.server.storage.memory.InMemoryStorageFactory
import io.spine.server.storage.system.SystemAwareStorageFactory.wrap
import io.spine.server.trace.TracerFactory
import io.spine.server.transport.TransportFactory
import io.spine.server.transport.memory.InMemoryTransportFactory
import io.spine.util.Exceptions.newIllegalStateException
import java.util.Optional
import java.util.function.Function
import java.util.function.Supplier

/**
 * The server conditions and configuration under which the application operates.
 *
 * ## Configuration
 *
 * Some parts of the `ServerEnvironment` can be customized based on the `EnvironmentType`.
 * To do so, one of the overloads of the `use` method can be called.
 * Two environment types exist out of the box: [Tests] and
 * [DefaultMode][io.spine.environment.DefaultMode]. For example:
 * ```
 * ServerEnvironment.under(Production.class)
 *                  .use(productionStorageFactory)
 *                  .use(memoizingTracerFactory);
 * ServerEnvironment.under(Tests.class)
 *                  .use(testingStorageFactory);
 * ```
 * A custom environment type may also be used:
 * ```
 * final class StagingEnvironment extends EnvironmentType {
 *     ...
 * }
 *
 * ServerEnvironment.under(Staging.class)
 *                  .use(inMemoryStorageFactory);
 * ```
 *
 * If `Staging` is [enabled][Environment.type], the specified value is going to be
 * returned on [storageFactory].
 *
 * ### Relation to Bounded Contexts
 *
 * A `ServerEnvironment` instance is a singleton. It means that all Bounded Contexts
 * that are deployed within the same JVM share the configuration of the server environment.
 * Namely, under the same environment type, all Bounded Contexts will share the same storage
 * factory, transport factory, delivery and tracing tools.
 *
 * If one decides that some of the Bounded Contexts of an application require custom
 * settings, they should arrange a separate deployable artifact and distinct configuration
 * of the respective `ServerEnvironment` for those Bounded Contexts. In this way,
 * the Contexts would reside in their own JVMs and not overlap on interacting with this singleton.
 *
 * Within a JVM, each open Bounded Context has a name of its own and serves entity state
 * types of its own, as described in [BoundedContext].
 */
public class ServerEnvironment private constructor() : Closeable {

    /**
     * The identifier of the server instance running in scope of this application.
     *
     * It is currently impossible to set the node identifier directly.
     * This is subject to change in the future framework versions.
     */
    private val nodeId: NodeId = NodeId.newBuilder()
        .setValue(Identifier.newUuid())
        .build()

    /**
     * The strategy of delivering the messages received by entity repositories
     * to the entity instances.
     *
     * If not [configured by the end-user][TypeConfigurator.use], initialized with
     * the [local][Delivery.local] delivery by default.
     *
     * Differs between [environment types][EnvironmentType].
     */
    private val delivery = EnvSetting<Delivery>()

    /**
     * Storage factory setting.
     *
     * Defaults to an `InMemoryStorageFactory` for tests.
     */
    private val storageFactory = EnvSetting<StorageFactory>(Tests::class.java) {
        wrap(InMemoryStorageFactory.newInstance())
    }

    /**
     * The setting for the factory of `Tracer`s.
     */
    private val tracerFactory = EnvSetting<TracerFactory>()

    /**
     * The setting for the factory for channel-based transport.
     *
     * Defaults to an `InMemoryTransportFactory` for tests.
     */
    private val transportFactory = EnvSetting<TransportFactory>(Tests::class.java) {
        InMemoryTransportFactory.newInstance()
    }

    /**
     * Provides schedulers used by all `CommandBus` instances of this environment.
     */
    private var commandScheduler: Supplier<CommandScheduler> =
        Supplier { ExecutorCommandScheduler() }

    /**
     * Flag indicating whether this environment has been closed.
     */
    @Volatile
    private var closed = false

    /**
     * Obtains the type of the current server environment.
     *
     * This is a convenience method for server-side configuration code, which
     * simply delegates to [Environment.type].
     */
    public fun type(): Class<out EnvironmentType<*>> = environment().type()

    /**
     * Returns the delivery mechanism specific to this environment.
     *
     * Unless [updated manually][TypeConfigurator.use], returns
     * a [local implementation][Delivery.local] of `Delivery`.
     */
    @Synchronized
    public fun delivery(): Delivery {
        val currentEnv = environment().type()
        val currentDelivery = delivery.optionalValue(currentEnv)
        if (currentDelivery.isPresent) {
            return currentDelivery.get()
        }
        val localDelivery = newLocalDelivery()
        delivery.use(localDelivery, currentEnv)
        return localDelivery
    }

    /**
     * Creates a local `Delivery` that runs on this node and creates its storages with
     * the storage factory of the current environment, if it is configured.
     */
    private fun newLocalDelivery(): Delivery {
        val factory = optionalStorageFactory().orElse(null)
        return Delivery.local(nodeId, factory)
    }

    /**
     * Obtains command scheduling mechanism used by `CommandBus` in this environment.
     */
    public fun newCommandScheduler(): CommandScheduler = commandScheduler.get()

    /**
     * Assigns command scheduling mechanism used at this environment by all
     * `CommandBus` instances.
     *
     * If not configured, [ExecutorCommandScheduler] is used.
     */
    public fun scheduleCommandsUsing(commandScheduler: Supplier<CommandScheduler>) {
        this.commandScheduler = commandScheduler
    }

    /**
     * Obtains the identifier of the server node, on which this code is running at the moment.
     *
     * At the moment, the node identifier is always UUID-generated. In future versions of the
     * framework it is expected to become configurable.
     */
    public fun nodeId(): NodeId = nodeId

    /**
     * Obtains the [TracerFactory] associated with the current environment, if it was set.
     */
    public fun tracing(): Optional<TracerFactory> {
        val currentType = environment().type()
        return tracerFactory.optionalValue(currentType)
    }

    /**
     * Obtains the storage factory for the current environment.
     *
     * For tests, if the value was not set, defaults to a new `InMemoryStorageFactory`.
     *
     * For other environments, if the value was not set, throws a `IllegalStateException`.
     *
     * @return `StorageFactory` instance for the storage for the current environment.
     * @throws IllegalStateException If the value `StorageFactory` was not
     *   [configured][TypeConfigurator.use] prior to the call.
     */
    public fun storageFactory(): StorageFactory {
        val type = environment().type()
        val result = storageFactory.optionalValue(type)
            .orElseThrow { SuggestConfiguring.storageFactory(type) }
        return result
    }

    /**
     * Returns a storage factory for the current environment, or an empty `Optional` if it
     * was not configured.
     */
    @Internal
    public fun optionalStorageFactory(): Optional<StorageFactory> {
        val type = environment().type()
        return storageFactory.optionalValue(type)
    }

    /**
     * Obtains the transport factory for the current environment.
     *
     * In the [testing environment][Tests], if the factory was not assigned, assigns
     * a new `InMemoryTransportFactory` and returns it.
     *
     * For all other environment types, throws an `IllegalStateException`.
     *
     * If the factory is not assigned in the Tests mode, assigns the instance of
     * [InMemoryTransportFactory] and returns it.
     */
    public fun transportFactory(): TransportFactory {
        val type = environment().type()
        val result = transportFactory.optionalValue(type)
            .orElseThrow { SuggestConfiguring.transportFactory(type) }
        return result
    }

    /**
     * This is test-only method required for cleaning of the server environment instance in tests.
     */
    @VisibleForTesting
    public fun reset() {
        transportFactory.reset()
        tracerFactory.reset()
        storageFactory.reset()
        delivery.reset()
        val currentEnv = environment().type()
        delivery.use(newLocalDelivery(), currentEnv)
    }

    /**
     * Tells if the environment is still open.
     */
    override fun isOpen(): Boolean = !closed

    /**
     * Releases resources associated with this instance.
     */
    override fun close() {
        if (closed) {
            return
        }
        closeFactory("TracerFactory", tracerFactory)
        closeFactory("TransportFactory", transportFactory)
        closeFactory("StorageFactory", storageFactory)
        closed = true
    }

    /**
     * Allows configuring values used by the `ServerEnvironment` for the given type.
     */
    public class TypeConfigurator internal constructor(
        type: Class<out EnvironmentType<*>>
    ) {

        private val se: ServerEnvironment = instance()

        /**
         * Obtains the type of the environment being currently configured.
         */
        @get:VisibleForTesting
        @get:JvmName("type")
        public val type: Class<out EnvironmentType<*>> = type

        init {
            // Clear the flag for the case of unit tests that closed the singleton before.
            se.closed = false
            if (CustomEnvironmentType::class.java.isAssignableFrom(type)) {
                registerCustomType(type)
            }
        }

        /**
         * Assigns the specified `Delivery` for the selected environment.
         *
         * @see useDelivery
         */
        @CanIgnoreReturnValue
        public fun use(delivery: Delivery): TypeConfigurator {
            se.delivery.use(delivery, type)
            return this
        }

        /**
         * Assigns a `Delivery` obtained from the passed function.
         *
         * In case the `Delivery` is never requested for the current server
         * environment type, the passed function will be not invoked.
         *
         * @param fn The function to provide the `Delivery` in response to
         *   the currently configured server environment type.
         * @see use
         */
        @CanIgnoreReturnValue
        public fun useDelivery(fn: Fn<Delivery>): TypeConfigurator {
            se.delivery.lazyUse({ fn.apply(type) }, type)
            return this
        }

        /**
         * Assigns `TracerFactory` for the selected environment.
         *
         * @see useTracerFactory
         */
        @CanIgnoreReturnValue
        public fun use(factory: TracerFactory): TypeConfigurator {
            se.tracerFactory.use(factory, type)
            return this
        }

        /**
         * Lazily uses the `TracerFactory` obtained from the passed function.
         *
         * In case the `TracerFactory` is never requested for the current server
         * environment type, the passed function will be not invoked.
         *
         * @param fn The function to provide the `TracerFactory` in response to
         *   the currently configured server environment type.
         * @see use
         */
        @CanIgnoreReturnValue
        public fun useTracerFactory(fn: Fn<TracerFactory>): TypeConfigurator {
            se.tracerFactory.lazyUse({ fn.apply(type) }, type)
            return this
        }

        /**
         * Assigns the specified transport factory for the selected environment.
         *
         * @see useTransportFactory
         */
        @CanIgnoreReturnValue
        public fun use(factory: TransportFactory): TypeConfigurator {
            se.transportFactory.use(factory, type)
            return this
        }

        /**
         * Assigns a `TransportFactory` obtained from the passed function.
         *
         * In case the `TransportFactory` is never requested for the current server
         * environment type, the passed function will be not invoked.
         *
         * @param fn The function to provide the `TransportFactory` in response to
         *   the currently configured server environment type.
         * @see use
         */
        @CanIgnoreReturnValue
        public fun useTransportFactory(fn: Fn<TransportFactory>): TypeConfigurator {
            se.transportFactory.lazyUse({ fn.apply(type) }, type)
            return this
        }

        /**
         * Assigns the specified `StorageFactory` for the selected environment.
         *
         * @see useStorageFactory
         */
        @CanIgnoreReturnValue
        public fun use(factory: StorageFactory): TypeConfigurator {
            val wrapped = wrap(factory)
            se.storageFactory.use(wrapped, type)
            return this
        }

        /**
         * Assigns a `StorageFactory` obtained from the passed function.
         *
         * In case the `StorageFactory` is never requested for the current server
         * environment type, the passed function will be not invoked.
         *
         * @param fn The function to provide the `StorageFactory` in response to
         *   the currently configured server environment type.
         * @see use
         */
        @CanIgnoreReturnValue
        public fun useStorageFactory(fn: Fn<StorageFactory>): TypeConfigurator {
            se.storageFactory.lazyUse({
                val factory = fn.apply(type)
                val wrapped = wrap(factory)
                wrapped
            }, type)
            return this
        }

        private companion object {

            private fun registerCustomType(type: Class<out EnvironmentType<*>>) {
                @Suppress("UNCHECKED_CAST") // Checked by the calling site.
                val customType = type as Class<out CustomEnvironmentType<*>>
                Environment.instance()
                    .register(customType)
            }
        }
    }

    /**
     * A function that accepts a class of [EnvironmentType] and returns
     * a value [configured][ServerEnvironment.under] in a `ServerEnvironment`.
     *
     * @param R The type of the configured value.
     */
    @Suppress("NewClassNamingConvention") // Two letters for this name is fine.
    public fun interface Fn<R> : Function<Class<out EnvironmentType<*>>, R>

    /**
     * Factory methods for creating exceptions raised when a feature of server
     * environment is not properly configured.
     */
    private object SuggestConfiguring {

        fun storageFactory(type: Class<out EnvironmentType<*>>): IllegalStateException =
            raise(
                "The storage factory for the environment `%s` was not configured.",
                type, "storageFactory"
            )

        fun transportFactory(type: Class<out EnvironmentType<*>>): IllegalStateException =
            raise(
                "Transport factory is not assigned for the current environment `%s`.",
                type, "transportFactory"
            )

        /**
         * Creates `IllegalStateException` with the error message suggesting configuring
         * a feature of `ServerEnvironment`.
         *
         * @param prefixFmt The first part of the error message that explains the error,
         *   containing a format parameter for the type name of the current environment.
         * @param type The type of the environment under which we detected the error.
         * @param featureParamName The name of the parameter passed to the `use()` method.
         */
        private fun raise(
            prefixFmt: String,
            type: Class<out EnvironmentType<*>>,
            featureParamName: String
        ): IllegalStateException {
            val typeName = type.simpleName
            val fmt = "$prefixFmt Please call `ServerEnvironment.under(%s.class).use(%s);`."
            return newIllegalStateException(fmt, typeName, typeName, featureParamName)
        }
    }

    public companion object {

        private val INSTANCE = ServerEnvironment()

        /**
         * Returns a singleton instance.
         */
        @JvmStatic
        public fun instance(): ServerEnvironment = INSTANCE

        private fun environment(): Environment = Environment.instance()

        /**
         * Closes a factory from an environment setting, wrapping any exceptions.
         */
        @Suppress("TooGenericExceptionCaught") // Wraps whatever closing throws.
        private fun <T : AutoCloseable> closeFactory(
            factoryName: String,
            setting: EnvSetting<T>
        ) {
            try {
                setting.apply(EnvSetting.SettingOperation { it.close() })
            } catch (e: RuntimeException) {
                throw newIllegalStateException(e, "Failed to close `%s`.", factoryName)
            }
        }

        /**
         * Starts a flowing API chain for configuring `ServerEnvironment` for the passed type.
         */
        @JvmStatic
        public fun under(type: Class<out EnvironmentType<*>>): TypeConfigurator =
            TypeConfigurator(type)

        /**
         * Starts a flowing API chain for configuring `ServerEnvironment` for the passed type.
         *
         * @deprecated Use [under] instead. Unlike this method, `under()` does not
         *   require backticks when called from Kotlin, where `when` is a keyword.
         */
        @Deprecated(
            "Use `under()` instead.",
            ReplaceWith("ServerEnvironment.under(type)", "io.spine.server.ServerEnvironment")
        )
        @JvmStatic
        public fun `when`(type: Class<out EnvironmentType<*>>): TypeConfigurator = under(type)
    }
}
