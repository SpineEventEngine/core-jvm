/*
 * Copyright 2026, TeamDev. All rights reserved.
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
import java.net.ServerSocket;
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
        var context = BoundedContext.singleTenant("Tennis")
                .add(new GameRepository());
        // Bind to a free ephemeral port chosen by the OS, rather than a fixed or randomly
        // guessed one, so the test does not intermittently fail to bind an already-used
        // port (notably under Windows).
        var port = freePort();
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

    /**
     * Obtains a currently free port from the operating system's ephemeral range.
     *
     * <p>Opening a {@link ServerSocket} on port {@code 0} lets the OS assign a port that is free
     * at that moment; the socket is closed immediately, so the port is available for the gRPC
     * server to bind. Unlike a fixed or randomly guessed port, this never selects a port already
     * in use — the cause of the intermittent bind failures on CI.
     */
    private static int freePort() throws IOException {
        try (var socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
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
