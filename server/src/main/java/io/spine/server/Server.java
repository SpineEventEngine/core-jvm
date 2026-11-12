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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.grpc.BindableService;
import io.spine.annotation.VisibleForTesting;
import io.spine.logging.WithLogging;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static io.spine.util.Preconditions2.checkNotEmptyOrBlank;
import static java.lang.String.format;

/**
 * Exposes the Bounded Contexts of the {@link Application} via gRPC.
 *
 * <p>The exposed services are
 * <ul>
 *     <li>{@link CommandService},
 *     <li>{@link QueryService},
 *     <li>{@link SubscriptionService}.
 * </ul>
 *
 * <p>The server exposes the contexts of the application given to its builder:
 * <pre>{@code
 * var application = Application.newBuilder()
 *         .setServerEnvironment(environment)
 *         .add(BoundedContext.singleTenant("Users").add(new UserRepository()))
 *         .add(BoundedContext.singleTenant("Tasks").add(new TaskRepository()))
 *         .build();
 * var server = Server.atPort(8484)
 *         .setApplication(application)
 *         .build();
 * server.start();
 * }</pre>
 *
 * <p>{@linkplain #shutdown() Shutting down} the server leaves the application open, since
 * several servers may expose one application. Shut down the servers first, and then close
 * the application:
 * <pre>{@code
 * server.shutdown();
 * application.close();
 * }</pre>
 */
public final class Server implements WithLogging {

    /** The container for Command- and Query- services. */
    private final GrpcContainer grpcContainer;
    private final QueryService queryService;
    private final SubscriptionService subscriptionService;
    private final CommandService commandService;

    /**
     * Initiates creating a server exposed at the passed port.
     *
     * <p>Passing {@code 0} asks the operating system to assign a free port when the server
     * {@linkplain #start() starts}. This is the recommended arrangement for tests, as it cannot
     * fail with "address already in use" the way a fixed or randomly guessed port can. Obtain
     * the assigned port via {@link #getBoundPort()} once the server is started.
     */
    public static Builder atPort(int port) {
        return new Builder(port, null);
    }

    /**
     * Initiates creating an in-process server exposed with the given name.
     *
     * <p>The server is full-featured, high performance, and is useful in testing.
     */
    public static Builder inProcess(String serverName) {
        checkNotEmptyOrBlank(serverName);
        return new Builder(null, serverName);
    }

    private Server(Builder builder) {
        this.grpcContainer = builder.createContainer();
        this.queryService = checkNotNull(builder.queryService);
        this.subscriptionService = checkNotNull(builder.subscriptionService);
        this.commandService = checkNotNull(builder.commandService);
    }

    /**
     * Starts the server.
     *
     * @throws IOException if the service could not be started
     */
    public void start() throws IOException {
        grpcContainer.start();
        grpcContainer.addShutdownHook();
        var info = logger().atInfo();
        if (grpcContainer.hasPort()) {
            var port = grpcContainer.getBoundPort();
            info.log(() -> format("Server started, listening to the port %d.", port));
        }
        if (grpcContainer.hasServerName()) {
            var name = grpcContainer.getServerName();
            info.log(() -> format("In-process server started with the name `%s`.", name));
        }
    }

    /**
     * Waits for the server to become terminated.
     */
    @SuppressWarnings("unused")
    public void awaitTermination() {
        grpcContainer.awaitTermination();
    }

    /**
     * Initiates an orderly shutdown in which existing calls continue but new calls are rejected.
     *
     * <p>The {@link Application} of the server stays open. Close it once all the servers that
     * expose it are shut down.
     */
    public void shutdown() {
        logger().atInfo().log(() -> "Shutting down the server...");
        grpcContainer.shutdown();
        logger().atInfo().log(() -> "Server shut down.");
    }

    /**
     * Initiates a forceful shutdown in which preexisting and new calls are rejected.
     *
     * <p>The method returns when the server becomes terminated. The {@link Application} of
     * the server stays open, as it does after {@link #shutdown()}.
     *
     * <p>The most common usage scenario for this method is clean-up in unit tests
     * (e.g. {@literal @}{@code AfterEach} in JUnit5) that involve client-server communications.
     */
    @VisibleForTesting
    public void shutdownAndWait() {
        grpcContainer.shutdownNowAndWait();
    }

    /**
     * Obtains the port at which this server actually listens.
     *
     * <p>When the server is created {@linkplain #atPort(int) at the port} {@code 0}, the actual
     * port is assigned by the operating system upon {@linkplain #start() start}, and this method
     * is the only way to learn it.
     *
     * @return the port this server listens to
     * @throws IllegalStateException
     *         if this is an {@linkplain #inProcess(String) in-process} server, or if the server
     *         was not started yet or is already shut down
     */
    public int getBoundPort() {
        return grpcContainer.getBoundPort();
    }

    /**
     * Obtains the {@link QueryService} exposed by this server.
     */
    public QueryService queryService() {
        return queryService;
    }

    /**
     * Obtains the {@link SubscriptionService} exposed by this server.
     */
    public SubscriptionService subscriptionService() {
        return subscriptionService;
    }

    /**
     * Obtains the {@link CommandService} exposed by this server.
     */
    public CommandService commandService() {
        return commandService;
    }

    /**
     * The builder for the server.
     */
    public static final class Builder extends ConnectionBuilder {

        private final Set<BindableService> extraServices = new HashSet<>();

        /** The application whose contexts the server serves. */
        private @MonotonicNonNull Application application;

        private @MonotonicNonNull QueryService queryService;
        private @MonotonicNonNull SubscriptionService subscriptionService;
        private @MonotonicNonNull CommandService commandService;

        private Builder(@Nullable Integer port, @Nullable String serverName) {
            super(port, serverName);
        }

        /**
         * Sets the application whose Bounded Contexts the server exposes.
         *
         * <p>The server does not close the application when it shuts down, since several
         * servers may expose one application. Close the application after the servers.
         */
        @CanIgnoreReturnValue
        public Builder setApplication(Application application) {
            this.application = checkNotNull(application);
            return this;
        }

        /**
         * Adds a gRPC service to the built server.
         *
         * <p>By default, the {@linkplain CommandService Command}, {@linkplain QueryService Query},
         * and {@linkplain SubscriptionService Subscription} services are present in the server.
         * But the users may add any other gRPC services to work alongside the standard ones,
         * e.g. for monitoring, warmup procedures, etc.
         */
        @CanIgnoreReturnValue
        public Builder include(BindableService service) {
            checkNotNull(service);
            extraServices.add(service);
            return this;
        }

        /**
         * Creates a new instance of the server, which exposes the Bounded Contexts
         * of the {@linkplain #setApplication(Application) application}.
         *
         * @throws IllegalStateException
         *         if the application is not set, or is closed
         */
        public Server build() {
            var result = new Server(this);
            return result;
        }

        /**
         * Obtains the application that is set at the moment.
         *
         * <p>The builder creates the services from it each time it builds a server, so
         * a builder that is reused with another application builds a server of that
         * application.
         */
        private Application application() {
            checkState(application != null,
                       "The application of the server is not set." +
                               " Please call `setApplication()`.");
            checkState(application.isOpen(),
                       "The application of the server is closed.");
            return application;
        }

        /**
         * Creates a container for the Bounded Contexts of the server.
         */
        private GrpcContainer createContainer() {
            var application = application();
            this.queryService = QueryService.of(application);
            this.subscriptionService = SubscriptionService.of(application);
            this.commandService = CommandService.of(application);
            var builder = createContainerBuilder();
            builder.addService(this.commandService)
                   .addService(this.queryService)
                   .addService(this.subscriptionService);
            extraServices.forEach(builder::addService);
            return builder.build();
        }

        private GrpcContainer.Builder createContainerBuilder() {
            GrpcContainer.Builder result;
            if (hasServerName()) {
                result = GrpcContainer.inProcess(getServerName());
            } else {
                checkState(hasPort(), "Neither `port` nor `serverName` assigned.");
                result = GrpcContainer.atPort(getPort());
            }
            return result;
        }
    }
}
