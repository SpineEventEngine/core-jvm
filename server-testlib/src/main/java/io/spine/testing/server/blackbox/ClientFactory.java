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

package io.spine.testing.server.blackbox;

import io.spine.base.Identifier;
import io.spine.client.Client;
import io.spine.core.TenantId;
import io.spine.server.Application;
import io.spine.server.BoundedContext;
import io.spine.server.Closeable;
import io.spine.server.CommandService;
import io.spine.server.GrpcContainer;
import io.spine.server.QueryService;
import io.spine.server.SubscriptionService;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkState;
import static io.spine.type.ProtoTexts.shortDebugString;
import static io.spine.util.Exceptions.illegalStateWithCauseOf;
import static java.util.Objects.isNull;

/**
 * Creates {@code Client} instances for the only Bounded Context of the passed application.
 *
 * <p>As {@code Client} requires a gRPC server to be connected to, the factory assembles
 * and starts a {@link GrpcContainer}. The server and the created clients are working in-process.
 */
final class ClientFactory implements Closeable {

    /**
     * The application whose services serve the created {@code Client} instances.
     */
    private final Application application;

    /**
     * The context to which this factory creates {@code Client} instances.
     */
    private final BoundedContext context;

    /**
     * Instances created so far.
     *
     * <p>Ultimately, they need to be closed.
     *
     * @see #close()
     */
    private final List<Client> clients;

    private @MonotonicNonNull GrpcContainer grpcContainer;
    private @MonotonicNonNull String serverName;

    /**
     * Creates a factory producing {@code Client}s that would send their requests
     * to the only context of the specified application.
     */
    ClientFactory(Application application) {
        this.application = application;
        this.context = application.contexts().get(0);
        this.clients = new ArrayList<>();
    }

    /**
     * Opens a new {@code Client} to the Bounded Context.
     *
     * <p>It is a caller responsibility to match the tenancy setting of the Bounded Context.
     * So if the context is single-tenant, this method expects {@code null}.
     *
     * <p>For the multi-tenant Context, the passed {@code tenant} value must be non-{@code null}.
     */
    Client create(@Nullable TenantId tenant) {
        ensureTenantMatch(tenant);
        var serverName = ensureServer();
        var builder = Client.inProcess(serverName);
        if(tenant != null) {
            builder.forTenant(tenant);
        }
        var client = builder.build();
        clients.add(client);
        return client;
    }

    private void ensureTenantMatch(@Nullable TenantId tenant) {
        if (tenant == null) {
            checkState(!context.isMultiTenant(),
                       "The context `%s` is multi-tenant, " +
                               "it is not possible to create a `Client` with no tenant ID passed.",
                       context.name().value());
        } else {
            checkState(context.isMultiTenant(),
                       "The context `%s` is single-tenant, " +
                               "it is not possible to create a `Client` for the tenant ID `%s`.",
                       context.name().value(), shortDebugString(tenant));
        }
    }

    /**
     * Runs the gRPC server in case it has not been run previously.
     *
     * @return the name of the server
     */
    private String ensureServer() {
        checkOpen();
        if (isNull(serverName)) {
            initServer();
        }
        return serverName;
    }

    private void initServer() {
        var commandService = CommandService.of(application);
        var queryService = QueryService.of(application);
        var subscriptionService = SubscriptionService.of(application);

        var serverName = "BlackBox-Server-" + Identifier.newUuid();
        var grpcContainer = GrpcContainer
                .inProcess(serverName)
                .addService(commandService)
                .addService(queryService)
                .addService(subscriptionService)
                .build();

        try {
            grpcContainer.start();
        } catch (IOException e) {
            throw illegalStateWithCauseOf(e);
        }

        this.serverName = serverName;
        this.grpcContainer = grpcContainer;
    }

    /**
     * Closes all created clients and shuts down the associated {@code GrpcContainer}.
     */
    @Override
    public void close() {
        if (!isOpen() || isNull(grpcContainer)) {
            return;
        }
        grpcContainer.shutdownNowAndWait();
        clients.forEach(Client::close);
        clients.clear();
    }

    @Override
    public boolean isOpen() {
        return isNull(grpcContainer) || !grpcContainer.isShutdown();
    }
}
