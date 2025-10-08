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
        grpcContainer.port().ifPresent(
                p -> info.log(() -> format("Server started, listening to the port %d.", p)));
        grpcContainer.serverName().ifPresent(
                n -> info.log(() -> format("In-process server started with the name `%s`.", n)));
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
     */
    public void shutdown() {
        logger().atInfo().log(() -> "Shutting down the server...");
        grpcContainer.shutdown();
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
        logger().atInfo().log(() -> "Server shut down.");
    }

    /**
     * Initiates a forceful shutdown in which preexisting and new calls are rejected.
     *
     * <p>The method returns when the server becomes terminated.
     * The most common usage scenario for this method is clean-up in unit tests
     * (e.g. {@literal @}{@code AfterEach} in JUnit5) that involve client-server communications.
     */
    @VisibleForTesting
    public void shutdownAndWait() {
        grpcContainer.shutdownNowAndWait();
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
         * Adds a builder for a {@code BoundedContext} to be added the server.
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
            if (serverName().isPresent()) {
                result = GrpcContainer.inProcess(serverName().get());
            } else {
                int port = port().orElseThrow(() -> newIllegalStateException(
                        "Neither `port` nor `serverName` assigned."));
                result = GrpcContainer.atPort(port);
            }
            return result;
        }
    }
}
