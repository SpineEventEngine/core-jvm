/*
 * Copyright 2022, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Redistribution and use in source and/or binary forms, with or without
 * modification, must retain the above copyright notice and the following
 * disclaimer.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package io.spine.server;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.spine.annotation.Internal;
import io.spine.annotation.VisibleForTesting;
import io.spine.base.Identifier;
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

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.server.storage.system.SystemAwareStorageFactory.wrap;
import static io.spine.util.Exceptions.newIllegalStateException;

/**
 * The server conditions and configuration under which the application operates.
 *
 * <h3>Configuration</h3>
 *
 * <p>Some parts of the {@code ServerEnvironment} can be customized based on the {@code
 * EnvironmentType}. To do so, one of the overloads of the {@code use} method can be called.
 * Two environment types exist out of the box: {@link Tests} and {@link DefaultMode}.
 * For example:
 * <pre>
 *
 *     ServerEnvironment.when(Production.class)
 *                      .use(productionStorageFactory)
 *                      .use(memoizingTracerFactory);
 *     ServerEnvironment.when(Tests.class)
 *                      .use(testingStorageFactory);
 * </pre>
 * A custom environment type may also be used:
 * <pre>
 *
 *     final class StagingEnvironment extends EnvironmentType {
 *         ...
 *     }
 *
 *     ServerEnvironment.when(Staging.class)
 *                      .use(inMemoryStorageFactory);
 * </pre>
 *
 * <p>If {@code Staging} is {@link Environment#type() enabled}, the specified value is going to be
 * returned on {@link #storageFactory()}.
 *
 * <h3>Relation to Bounded Contexts</h3>
 *
 * <p>A {@code ServerEnvironment} instance is a singleton. It means that all Bounded Contexts
 * which are deployed within the same JVM share the configuration of the server environment.
 * Namely, under the same environment type, all Bounded Contexts will share the same storage
 * factory, transport factory, delivery and tracing tools.
 *
 * <p>If one decides that some of the Bounded Contexts of an application requires custom
 * settings, they should arrange a separate deployable artifact and distinct configuration
 * of the respective {@code ServerEnvironment} for those Bounded Contexts. In this way,
 * the Contexts would reside in their own JVMs and not overlap on interacting with this singleton.
 */
public final class ServerEnvironment implements AutoCloseable {

    private static final ServerEnvironment INSTANCE = new ServerEnvironment();

    /**
     * The deployment detector is instantiated with a system {@link DeploymentDetector} and
     * can be reassigned the value using {@link #configureDeployment(Supplier)}.
     *
     * <p>Value from this supplier are used to {@linkplain #deploymentType()
     * get the deployment type}.
     */
    private Supplier<DeploymentType> deploymentDetector = DeploymentDetector.newInstance();

    /**
     * The identifier of the server instance running in scope of this application.
     *
     * <p>It is currently impossible to set the node identifier directly.
     * This is a subject to change in the future framework versions.
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
     * Provides schedulers used by all {@code CommandBus} instances of this environment.
     */
    private Supplier<CommandScheduler> commandScheduler;

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
     * The type of the environment application is deployed to.
     */
    public DeploymentType deploymentType() {
        return deploymentDetector.get();
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
     * Sets the default {@linkplain DeploymentType deployment type}
     * {@linkplain Supplier supplier} which utilizes system properties.
     */
    private void resetDeploymentType() {
        var supplier = DeploymentDetector.newInstance();
        configureDeployment(supplier);
    }

    /**
     * Makes the {@link #deploymentType()} return the values from the provided supplier.
     *
     * <p>When supplying your own deployment type in tests, remember to
     * {@linkplain #reset() reset it} during tear down.
     */
    @VisibleForTesting
    public void configureDeployment(Supplier<DeploymentType> supplier) {
        checkNotNull(supplier);
        deploymentDetector = supplier;
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
        delivery.reset();
        var currentEnv = environment().type();
        delivery.use(Delivery.local(), currentEnv);
        resetDeploymentType();
    }

    /**
     * Releases resources associated with this instance.
     */
    @Override
    public void close() throws Exception {
        tracerFactory.apply(AutoCloseable::close);
        transportFactory.apply(AutoCloseable::close);
        storageFactory.apply(AutoCloseable::close);
    }

    /**
     * Starts flowing API chain for configuring {@code ServerEnvironment} for the passed type.
     */
    public static TypeConfigurator when(Class<? extends EnvironmentType<?>> type) {
        checkNotNull(type);
        return new TypeConfigurator(type);
    }

    /**
     * Allows configuring values used by the {@code ServerEnvironment} for the given type.
     */
    public static class TypeConfigurator {

        private final ServerEnvironment se;
        private final Class<? extends EnvironmentType<?>> type;

        private TypeConfigurator(Class<? extends EnvironmentType<?>> type) {
            this.se = instance();
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
         * @see #useDelivery(ServerEnvironment.Fn)
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
         * @see #useTracerFactory(ServerEnvironment.Fn)
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
         * @see #useTransportFactory(ServerEnvironment.Fn)
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
         * @see #useStorageFactory(ServerEnvironment.Fn)
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
    }

    /**
     * A function which accepts a class of {@link EnvironmentType} and returns
     * a value {@link ServerEnvironment#when(Class) configured} in a {@code ServerEnvironment}.
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
         *         the first part of the error message which explains the error, containing
         *         format parameter for the type name of the current environment
         * @param type
         *         the type of the environment under which we depected the error
         * @param featureParamName
         *         the name of the parameter passed to the {@code use()} method
         */
        private static IllegalStateException
        raise(String prefixFmt, Class<? extends EnvironmentType<?>> type, String featureParamName) {
            var typeName = type.getSimpleName();
            var fmt = prefixFmt + " Please call `ServerEnvironment.when(%s.class).use(%s);`.";
            return newIllegalStateException(fmt, typeName, typeName, featureParamName);
        }
    }
}
