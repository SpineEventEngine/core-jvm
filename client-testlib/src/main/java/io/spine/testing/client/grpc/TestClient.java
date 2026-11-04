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

package io.spine.testing.client.grpc;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.spine.base.CommandMessage;
import io.spine.base.EntityState;
import io.spine.client.QueryResponse;
import io.spine.client.grpc.CommandServiceGrpc;
import io.spine.client.grpc.CommandServiceGrpc.CommandServiceBlockingStub;
import io.spine.client.grpc.QueryServiceGrpc;
import io.spine.client.grpc.QueryServiceGrpc.QueryServiceBlockingStub;
import io.spine.core.Ack;
import io.spine.core.ErrorCase;
import io.spine.core.UserId;
import io.spine.grpc.StreamObservers;
import io.spine.logging.WithLogging;
import io.spine.testing.client.TestActorRequestFactory;
import io.spine.type.TypeName;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.util.Exceptions.newIllegalStateException;
import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * {@link TestClient} connects to the server using gRPC and allows sending commands and
 * querying its entities.
 */
public class TestClient implements WithLogging {

    private static final String RPC_FAILED = "The command could not be posted.";
    private final TestActorRequestFactory requestFactory;
    private final ManagedChannel channel;
    private final CommandServiceBlockingStub commandClient;
    private final QueryServiceBlockingStub queryClient;

    /**
     * Constructs the client connecting to the server at {@code host:port}.
     */
    public TestClient(UserId userId, String host, int port) {
        checkNotNull(userId);
        checkNotNull(host);
        this.requestFactory = new TestActorRequestFactory(userId);
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        this.commandClient = CommandServiceGrpc.newBlockingStub(channel);
        this.queryClient = QueryServiceGrpc.newBlockingStub(channel);
    }

    /**
     * Creates a command for the passed message and sends it to the server.
     */
    @CanIgnoreReturnValue
    public Optional<Ack> post(CommandMessage domainCommand) {
        var command = requestFactory.command()
                                        .create(domainCommand);
        var commandType = TypeName.of(domainCommand);
        Ack result = null;
        try {
            logger().atDebug().log(() -> format("Sending command: `%s` ...", commandType));
            var ack = commandClient.post(command);
            logger().atDebug().log(() -> format("Ack: `%s`.", ack));
            result = ack;
        } catch (RuntimeException e) {
            logger().atWarning().withCause(e)
                    .log(() -> RPC_FAILED);
        }
        return Optional.ofNullable(result);
    }

    /**
     * Queries all entities of the provided type.
     *
     * @param messageType
     *         an entity type to query
     * @return query response with the state of entities obtained from the server
     */
    public QueryResponse queryAll(Class<? extends EntityState<?>> messageType) {
        var query = requestFactory.query().all(messageType);
        try {
            var result = queryClient.read(query);
            return result;
        } catch (StatusRuntimeException e) {
            var message = e.getMessage();
            logger().atError().withCause(e).log(() -> format(
                    "Error occurred when executing query: %s.",
                         StreamObservers.errorCaseOf(e)
                                        .map(ErrorCase::name)
                                        .orElse(message)));
            throw newIllegalStateException(e, message);
        }
    }

    /**
     * Shuts down the client, waiting 5 seconds for preexisting calls to continue.
     *
     * @throws InterruptedException
     *         if waiting is interrupted.
     */
    public void shutdown() throws InterruptedException {
        if (!channel.isShutdown()) {
            channel.shutdown()
                   .awaitTermination(5, SECONDS);
        }
    }

    /**
     * Verifies if the client is shut down.
     */
    public boolean isShutdown() {
        return channel.isShutdown();
    }
}
