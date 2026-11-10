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

import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.spine.server.Server;
import io.spine.test.client.billing.PaymentReceived;
import io.spine.test.client.tasks.event.CTaskCreated;
import io.spine.testing.SlowTest;
import io.spine.testing.logging.mute.MuteLogging;
import io.spine.testing.server.TestApplications;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;
import static io.grpc.ManagedChannelBuilder.forAddress;
import static io.grpc.Status.CANCELLED;
import static io.spine.client.Client.usingChannel;
import static io.spine.client.given.ClientTasksTestEnv.createCTask;
import static io.spine.server.Server.atPort;
import static io.spine.test.client.ClientTestContext.tasks;
import static io.spine.test.client.ClientTestContext.users;
import static java.time.Duration.ofSeconds;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.fail;

@SlowTest
@MuteLogging
@DisplayName("Real gRPC-based `Client` should")
class ClientEndToEndTest {

    @SuppressWarnings("DuplicateStringLiteralInspection")
    private static final String ADDRESS = "localhost";

    private Client client;
    private Server server;
    private ManagedChannel channel;

    @BeforeEach
    void startAndConnect() throws IOException {
        var application = TestApplications.newBuilder()
                .add(users())
                .add(tasks())
                .build();
        // Bind to the port `0` so that the OS assigns a free one, and connect to the port
        // the server actually got. A fixed port makes the test fail intermittently when
        // the port is taken by another process on the CI runner.
        server = atPort(0).setApplication(application)
                          .build();
        server.start();
        channel = forAddress(ADDRESS, server.getBoundPort())
                .usePlaintext()
                .build();
        client = usingChannel(channel).build();
    }

    @AfterEach
    void stopAndDisconnect() throws InterruptedException {
        try {
            client.close();
        } catch (StatusRuntimeException e) {
            if (e.getStatus().equals(CANCELLED)) {
                fail(e);
            }
        }
        server.shutdown();
        channel.shutdown();
        channel.awaitTermination(1, SECONDS);
    }

    @Test
    @DisplayName("subscribe to Event type from all contexts at once")
    void subscribeToUnknownEvent() {
        client.asGuest()
              .subscribeToEvent(PaymentReceived.class)
              .onStreamingError(Assertions::fail)
              .onConsumingError((consumer, throwable) -> fail(throwable))
              .observe(event -> {
                  // Do nothing.
              })
              .post();
    }

    @Test
    @DisplayName("post Command and subscribe to Event which is not declared explicitly")
    void subscribeToEvent() {
        var fired = new AtomicBoolean(false);
        var task = createCTask("`Task` emitting events");
        client.asGuest()
              .command(task)
              .observe(CTaskCreated.class, event -> fired.set(true))
              .post();
        sleepUninterruptibly(ofSeconds(1));
        assertThat(fired.get())
                .isTrue();
    }
}
