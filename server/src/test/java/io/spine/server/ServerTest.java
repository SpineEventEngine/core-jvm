/*
 * Copyright 2024, TeamDev. All rights reserved.
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

package io.spine.server;

import com.google.protobuf.Empty;
import io.grpc.ManagedChannelBuilder;
import io.spine.server.given.service.StatusCheckService;
import io.spine.test.server.StatusCheckGrpc;
import io.spine.testing.logging.mute.MuteLogging;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static io.spine.test.client.ClientTestContext.tasks;
import static io.spine.test.client.ClientTestContext.users;

@DisplayName("`Server` should")
class ServerTest {

    @SuppressWarnings("DuplicateStringLiteralInspection")
    private static final String ADDRESS = "localhost";
    private static final int PORT = 4242;

    @Test
    @MuteLogging
    @DisplayName("allow registering custom gRPC services")
    void customServices() throws IOException {
        var server = Server.atPort(PORT)
                           .add(users())
                           .add(tasks())
                           .include(new StatusCheckService())
                           .build();
        server.start();
        var ch = ManagedChannelBuilder.forAddress(ADDRESS, PORT)
                                      .usePlaintext()
                                      .build();
        var client = StatusCheckGrpc.newBlockingStub(ch);
        var response = client.check(Empty.getDefaultInstance());
        assertThat(response)
                .isNotEqualToDefaultInstance();
        server.shutdown();
        ch.shutdownNow();
    }

    @Test
    @DisplayName("provide `CommandService`, `QueryService`, and `SubscriptionService`")
    void cAndQ() {
        var server = Server.atPort(PORT)
                           .add(users())
                           .add(tasks())
                           .build();
        assertThat(server.subscriptionService()).isNotNull();
        assertThat(server.queryService()).isNotNull();
        assertThat(server.commandService()).isNotNull();
    }
}
