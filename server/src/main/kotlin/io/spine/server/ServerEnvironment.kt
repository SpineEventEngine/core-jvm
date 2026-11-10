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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier

/**
 * The technologies on which the Bounded Contexts of an [Application] run: storage,
 * transport, delivery, command scheduling, and tracing.
 *
 * ## Configuration
 *
 * An environment is built with a [Builder], which takes the technologies for each
 * [type of environment][EnvironmentType]. When a technology is requested, the environment
 * gives the one configured for the [current type][Environment.type]. Two environment types
 * exist out of the box: [Tests] and [DefaultMode][io.spine.environment.DefaultMode].
 * For example, in Java:
 * ```
 * var environment = ServerEnvironment.newBuilder()
 *         .under(Production.class, env -> {
 *             env.use(productionStorageFactory);
 *             env.use(memoizingTracerFactory);
 *         })
 *         .under(Tests.class, env -> env.use(testingStorageFactory))
 *         .build();
 * ```
 * In Kotlin:
 * ```
 * val environment = serverEnvironment {
 *     under<Production> {
 *         use(productionStorageFactory)
 *         use(memoizingTracerFactory)
 *     }
 *     under<Tests> {
 *         use(testingStorageFactory)
 *     }
 * }
 * ```
 * A custom environment type may also be used:
 * ```
 * final class Staging extends CustomEnvironmentType<Staging> {
 *     ...
 * }
 *
 * var environment = ServerEnvironment.newBuilder()
 *         .under(Staging.class, env -> env.use(inMemoryStorageFactory))
 *         .build();
 * ```
 *
 * If `Staging` is [enabled][Environment.type], the specified value is going to be
 * returned on [storageFactory].
 *
 * ## Relation to Bounded Contexts
 *
 * An environment belongs to the [Application] built with it. The Bounded Contexts of the
 * application share the technologies of the environment. The application closes
 * the environment when the application is closed.
 *
 * Bounded Contexts that need other technologies form another application, with another
 * environment. Applications with environments of their own may run in one JVM.
 */
public class ServerEnvironment private constructor(builder: Builder) : Closeable {

    /**
     * The identifier of the server node, on which this environment runs.
     *
     * At the moment, the node identifier is always UUID-generated. In future versions of the
     * framework it is expected to become configurable.
     */
    @get:JvmName("nodeId")
    public val nodeId: NodeId = NodeId.newBuilder()
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
    private val delivery: EnvSetting<Delivery> = builder.deliverySetting

    /**
     * Storage factory setting.
     *
     * Defaults to an `InMemoryStorageFactory` for tests.
     */
    private val storageFactory: EnvSetting<StorageFactory> = builder.storageFactorySetting

    /**
     * The setting for the factory of `Tracer`s.
     */
    private val tracerFactory: EnvSetting<TracerFactory> = builder.tracerFactorySetting

    /**
     * The setting for the factory for channel-based transport.
     *
     * Defaults to an `InMemoryTransportFactory` for tests.
     */
    private val transportFactory: EnvSetting<TransportFactory> = builder.transportFactorySetting

    /**
     * Provides schedulers used by all `CommandBus` instances of this environment.
     */
    private val commandScheduler: Supplier<CommandScheduler> = builder.schedulerFactory

    /**
     * Tells if an [Application] owns this environment.
     *
     * An environment belongs to one application, which closes it.
     */
    internal val owned = AtomicBoolean(false)

    /**
     * Flag indicating whether this environment has been closed.
     */
    @Volatile
    private var closed = false

    /**
     * The type of the current server environment.
     *
     * This is a convenience property for server-side configuration code, which
     * simply delegates to [Environment.type].
     */
    @get:JvmName("type")
    public val type: Class<out EnvironmentType<*>>
        get() = Environment.instance().type()

    /**
     * Returns the delivery mechanism specific to this environment.
     *
     * Unless [configured][TypeConfigurator.use], returns a [local][Delivery.local]
     * `Delivery`, which runs on this node, and creates its storages with the storage factory
     * of the current environment type, if it is configured.
     */
    @Synchronized
    public fun delivery(): Delivery {
        val currentType = type
        val configured = delivery.optionalValue(currentType)
        if (configured.isPresent) {
            return configured.get()
        }
        val factory = optionalStorageFactory().orElse(null)
        val localDelivery = Delivery.local(nodeId, factory)
        delivery.use(localDelivery, currentType)
        return localDelivery
    }

    /**
     * Obtains command scheduling mechanism used by `CommandBus` in this environment.
     */
    public fun newCommandScheduler(): CommandScheduler = commandScheduler.get()

    /**
     * Obtains the [TracerFactory] associated with the current environment, if it was set.
     */
    public fun tracing(): Optional<TracerFactory> = tracerFactory.optionalValue(type)

    /**
     * Obtains the storage factory for the current environment.
     *
     * For tests, if the value was not set, defaults to a new `InMemoryStorageFactory`.
     *
     * For other environments, if the value was not set, throws an `IllegalStateException`.
     *
     * @return `StorageFactory` instance for the storage for the current environment.
     * @throws IllegalStateException If the value `StorageFactory` was not
     *   [configured][TypeConfigurator.use] for the current environment.
     */
    public fun storageFactory(): StorageFactory {
        val currentType = type
        return storageFactory.optionalValue(currentType)
            .orElseThrow { SuggestConfiguring.storageFactory(currentType) }
    }

    /**
     * Returns a storage factory for the current environment, or an empty `Optional` if it
     * was not configured.
     */
    @Internal
    public fun optionalStorageFactory(): Optional<StorageFactory> =
        storageFactory.optionalValue(type)

    /**
     * Obtains the transport factory for the current environment.
     *
     * For tests, if the value was not set, defaults to a new `InMemoryTransportFactory`.
     *
     * For other environments, if the value was not set, throws an `IllegalStateException`.
     *
     * @throws IllegalStateException If the `TransportFactory` was not
     *   [configured][TypeConfigurator.use] for the current environment.
     */
    public fun transportFactory(): TransportFactory {
        val currentType = type
        return transportFactory.optionalValue(currentType)
            .orElseThrow { SuggestConfiguring.transportFactory(currentType) }
    }

    /**
     * Tells if the environment is still open.
     */
    override fun isOpen(): Boolean = !closed

    /**
     * Releases resources associated with this instance.
     *
     * Closes the tracer factories, the transport factories, and the storage factories
     * that were created for the environment types.
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
     * The builder of a [ServerEnvironment].
     *
     * Takes the technologies for each [type of environment][EnvironmentType] with [under].
     * A builder builds one environment.
     */
    @ServerDsl
    public class Builder internal constructor() {

        /**
         * The setting of the `Delivery` for each type of environment.
         *
         * The names of the settings end with `Setting`, so that inside the block of
         * [serverEnvironment] they do not hide the properties of the enclosing code.
         */
        internal val deliverySetting = EnvSetting<Delivery>()

        /**
         * The setting of the storage factory for each type of environment.
         *
         * Defaults to an `InMemoryStorageFactory` for tests.
         */
        internal val storageFactorySetting = EnvSetting<StorageFactory>(Tests::class.java) {
            wrap(InMemoryStorageFactory.newInstance())
        }

        /**
         * The setting of the factory of `Tracer`s for each type of environment.
         */
        internal val tracerFactorySetting = EnvSetting<TracerFactory>()

        /**
         * The setting of the factory of the channel-based transport for each type
         * of environment.
         *
         * Defaults to an `InMemoryTransportFactory` for tests.
         */
        internal val transportFactorySetting = EnvSetting<TransportFactory>(Tests::class.java) {
            InMemoryTransportFactory.newInstance()
        }

        /**
         * Creates a scheduler of the commands for the `CommandBus` of each context.
         */
        internal var schedulerFactory: Supplier<CommandScheduler> =
            Supplier { ExecutorCommandScheduler() }
            private set

        /**
         * Tells if this builder has built its environment.
         */
        private var built = false

        /**
         * Configures the technologies for the given type of environment.
         *
         * @param type The type of environment to configure.
         * @param configure The function that sets the technologies with the given configurator.
         * @return This builder, for call chaining.
         */
        @CanIgnoreReturnValue
        public fun under(
            type: Class<out EnvironmentType<*>>,
            configure: Consumer<TypeConfigurator>
        ): Builder {
            checkNotBuilt()
            configure.accept(TypeConfigurator(this, type))
            return this
        }

        /**
         * Configures the technologies for the type of environment [E].
         *
         * This is the Kotlin counterpart of [under]. The reified type parameter spares
         * the caller a `::class.java` literal, and the block, having the configurator
         * as its receiver, groups several `use()` calls without repeating the receiver
         * on each of them.
         *
         * @param E The type of environment to configure.
         * @param block The function that sets the technologies.
         * @return This builder, for call chaining.
         */
        @CanIgnoreReturnValue
        public inline fun <reified E : EnvironmentType<*>> under(
            noinline block: TypeConfigurator.() -> Unit
        ): Builder = under(E::class.java) { it.block() }

        /**
         * Sets the factory of the schedulers of commands for the `CommandBus` of each
         * Bounded Context.
         *
         * A scheduler serves one `CommandBus`, and is closed with it, so each context obtains
         * a scheduler of its own from the factory.
         *
         * If not set, a new [ExecutorCommandScheduler] is created for each context.
         *
         * @return This builder, for call chaining.
         */
        @CanIgnoreReturnValue
        public fun scheduleCommandsUsing(factory: Supplier<CommandScheduler>): Builder {
            checkNotBuilt()
            schedulerFactory = factory
            return this
        }

        /**
         * Creates a new environment with the configured technologies.
         *
         * @throws IllegalStateException If this builder has already built an environment.
         */
        public fun build(): ServerEnvironment {
            checkNotBuilt()
            built = true
            return ServerEnvironment(this)
        }

        /**
         * Throws if this builder has built its environment, which now holds the settings
         * of the builder.
         */
        internal fun checkNotBuilt() {
            check(!built) {
                "The server environment is already built. Please use another builder."
            }
        }
    }

    /**
     * Allows configuring the technologies of a [ServerEnvironment] for the given type
     * of environment.
     */
    @ServerDsl
    public class TypeConfigurator internal constructor(
        private val builder: Builder,

        /**
         * The type of environment being configured.
         */
        @get:JvmName("type")
        public val type: Class<out EnvironmentType<*>>
    ) {

        init {
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
            builder.checkNotBuilt()
            builder.deliverySetting.use(delivery, type)
            return this
        }

        /**
         * Assigns a `Delivery` obtained from the passed function.
         *
         * In case the `Delivery` is never requested for the current server
         * environment type, the passed function will not be invoked.
         *
         * @param fn The function to provide the `Delivery` in response to
         *   the currently configured server environment type.
         * @see use
         */
        @CanIgnoreReturnValue
        public fun useDelivery(fn: Fn<Delivery>): TypeConfigurator {
            builder.checkNotBuilt()
            builder.deliverySetting.lazyUse({ fn.apply(type) }, type)
            return this
        }

        /**
         * Assigns `TracerFactory` for the selected environment.
         *
         * @see useTracerFactory
         */
        @CanIgnoreReturnValue
        public fun use(factory: TracerFactory): TypeConfigurator {
            builder.checkNotBuilt()
            builder.tracerFactorySetting.use(factory, type)
            return this
        }

        /**
         * Lazily uses the `TracerFactory` obtained from the passed function.
         *
         * In case the `TracerFactory` is never requested for the current server
         * environment type, the passed function will not be invoked.
         *
         * @param fn The function to provide the `TracerFactory` in response to
         *   the currently configured server environment type.
         * @see use
         */
        @CanIgnoreReturnValue
        public fun useTracerFactory(fn: Fn<TracerFactory>): TypeConfigurator {
            builder.checkNotBuilt()
            builder.tracerFactorySetting.lazyUse({ fn.apply(type) }, type)
            return this
        }

        /**
         * Assigns the specified transport factory for the selected environment.
         *
         * @see useTransportFactory
         */
        @CanIgnoreReturnValue
        public fun use(factory: TransportFactory): TypeConfigurator {
            builder.checkNotBuilt()
            builder.transportFactorySetting.use(factory, type)
            return this
        }

        /**
         * Assigns a `TransportFactory` obtained from the passed function.
         *
         * In case the `TransportFactory` is never requested for the current server
         * environment type, the passed function will not be invoked.
         *
         * @param fn The function to provide the `TransportFactory` in response to
         *   the currently configured server environment type.
         * @see use
         */
        @CanIgnoreReturnValue
        public fun useTransportFactory(fn: Fn<TransportFactory>): TypeConfigurator {
            builder.checkNotBuilt()
            builder.transportFactorySetting.lazyUse({ fn.apply(type) }, type)
            return this
        }

        /**
         * Assigns the specified `StorageFactory` for the selected environment.
         *
         * @see useStorageFactory
         */
        @CanIgnoreReturnValue
        public fun use(factory: StorageFactory): TypeConfigurator {
            builder.checkNotBuilt()
            builder.storageFactorySetting.use(wrap(factory), type)
            return this
        }

        /**
         * Assigns a `StorageFactory` obtained from the passed function.
         *
         * In case the `StorageFactory` is never requested for the current server
         * environment type, the passed function will not be invoked.
         *
         * @param fn The function to provide the `StorageFactory` in response to
         *   the currently configured server environment type.
         * @see use
         */
        @CanIgnoreReturnValue
        public fun useStorageFactory(fn: Fn<StorageFactory>): TypeConfigurator {
            builder.checkNotBuilt()
            builder.storageFactorySetting.lazyUse({ wrap(fn.apply(type)) }, type)
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
     * a value [configured][Builder.under] in a `ServerEnvironment`.
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
            val fmt = "$prefixFmt Please configure it with" +
                    " `ServerEnvironment.newBuilder().under(%s.class, env -> env.use(%s))`."
            return newIllegalStateException(fmt, typeName, typeName, featureParamName)
        }
    }

    public companion object {

        /**
         * Creates a new builder of a server environment.
         */
        @JvmStatic
        public fun newBuilder(): Builder = Builder()

        /**
         * Closes a factory from an environment setting, wrapping any exceptions.
         */
        @Suppress("TooGenericExceptionCaught") // Wraps whatever closing throws.
        private fun <T : Closeable> closeFactory(factoryName: String, setting: EnvSetting<T>) {
            try {
                setting.apply(EnvSetting.SettingOperation { it.close() })
            } catch (e: RuntimeException) {
                throw newIllegalStateException(e, "Failed to close `%s`.", factoryName)
            }
        }
    }
}
