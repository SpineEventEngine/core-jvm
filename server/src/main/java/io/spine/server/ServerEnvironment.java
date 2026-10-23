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

package io.spine.server;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.spine.annotation.Internal;
import io.spine.annotation.VisibleForTesting;
import io.spine.base.Identifier;
import io.spine.core.BoundedContextName;
import io.spine.environment.CustomEnvironmentType;
import io.spine.environment.DefaultMode;
import io.spine.environment.Environment;
import io.spine.environment.EnvironmentType;
import io.spine.environment.Tests;
import io.spine.server.commandbus.CommandScheduler;
import io.spine.server.commandbus.ExecutorCommandScheduler;
import io.spine.server.delivery.Delivery;
import io.spine.server.storage.StorageFactory;
import io.spine.server.storage.memory.InMemoryStorageFactory;
import io.spine.server.trace.TracerFactory;
import io.spine.server.transport.TransportFactory;
import io.spine.server.transport.memory.InMemoryTransportFactory;
import io.spine.type.TypeUrl;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.server.storage.system.SystemAwareStorageFactory.wrap;
import static io.spine.util.Exceptions.newIllegalStateException;

/**
 * The server conditions and configuration under which the application operates.
 *
 * <h2>Configuration</h2>
 *
 * <p>Some parts of the {@code ServerEnvironment} can be customized based on the {@code
 * EnvironmentType}. To do so, one of the overloads of the {@code use} method can be called.
 * Two environment types exist out of the box: {@link Tests} and {@link DefaultMode}.
 * For example:
 * <pre>
 *
 *     ServerEnvironment.under(Production.class)
 *                      .use(productionStorageFactory)
 *                      .use(memoizingTracerFactory);
 *     ServerEnvironment.under(Tests.class)
 *                      .use(testingStorageFactory);
 * </pre>
 * A custom environment type may also be used:
 * <pre>
 *
 *     final class StagingEnvironment extends EnvironmentType {
 *         ...
 *     }
 *
 *     ServerEnvironment.under(Staging.class)
 *                      .use(inMemoryStorageFactory);
 * </pre>
 *
 * <p>If {@code Staging} is {@link Environment#type() enabled}, the specified value is going to be
 * returned on {@link #storageFactory()}.
 *
 * <h3>Relation to Bounded Contexts</h3>
 *
 * <p>A {@code ServerEnvironment} instance is a singleton. It means that all Bounded Contexts
 * that are deployed within the same JVM share the configuration of the server environment.
 * Namely, under the same environment type, all Bounded Contexts will share the same storage
 * factory, transport factory, delivery and tracing tools.
 *
 * <p>If one decides that some of the Bounded Contexts of an application require custom
 * settings, they should arrange a separate deployable artifact and distinct configuration
 * of the respective {@code ServerEnvironment} for those Bounded Contexts. In this way,
 * the Contexts would reside in their own JVMs and not overlap on interacting with this singleton.
 *
 * <p>Within a JVM, entities with a given state type can be served by only one
 * Bounded Context. Adding a repository to a context when another context already serves
 * the same entity state type fails with {@link DuplicateEntityStateException}.
 * The check is {@linkplain TypeConfigurator#checkDuplicateEntityStates(boolean) off}
 * in tests by default.
 */
public final class ServerEnvironment implements Closeable {

    private static final ServerEnvironment INSTANCE = new ServerEnvironment();

    /**
     * The identifier of the server instance running in scope of this application.
     *
     * <p>It is currently impossible to set the node identifier directly.
     * This is subject to change in the future framework versions.
     */
    private final NodeId nodeId;

    /**
     * The strategy of delivering the messages received by entity repositories
     * to the entity instances.
     *
     * <p>If not {@linkplain TypeConfigurator#use(Delivery) configured by the end-user},
     * initialized with the {@linkplain Delivery#local() local} delivery by default.
     *
     * <p>Differs between {@linkplain EnvironmentType environment types}.
     */
    private final EnvSetting<Delivery> delivery = new EnvSetting<>();

    /**
     * Storage factory setting.
     *
     * <p>Defaults to an {@code InMemoryStorageFactory} for tests.
     */
    private final EnvSetting<StorageFactory> storageFactory =
            new EnvSetting<>(Tests.class, () -> wrap(InMemoryStorageFactory.newInstance()));

    /**
     * The setting for the factory of {@code Tracer}s.
     */
    private final EnvSetting<TracerFactory> tracerFactory = new EnvSetting<>();

    /**
     * The setting for the factory for channel-based transport.
     *
     * <p>Defaults to an {@code InMemoryTransportFactory} for tests.
     */
    private final EnvSetting<TransportFactory> transportFactory =
            new EnvSetting<>(Tests.class, InMemoryTransportFactory::newInstance);

    /**
     * Tells if adding a repository to a Bounded Context fails when another open
     * context serves entities with the same state type.
     *
     * <p>Defaults to {@code false} for tests. For other environment types, an absent value
     * means {@code true}.
     */
    private final EnvSetting<Boolean> checkDuplicateEntityStates =
            new EnvSetting<>(Tests.class, () -> false);

    /**
     * The record of which open Bounded Context serves which entity state type.
     *
     * <p>Unlike the settings, it is not cleared on {@link #reset()}, because it describes
     * the contexts that are still open.
     */
    private final ServedStateTypes servedStateTypes = new ServedStateTypes();

    /**
     * Provides schedulers used by all {@code CommandBus} instances of this environment.
     */
    private Supplier<CommandScheduler> commandScheduler;

    /**
     * Flag indicating whether this environment has been closed.
     */
    private volatile boolean closed = false;

    private ServerEnvironment() {
        nodeId = NodeId.newBuilder()
                       .setValue(Identifier.newUuid())
                       .build();
        commandScheduler = ExecutorCommandScheduler::new;
    }

    /**
     * Returns a singleton instance.
     */
    public static ServerEnvironment instance() {
        return INSTANCE;
    }

    /**
     * Obtains the type of the current server environment.
     *
     * @apiNote This is a convenience method for server-side configuration code, which
     *         simply delegates to {@link Environment#type()}.
     */
    public Class<? extends EnvironmentType<?>> type() {
        return Environment.instance().type();
    }

    /**
     * Returns the delivery mechanism specific to this environment.
     *
     * <p>Unless {@linkplain TypeConfigurator#use(Delivery) updated manually}, returns
     * a {@linkplain Delivery#local() local implementation} of {@code Delivery}.
     */
    public synchronized Delivery delivery() {
        var currentEnv = environment().type();
        var currentDelivery = this.delivery.optionalValue(currentEnv);
        if (currentDelivery.isPresent()) {
            return currentDelivery.get();
        }
        var localDelivery = Delivery.local();
        this.delivery.use(localDelivery, currentEnv);
        return localDelivery;
    }

    /**
     * Obtains command scheduling mechanism used by {@code CommandBus} in this environment.
     */
    public CommandScheduler newCommandScheduler() {
        return commandScheduler.get();
    }

    /**
     * Assigns command scheduling mechanism used at this environment by all
     * {@code CommandBus} instances.
     *
     * <p>If not configured, {@link ExecutorCommandScheduler} is used.
     */
    public void scheduleCommandsUsing(Supplier<CommandScheduler> commandScheduler) {
        checkNotNull(commandScheduler);
        this.commandScheduler = commandScheduler;
    }

    /**
     * Obtains the identifier of the server node, on which this code is running at the moment.
     *
     * <p>At the moment, the node identifier is always UUID-generated. In future versions of the
     * framework it is expected to become configurable.
     */
    public NodeId nodeId() {
        return nodeId;
    }

    /**
     * Obtains the {@link TracerFactory} associated with the current environment, if it was set.
     */
    public Optional<TracerFactory> tracing() {
        var currentType = environment().type();
        return this.tracerFactory.optionalValue(currentType);
    }

    /**
     * Obtains the storage factory for the current environment.
     *
     * <p>For tests, if the value was not set, defaults to a new {@code InMemoryStorageFactory}.
     *
     * <p>For other environments, if the value was not set, throws a {@code IllegalStateException}.
     *
     * @return {@code StorageFactory} instance for the storage for the current environment
     * @throws IllegalStateException
     *         if the value {@code StorageFactory} was not
     *         {@linkplain TypeConfigurator#use(StorageFactory) configured} prior to the call
     */
    public StorageFactory storageFactory() {
        var type = environment().type();
        var result = storageFactory.optionalValue(type)
                                   .orElseThrow(() -> SuggestConfiguring.storageFactory(type));
        return result;
    }

    /**
     * Returns a storage factory for the current environment, or an empty {@code Optional} if it
     * was not configured.
     */
    @Internal
    public Optional<StorageFactory> optionalStorageFactory() {
        var type = environment().type();
        return storageFactory.optionalValue(type);
    }

    /**
     * Obtains the transport factory for the current environment.
     *
     * <p>In the {@linkplain Tests testing environment}, if the factory was not assigned, assigns
     * a new {@code InMemoryTransportFactory} and returns it.
     *
     * <p>For all other environment types, throws an {@code IllegalStateException}.
     *
     * <p>If the factory is not assigned in the Tests mode, assigns the instance of
     * {@link InMemoryTransportFactory} and returns it.
     */
    public TransportFactory transportFactory() {
        var type = environment().type();
        var result = transportFactory.optionalValue(type)
                                     .orElseThrow(() -> SuggestConfiguring.transportFactory(type));

        return result;
    }

    /**
     * Obtains the names of the open Bounded Contexts that serve entities with
     * the given state type.
     *
     * <p>The list has more than one name only if the check of duplicate entity states was
     * {@linkplain TypeConfigurator#checkDuplicateEntityStates(boolean) off} when the contexts
     * were given their repositories. This is the default in tests.
     *
     * @param stateType
     *         the state type of the entities
     * @return the names of the contexts in the order they started serving the type,
     *         or an empty list if no open context serves it
     */
    @Internal
    public ImmutableList<BoundedContextName> contextsServing(TypeUrl stateType) {
        return servedStateTypes.contextsServing(stateType);
    }

    /**
     * Records that the given context serves entities with the given state type.
     *
     * @return {@code true} if this call recorded the context for the type,
     *         {@code false} if the context had been recorded for it before
     * @throws DuplicateEntityStateException
     *         if another open context already serves the type, and the check of
     *         duplicate entity states is on
     */
    boolean serve(BoundedContext context, TypeUrl stateType) {
        var check = checkDuplicateEntityStates.optionalValue(environment().type())
                                              .orElse(true);
        return servedStateTypes.serve(context, stateType, check);
    }

    /**
     * Records that the given context no longer serves entities with the given state type.
     */
    void forget(BoundedContext context, TypeUrl stateType) {
        servedStateTypes.forget(context, stateType);
    }

    /**
     * Records that the given context, which is closing, no longer serves any entities.
     */
    void forget(BoundedContext context) {
        servedStateTypes.forget(context);
    }

    private static Environment environment() {
        return Environment.instance();
    }

    /**
     * This is test-only method required for cleaning of the server environment instance in tests.
     */
    @VisibleForTesting
    public void reset() {
        transportFactory.reset();
        tracerFactory.reset();
        storageFactory.reset();
        checkDuplicateEntityStates.reset();
        delivery.reset();
        var currentEnv = environment().type();
        delivery.use(Delivery.local(), currentEnv);
    }

    /**
     * Tells if the environment is still open.
     */
    @Override
    public boolean isOpen() {
        return !closed;
    }

    /**
     * Releases resources associated with this instance.
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closeFactory("TracerFactory", tracerFactory);
        closeFactory("TransportFactory", transportFactory);
        closeFactory("StorageFactory", storageFactory);
        closed = true;
    }

    /**
     * Closes a factory from an environment setting, wrapping any exceptions.
     */
    private static <T extends Closeable> void closeFactory(
            String factoryName,
            EnvSetting<T> setting
    ) {
        try {
            setting.apply(Closeable::close);
        } catch (RuntimeException e) {
            throw newIllegalStateException(e, "Failed to close `%s`.", factoryName);
        }
    }

    /**
     * Starts a flowing API chain for configuring {@code ServerEnvironment} for the passed type.
     */
    public static TypeConfigurator under(Class<? extends EnvironmentType<?>> type) {
        checkNotNull(type);
        return new TypeConfigurator(type);
    }

    /**
     * Starts a flowing API chain for configuring {@code ServerEnvironment} for the passed type.
     *
     * @deprecated Use {@link #under(Class)} instead. Unlike this method, {@code under()} does not
     *         require backticks when called from Kotlin, where {@code when} is a keyword.
     */
    @Deprecated
    public static TypeConfigurator when(Class<? extends EnvironmentType<?>> type) {
        return under(type);
    }

    /**
     * Allows configuring values used by the {@code ServerEnvironment} for the given type.
     */
    public static class TypeConfigurator {

        private final ServerEnvironment se;
        private final Class<? extends EnvironmentType<?>> type;

        private TypeConfigurator(Class<? extends EnvironmentType<?>> type) {
            this.se = instance();
            // Clear the flag for the case of unit tests that closed the singleton before.
            this.se.closed = false;
            if (CustomEnvironmentType.class.isAssignableFrom(type)) {
                registerCustomType(type);
            }
            this.type = checkNotNull(type);
        }

        private static void registerCustomType(Class<? extends EnvironmentType<?>> type) {
            @SuppressWarnings("unchecked") // checked by calling site.
            var customType =
                    (Class<? extends CustomEnvironmentType<?>>) type;
            Environment.instance()
                       .register(customType);
        }

        /**
         * Obtains the type of the environment being currently configured.
         */
        @VisibleForTesting
        public Class<? extends EnvironmentType<?>> type() {
            return type;
        }

        /**
         * Assigns the specified {@code Delivery} for the selected environment.
         *
         * @see #useDelivery(Fn)
         */
        @CanIgnoreReturnValue
        public TypeConfigurator use(Delivery delivery) {
            checkNotNull(delivery);
            se.delivery.use(delivery, type);
            return this;
        }

        /**
         * Assigns a {@code Delivery} obtained from the passed function.
         *
         * <p>In case the {@code Delivery} is never requested for the current server
         * environment type, the passed function will be not invoked.
         *
         * @param fn
         *         the function to provide the {@code Delivery} in response to
         *         the currently configured server environment type
         * @see #use(Delivery)
         */
        @CanIgnoreReturnValue
        public TypeConfigurator useDelivery(Fn<Delivery> fn) {
            checkNotNull(fn);
            se.delivery.lazyUse(() -> fn.apply(type), type);
            return this;
        }

        /**
         * Assigns {@code TracerFactory} for the selected environment.
         *
         * @see #useTracerFactory(Fn)
         */
        @CanIgnoreReturnValue
        public TypeConfigurator use(TracerFactory factory) {
            checkNotNull(factory);
            se.tracerFactory.use(factory, type);
            return this;
        }

        /**
         * Lazily uses the {@code TracerFactory} obtained from the passed function.
         *
         * <p>In case the {@code TracerFactory} is never requested for the current server
         * environment type, the passed function will be not invoked.
         *
         * @param fn
         *         the function to provide the {@code TracerFactory} in response to
         *         the currently configured server environment type
         * @see #use(TracerFactory)
         */
        @CanIgnoreReturnValue
        public TypeConfigurator useTracerFactory(Fn<TracerFactory> fn) {
            checkNotNull(fn);
            se.tracerFactory.lazyUse(() -> fn.apply(type), type);
            return this;
        }

        /**
         * Assigns the specified transport factory for the selected environment.
         *
         * @see #useTransportFactory(Fn)
         */
        @CanIgnoreReturnValue
        public TypeConfigurator use(TransportFactory factory) {
            checkNotNull(factory);
            se.transportFactory.use(factory, type);
            return this;
        }

        /**
         * Assigns a {@code TransportFactory} obtained from the passed function.
         *
         * <p>In case the {@code TransportFactory} is never requested for the current server
         * environment type, the passed function will be not invoked.
         *
         * @param fn
         *         the function to provide the {@code TransportFactory} in response to
         *         the currently configured server environment type
         * @see #use(TransportFactory)
         */
        @CanIgnoreReturnValue
        public TypeConfigurator useTransportFactory(Fn<TransportFactory> fn) {
            checkNotNull(fn);
            se.transportFactory.lazyUse(() -> fn.apply(type), type);
            return this;
        }

        /**
         * Assigns the specified {@code StorageFactory} for the selected environment.
         *
         * @see #useStorageFactory(Fn)
         */
        @CanIgnoreReturnValue
        public TypeConfigurator use(StorageFactory factory) {
            var wrapped = wrap(factory);
            se.storageFactory.use(wrapped, type);
            return this;
        }

        /**
         * Assigns a {@code StorageFactory} obtained from the passed function.
         *
         * <p>In case the {@code StorageFactory} is never requested for the current server
         * environment type, the passed function will be not invoked.
         *
         * @param fn
         *         the function to provide the {@code StorageFactory} in response to
         *         the currently configured server environment type
         * @see #use(StorageFactory)
         */
        @CanIgnoreReturnValue
        public TypeConfigurator useStorageFactory(Fn<StorageFactory> fn) {
            checkNotNull(fn);
            se.storageFactory.lazyUse(() -> {
                var factory = fn.apply(type);
                var wrapped = wrap(factory);
                return wrapped;
            }, type);
            return this;
        }

        /**
         * Turns on or off the check that entities with a given state type are served by
         * only one Bounded Context.
         *
         * <p>When the check is on, adding a repository to a context fails with
         * {@link DuplicateEntityStateException} if another open context already serves
         * entities with the same state type. In tests, which build many contexts in one JVM,
         * closing a context at the end of a test frees its entity state types.
         *
         * <p>By default, the check is off for {@link Tests} and on for other environment types.
         *
         * @param check
         *         {@code true} to turn the check on, {@code false} to turn it off
         */
        @CanIgnoreReturnValue
        public TypeConfigurator checkDuplicateEntityStates(boolean check) {
            se.checkDuplicateEntityStates.use(check, type);
            return this;
        }
    }

    /**
     * A function that accepts a class of {@link EnvironmentType} and returns
     * a value {@link ServerEnvironment#under(Class) configured} in a {@code ServerEnvironment}.
     *
     * @param <R> the type of the configured value
     */
    @FunctionalInterface
    @SuppressWarnings("NewClassNamingConvention") // two letters for this name is fine.
    public interface Fn<R> extends Function<Class<? extends EnvironmentType<?>>, R> {
    }

    /**
     * Factory methods for creating exceptions raised when a feature of server
     * environment is not properly configured.
     */
    private static class SuggestConfiguring {

        /** Prevents instantiation of this utility class. */
        private SuggestConfiguring() {
        }

        private static IllegalStateException
        storageFactory(Class<? extends EnvironmentType<?>> type) {
            return raise(
                    "The storage factory for the environment `%s` was not configured.",
                    type, "storageFactory"
            );
        }

        private static IllegalStateException
        transportFactory(Class<? extends EnvironmentType<?>> type) {
            return raise(
                    "Transport factory is not assigned for the current environment `%s`.",
                    type, "transportFactory"
            );
        }

        /**
         * Creates {@code IllegalStateException} with the error message suggesting configuring
         * a feature of {@code ServerEnvironment}.
         *
         * @param prefixFmt
         *         the first part of the error message that explains the error, containing
         *         a format parameter for the type name of the current environment
         * @param type
         *         the type of the environment under which we detected the error
         * @param featureParamName
         *         the name of the parameter passed to the {@code use()} method
         */
        private static IllegalStateException
        raise(String prefixFmt, Class<? extends EnvironmentType<?>> type, String featureParamName) {
            var typeName = type.getSimpleName();
            var fmt = prefixFmt + " Please call `ServerEnvironment.under(%s.class).use(%s);`.";
            return newIllegalStateException(fmt, typeName, typeName, featureParamName);
        }
    }
}
