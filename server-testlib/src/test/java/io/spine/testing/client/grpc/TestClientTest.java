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
import io.spine.core.Ack;
import io.spine.core.UserId;
import io.spine.server.BoundedContext;
import io.spine.server.Server;
import io.spine.testing.client.grpc.command.Ping;
import io.spine.testing.client.grpc.given.GameRepository;
import io.spine.testing.logging.mute.MuteLogging;
import io.spine.testing.server.TestApplications;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Optional;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static io.spine.core.Responses.statusOk;
import static io.spine.testing.client.grpc.TableSide.LEFT;
import static io.spine.testing.client.grpc.TableSide.RIGHT;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link TestClient}.
 *
 * <p>This test suite is placed under {@code testutil-server} and not {@code testutil-client} due to
 * a dependency onto server-only components, such as {@link Server}.
 */
@MuteLogging
class TestClientTest {

    private Server server;
    private TestClient client;

    @BeforeEach
    void setUpAll() throws IOException {
        var application = TestApplications.newBuilder()
                .add(BoundedContext.singleTenant("Tennis")
                                   .add(new GameRepository()))
                .build();
        // Bind to the port `0` so that the OS assigns a free port at the time of binding.
        // Unlike a fixed or randomly guessed port, this cannot fail with "address already
        // in use" (notably under Windows).
        server = Server
                .atPort(0)
                .setApplication(application)
                .build();
        server.start();
        var userId = UserId.newBuilder()
                .setValue(TestClientTest.class.getSimpleName())
                .build();
        client = new TestClient(userId, "localhost", server.getBoundPort());
    }

    @AfterEach
    void tearDownAll() throws Exception {
        if (!client.isShutdown()) {
            client.shutdown();
        }
        server.shutdownAndWait();
    }

    @Test
    void post() {
        var optional = ping(LEFT);
        assertOk(optional);
    }

    @CanIgnoreReturnValue
    private Optional<Ack> ping(TableSide side) {
        return client.post(Ping.newBuilder()
                               .setTable(1)
                               .setSide(side)
                               .build());
    }

    @Test
    void queryAll() {
        var optional = ping(LEFT);
        assertOk(optional);

        // Query the state of the Game Process Manager, which has Timestamp as its state.
        var response = client.queryAll(Table.class);
        assertFalse(response.isEmpty());
    }

    @Test
    void shutdown() throws InterruptedException {
        // Ensure that the client is operational.
        assertThat(ping(RIGHT)).isPresent();

        assertFalse(client.isShutdown());
        client.shutdown();

        assertTrue(client.isShutdown());
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static void assertOk(Optional<Ack> optional) {
        assertTrue(optional.isPresent());
        var ack = optional.get();
        assertThat(ack.getStatus())
             .isEqualTo(statusOk());
    }
}
