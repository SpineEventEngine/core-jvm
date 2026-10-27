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

import com.google.common.collect.ImmutableSet;
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
import static io.spine.util.Exceptions.newIllegalStateException;
import static io.spine.util.Preconditions2.checkNotEmptyOrBlank;
import static java.lang.String.format;

/**
 * Exposes one or more Bounded Contexts via gRPC.
 *
 * <p>The exposed services are
 * <ul>
 *     <li>{@link CommandService},
 *     <li>{@link QueryService},
 *     <li>{@link SubscriptionService}.
 * </ul>
 */
public final class Server implements WithLogging {

    /** Bounded Contexts exposed by the server. */
    private final ImmutableSet<BoundedContext> contexts;

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
        this.contexts = builder.contexts();
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
            var p = grpcContainer.getBoundPort();
            info.log(() -> format("Server started, listening to the port %d.", p));
        }
        if (grpcContainer.hasServerName()) {
            var n = grpcContainer.getServerName();
            info.log(() -> format("In-process server started with the name `%s`.", n));
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
     * <p>Also closes the Bounded Contexts of the server, logging an error for each one
     * that fails to close.
     */
    public void shutdown() {
        logger().atInfo().log(() -> "Shutting down the server...");
        grpcContainer.shutdown();
        closeContexts();
        logger().atInfo().log(() -> "Server shut down.");
    }

    /**
     * Initiates a forceful shutdown in which preexisting and new calls are rejected.
     *
     * <p>The method returns when the server becomes terminated and its Bounded Contexts
     * are closed, as {@link #shutdown()} closes them.
     *
     * <p>The most common usage scenario for this method is clean-up in unit tests
     * (e.g. {@literal @}{@code AfterEach} in JUnit5) that involve client-server communications.
     */
    @VisibleForTesting
    public void shutdownAndWait() {
        try {
            grpcContainer.shutdownNowAndWait();
        } finally {
            closeContexts();
        }
    }

    /**
     * Closes the Bounded Contexts of the server, logging an error for each one that fails
     * to close.
     */
    private void closeContexts() {
        contexts.forEach(context -> {
            try {
                context.close();
            } catch (Exception e) {
                var contextName = context.name()
                                         .getValue();
                logger().atError().withCause(e).log(() -> format(
                        "Unable to close the `%s` Context.", contextName));
            }
        });
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
        private final Set<BoundedContextBuilder> contextBuilders = new HashSet<>();
        private @MonotonicNonNull ImmutableSet<BoundedContext> contexts;
        private @MonotonicNonNull QueryService queryService;
        private @MonotonicNonNull SubscriptionService subscriptionService;
        private @MonotonicNonNull CommandService commandService;

        private Builder(@Nullable Integer port, @Nullable String serverName) {
            super(port, serverName);
        }

        /**
         * Adds a builder for a {@code BoundedContext} to be added to the server.
         */
        @CanIgnoreReturnValue
        public Builder add(BoundedContextBuilder context) {
            checkNotNull(context);
            contextBuilders.add(context);
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
         * Creates a new instance of the server.
         */
        public Server build() {
            var result = new Server(this);
            return result;
        }

        private ImmutableSet<BoundedContext> contexts() {
            if (contexts == null) {
                ImmutableSet.Builder<BoundedContext> result = ImmutableSet.builder();
                contextBuilders.forEach(c -> result.add(c.build()));
                contexts = result.build();
            }
            return contexts;
        }

        /**
         * Creates a container for the passed Bounded Contexts.
         */
        private GrpcContainer createContainer() {
            var commandService = CommandService.newBuilder();
            var queryService = QueryService.newBuilder();
            var subscriptionService = SubscriptionService.newBuilder();
            contexts().forEach(context -> {
                commandService.add(context);
                queryService.add(context);
                subscriptionService.add(context);
            });
            this.queryService = queryService.build();
            this.subscriptionService = subscriptionService.build();
            this.commandService = commandService.build();
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
