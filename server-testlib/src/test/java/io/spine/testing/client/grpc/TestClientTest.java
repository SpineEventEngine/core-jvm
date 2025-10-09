/*
 * Copyright 2025, TeamDev. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
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

package io.spine.testing.client.grpc;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.spine.core.Ack;
import io.spine.core.UserId;
import io.spine.server.BoundedContext;
import io.spine.server.Server;
import io.spine.testing.client.grpc.command.Ping;
import io.spine.testing.client.grpc.given.GameRepository;
import io.spine.testing.logging.mute.MuteLogging;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Optional;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static io.spine.client.Client.DEFAULT_CLIENT_SERVICE_PORT;
import static io.spine.core.Responses.statusOk;
import static io.spine.testing.TestValues.random;
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
        var context = BoundedContext.singleTenant("Tennis")
                .add(new GameRepository());
        // Select a port randomly to avoid the intermittent hanging port under Windows.
        var port = random(DEFAULT_CLIENT_SERVICE_PORT, DEFAULT_CLIENT_SERVICE_PORT + 1000);
        server = Server
                .atPort(port)
                .add(context)
                .build();
        server.start();
        var userId = UserId.newBuilder()
                .setValue(TestClientTest.class.getSimpleName())
                .build();
        client = new TestClient(userId, "localhost", port);
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
