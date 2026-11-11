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

package io.spine.client;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.spine.annotation.VisibleForTesting;
import io.spine.base.EntityState;
import io.spine.client.grpc.CommandServiceGrpc;
import io.spine.client.grpc.CommandServiceGrpc.CommandServiceBlockingStub;
import io.spine.client.grpc.QueryServiceGrpc;
import io.spine.client.grpc.QueryServiceGrpc.QueryServiceBlockingStub;
import io.spine.core.Ack;
import io.spine.core.Command;
import io.spine.core.TenantId;
import io.spine.core.UserId;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.util.Exceptions.illegalStateWithCauseOf;
import static io.spine.util.Preconditions2.checkNotDefaultArg;
import static io.spine.util.Preconditions2.checkNotEmptyOrBlank;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * The gRPC-based gateway for backend services such as {@code CommandService},
 * {@code QueryService}, or {@code SubscriptionService}.
 *
 * <p>A connection can be established via {@linkplain #connectTo(String, int) host/port}
 * combination, or via an already available {@link #usingChannel(ManagedChannel) ManagedChannel}.
 *
 * <p>Multi-tenant applications need to specify {@link Builder#forTenant(TenantId) TenantId}
 * for a new client connection. Single-tenant applications do nothing about it.
 *
 * <p>Client requests can be created on behalf of a {@linkplain #asGuest() guest user},
 * if the user is not yet authenticated, and on behalf of the
 * {@linkplain #onBehalfOf(UserId) current user} after the user is authenticated.
 *
 * <p>Please note that the Spine client-side library does not define the authentication process.
 * The {@code Client} class simply relies on the fact that the {@link UserId} passed to the method
 * {@link #onBehalfOf(UserId)} represents a valid logged-in user, ID of whom the client application
 * got (presumably as a field of a {@code UserLoggedIn} event) following due authentication process.
 * The server-side code also needs to make sure that the {@link UserId} matches security
 * constraints of the backend services. Security arrangements are not a part of the Spine
 * client-side library either.
 *
 * <p>Subscriptions to {@linkplain SubscriptionRequest entity states} or
 * {@linkplain EventSubscriptionRequest events} must be {@linkplain #cancel(Subscription)
 * cancelled} when no longer needed to preserve both client-side and backend resources.
 *
 * <p>The client connection must be {@link #close() closed} when the application finishes its work.
 */
public final class Client implements AutoCloseable {

    /** The default port number for a gRPC connection. */
    public static final int DEFAULT_CLIENT_SERVICE_PORT = 50051;

    /** The default amount of time to wait when {@linkplain #close() closing} the client. */
    public static final Timeout DEFAULT_SHUTDOWN_TIMEOUT = Timeout.of(5, SECONDS);

    /** Default ID for a guest user. */
    public static final UserId DEFAULT_GUEST_ID = user("guest");

    /** The ID of the tenant in a multi-tenant application, or {@code null} if single-tenant. */
    private final @Nullable TenantId tenant;

    /** The ID of a user to be used for performing {@linkplain #asGuest() guest requests}. */
    private final UserId guestUser;

    /** The channel for gRPC requests. */
    private final ManagedChannel channel;

    /** The amount of time to wait before {@linkplain #close() closing} the client. */
    private final Timeout shutdownTimeout;

    /** The stub for communicating with the {@code QueryService}. */
    private final QueryServiceBlockingStub queryService;

    /** The stub for communicating with the {@code CommandService}. */
    private final CommandServiceBlockingStub commandService;

    /** Active subscriptions maintained by the client. */
    private final Subscriptions subscriptions;

    /** The handler for errors that may occur during async. requests initiated by this client. */
    private final @Nullable ErrorHandler streamingErrorHandler;

    /** The handler for errors returned from server side in response to posted messages. */
    private final @Nullable ServerErrorHandler serverErrorHandler;

    /**
     * Creates a builder for a client connected to the specified address.
     *
     * <p>The returned builder will create a {@code ManagedChannel} with the default configuration.
     * For a channel with custom configuration, please use {@link #usingChannel(ManagedChannel)}.
     *
     * @see #usingChannel(ManagedChannel)
     * @see #inProcess(String)
     */
    public static Builder connectTo(String host, int port) {
        checkNotEmptyOrBlank(host);
        return new Builder(host, port);
    }

    /**
     * Creates a builder for a client that will use the passed channel for the communication
     * with the backend services.
     *
     * <p>Use this method when a channel with custom configuration is needed for your client
     * application.
     *
     * @see #connectTo(String, int)
     * @see #inProcess(String)
     * @see ManagedChannel
     */
    public static Builder usingChannel(ManagedChannel channel) {
        checkNotNull(channel);
        return new Builder(channel);
    }

    /**
     * Creates a builder for the client that will be connected to the in-process
     * server with the given name.
     *
     * <p>The client is fully-featured, high performance, and useful in testing.
     *
     * @see #connectTo(String, int)
     * @see #usingChannel(ManagedChannel)
     */
    public static Builder inProcess(String serverName) {
        checkNotEmptyOrBlank(serverName);
        return new Builder(serverName);
    }

    /**
     * Creates a new client that uses the passed channel for communications
     * with the backend services.
     */
    private Client(Builder builder) {
        this.tenant = builder.tenant;
        this.guestUser = builder.guestUser;
        this.channel = checkNotNull(builder.channel);
        this.shutdownTimeout = checkNotNull(builder.shutdownTimeout);
        this.commandService = CommandServiceGrpc.newBlockingStub(channel);
        this.queryService = QueryServiceGrpc.newBlockingStub(channel);
        this.streamingErrorHandler = builder.streamingErrorHandler;
        this.serverErrorHandler = builder.serverErrorHandler;
        this.subscriptions = new Subscriptions(channel, streamingErrorHandler, serverErrorHandler);
    }

    /**
     * Obtains the tenant of this client connection in a multi-tenant application,
     * and empty {@code Optional} in a single-tenant one.
     *
     * @see Builder#forTenant(TenantId)
     */
    public Optional<TenantId> tenant() {
        return Optional.ofNullable(tenant);
    }

    /**
     * Closes the client by shutting down the gRPC connection.
     *
     * <p>Subscriptions created by this client that were not cancelled
     * {@linkplain #cancel(Subscription) directly} will be cancelled.
     *
     * @see #isOpen()
     */
    @Override
    public void close() {
        if (!isOpen()) {
            return;
        }
        subscriptions.cancelAll();
        try {
            channel.shutdown()
                   .awaitTermination(shutdownTimeout.value(), shutdownTimeout.unit());
        } catch (InterruptedException e) {
            throw illegalStateWithCauseOf(e);
        }
    }

    /**
     * Same as {@link #close()}.
     */
    public void shutdown() {
        close();
    }

    /**
     * Verifies if the client connection is open.
     *
     * @see #close()
     */
    public boolean isOpen() {
        return !channel.isTerminated();
    }

    /**
     * Creates a builder for requests on behalf of the passed user.
     *
     * @see #asGuest()
     */
    public ClientRequest onBehalfOf(UserId user) {
        checkNotDefaultArg(user);
        var request = new ClientRequest(user, this);
        if (streamingErrorHandler != null) {
            request.onStreamingError(streamingErrorHandler);
        }
        if (serverErrorHandler != null) {
            request.onServerError(serverErrorHandler);
        }
        return request;
    }

    /**
     * Creates a builder for posting guest requests.
     *
     * @see #onBehalfOf(UserId)
     */
    public ClientRequest asGuest() {
        return onBehalfOf(guestUser);
    }

    /**
     * Requests cancellation of the passed subscription.
     *
     * @see ClientRequest#subscribeTo(Class)
     * @see ClientRequest#subscribeToEvent(Class)
     * @deprecated please call {@link Subscriptions#cancel(Subscription)}
     */
    @Deprecated // Make this method package-access during next deprecation cycle.
    public void cancel(Subscription s) {
        subscriptions.cancel(s);
    }

    @VisibleForTesting
    ManagedChannel channel() {
        return channel;
    }

    @VisibleForTesting
    Timeout shutdownTimeout() {
        return shutdownTimeout;
    }

    /**
     * Obtains subscriptions created by this client.
     */
    public Subscriptions subscriptions() {
        return subscriptions;
    }

    /**
     * Creates a new request factory for the requests to be sent on behalf of the passed user.
     */
    ActorRequestFactory requestOf(UserId user) {
        return ActorRequestFactory.newBuilder()
                .setTenantId(tenant)
                .setActor(user)
                .build();
    }

    /**
     * Posts the command to the {@code CommandService}.
     */
    Ack post(Command c) {
        var ack = commandService.post(c);
        return ack;
    }

    /**
     * Queries the read-side with the specified query.
     */
    <S extends EntityState<?>> ImmutableList<S> read(Query query, Class<S> stateType) {
        var result = queryService.read(query).states(stateType);
        return result;
    }

    private static UserId user(String value) {
        checkNotEmptyOrBlank(value);
        return UserId.newBuilder()
                     .setValue(value)
                     .build();
    }

    /**
     * The builder for the client.
     */
    public static final class Builder {

        /**
         * The channel to be used in the client.
         *
         * <p>If not set directly, the channel will be created using the assigned
         * host and port values.
         */
        private ManagedChannel channel;

        /**
         * The address of the host that will be used for creating an instance
         * of {@code ManagedChannel}.
         *
         * <p>This field is {@code null} if the builder is created using an already made
         * {@code ManagedChannel}.
         */
        private @MonotonicNonNull String host;
        private int port;
        private @MonotonicNonNull Timeout shutdownTimeout;

        /**
         * The ID of the tenant in a multi-tenant application.
         *
         * <p>It is {@code null} in single-tenant applications.
         */
        private @Nullable TenantId tenant;

        /** The ID of the user for performing requests on behalf of a non-logged in user. */
        private UserId guestUser = DEFAULT_GUEST_ID;

        private @Nullable ErrorHandler streamingErrorHandler;
        private @Nullable ServerErrorHandler serverErrorHandler;

        private Builder(ManagedChannel channel) {
            this.channel = checkNotNull(channel);
        }

        private Builder(String host, int port) {
            this.host = host;
            this.port = port;
        }

        private Builder(String processServerName) {
            this(inprocessChannel(processServerName));
        }

        private static ManagedChannel inprocessChannel(String serverName) {
            var result = InProcessChannelBuilder.forName(serverName)
                    .directExecutor()
                    .build();
            return result;
        }

        private static ManagedChannel createChannel(String host, int port) {
            var result = ManagedChannelBuilder.forAddress(host, port).build();
            return result;
        }

        /**
         * Assigns the tenant for the client connection to be built.
         *
         * <p>This method should be called only in multi-tenant applications.
         *
         * @param tenant
         *          a non-null and non-default ID of the tenant
         */
        @CanIgnoreReturnValue
        public Builder forTenant(TenantId tenant) {
            this.tenant = checkNotDefaultArg(tenant);
            return this;
        }

        /**
         * Assigns the ID of the user for performing requests on behalf of a non-logged in user.
         *
         * <p>If not set directly, the value {@code "guest"} will be used.
         *
         * @param guestUser
         *         non-null and non-default value
         */
        @CanIgnoreReturnValue
        public Builder withGuestId(UserId guestUser) {
           checkNotNull(guestUser);
           this.guestUser = checkNotDefaultArg(
                    guestUser, "Guest user ID cannot be a default value.");
           return this;
        }

        /**
         * Assigns the ID of the user for performing requests on behalf of a non-logged in user.
         *
         * <p>If not set directly, the value {@code "guest"} will be used.
         *
         * @param guestUser
         *         non-null and not an empty or a blank value
         */
        @CanIgnoreReturnValue
        public Builder withGuestId(String guestUser) {
            checkNotEmptyOrBlank(guestUser, "Guest user ID cannot be empty or blank.");
            return withGuestId(user(guestUser));
        }

        /**
         * Sets the timeout for the {@linkplain Client#close() shutdown operation} of the client.
         *
         * <p>If not specified directly, {@link Client#DEFAULT_SHUTDOWN_TIMEOUT} will be used.
         *
         * @deprecated Use {@link #shutdownTimeout(long, TimeUnit)} instead.
         */
        @Deprecated
        @CanIgnoreReturnValue
        public Builder shutdownTimout(long timeout, TimeUnit timeUnit) {
            checkNotNull(timeUnit);
            this.shutdownTimeout = Timeout.of(timeout, timeUnit);
            return this;
        }

        /**
         * Sets the timeout for the {@linkplain Client#close() shutdown operation} of the client.
         *
         * <p>If not specified directly, {@link Client#DEFAULT_SHUTDOWN_TIMEOUT} will be used.
         */
        @CanIgnoreReturnValue
        public Builder shutdownTimeout(long timeout, TimeUnit timeUnit) {
            checkNotNull(timeUnit);
            this.shutdownTimeout = Timeout.of(timeout, timeUnit);
            return this;
        }

        /**
         * Assigns a default handler for streaming errors for the asynchronous requests
         * initiated by the client.
         */
        @CanIgnoreReturnValue
        public Builder onStreamingError(ErrorHandler handler) {
            this.streamingErrorHandler = checkNotNull(handler);
            return this;
        }

        /**
         * Assigns a default handler for the error case that the server returns in response
         * to a message posted by the client, such as {@code INVALID_REQUEST} for an invalid
         * message.
         */
        @CanIgnoreReturnValue
        public Builder onServerError(ServerErrorHandler handler) {
            checkNotNull(handler);
            this.serverErrorHandler = handler;
            return this;
        }

        /**
         * Creates a new instance of the client.
         */
        public Client build() {
            if (channel == null) {
                checkNotNull(host, "Either channel or host/port must be specified.");
                channel = createChannel(host, port);
            }
            if (shutdownTimeout == null) {
                shutdownTimeout = DEFAULT_SHUTDOWN_TIMEOUT;
            }
            return new Client(this);
        }

        @VisibleForTesting
        @Nullable String host() {
            return host;
        }

        @VisibleForTesting
        int port() {
            return port;
        }

        @VisibleForTesting
        @Nullable ManagedChannel channel() {
            return channel;
        }
    }
}
