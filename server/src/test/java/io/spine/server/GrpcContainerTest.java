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

import io.grpc.ServerServiceDefinition;
import io.spine.server.given.transport.TestGrpcServer;
import io.spine.testing.logging.mute.MuteLogging;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.server.given.service.GivenCommandService.noOpCommandService;
import static io.spine.testing.TestValues.randomString;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@DisplayName("`GrpcContainer` should")
class GrpcContainerTest {

    private GrpcContainer grpcContainer;

    @BeforeEach
    void setUp() {
        grpcContainer = GrpcContainer.inProcess(randomString()).build();
        grpcContainer.injectServer(new TestGrpcServer());
    }

    @SuppressWarnings("MagicNumber")
    @Test
    @MuteLogging
    @DisplayName("add and remove parameters from builder")
    void setParamsInBuilder() {
        var port = 60;
        var builder = GrpcContainer.atPort(port);

        assertTrue(builder.hasPort());
        assertThat(builder.getPort()).isEqualTo(port);

        var count = 3;
        for (var i = 0; i < count; i++) {
            var service = noOpCommandService();
            builder.addService(service);
        }

        var services = builder.services();

        // Perform removal and check that the return value is builder itself.
        assertEquals(builder, builder.removeService(services.iterator().next()));

        Set<ServerServiceDefinition> serviceSet = builder.services();
        assertThat(serviceSet).hasSize(count - 1);

        var container = builder.build();
        assertNotNull(container);
    }

    @Test
    @DisplayName("start server")
    void startServer() throws IOException {
        grpcContainer.start();

        assertThat(grpcContainer.grpcServer())
                .isNotNull();
    }

    @Test
    @DisplayName("start server with the given `Executor`")
    void startServerWithExecutor() throws IOException {
        grpcContainer.start(newSingleThreadExecutor());

        assertThat(grpcContainer.grpcServer())
                .isNotNull();
    }

    @Test
    @MuteLogging
    @DisplayName("configure underlying gRPC server")
    void configureUnderlyingGrpcServer() {
        var port = 1654;
        var service = noOpCommandService();
        var container = GrpcContainer
                .atPort(port)
                .withServer((server) -> server.addService(service))
                .build();
        try {
            container.start();
            var server = container.grpcServer();
            assertThat(server)
                    .isNotNull();

            var deployedServices = server.getServices();
            assertThat(deployedServices)
                    .hasSize(1);

            var actualName = deployedServices
                    .get(0)
                    .getServiceDescriptor()
                    .getName();
            assertThat(actualName).contains(service.getClass()
                                                   .getSimpleName());
        } catch (IOException e) {
            fail(e);
        } finally {
            container.shutdown();
        }
    }

    @Test
    @DisplayName("shutdown server")
    void shutdownItself() throws IOException {
        grpcContainer.start();

        var server = grpcContainer.grpcServer();
        grpcContainer.shutdown();

        assertThat(server.isShutdown())
                .isTrue();
        assertThat(grpcContainer.grpcServer())
                .isNull();
    }

    @Test
    @DisplayName("forcefully shutdown server")
    void shutdownAndWait() throws IOException {
        grpcContainer.start();
        grpcContainer.shutdownNowAndWait();

        assertTrue(grpcContainer.isShutdown());
    }

    @Test
    @MuteLogging
    @DisplayName("stop properly upon application shutdown")
    void stopUponAppShutdown() throws IOException {
        var container = GrpcContainer.inProcess(randomString()).build();
        container.addShutdownHook();

        container.start();
        container.shutdownCallback()
                 .run();
        assertThat(container.isShutdown())
                .isTrue();
    }

    @Nested
    @DisplayName("throw `ISE` if performing")
    class NotPerformTwice {

        @Test
        @DisplayName("start if container is already started")
        void start() throws IOException {
            grpcContainer.start();
            try {
                grpcContainer.start();
            } catch (IllegalStateException expected) {
                return;
            }
            fail("Exception must be thrown.");
        }

        @Test
        @DisplayName("shutdown if container is already shutdown")
        void shutdown() throws IOException {
            grpcContainer.start();
            grpcContainer.shutdown();
            try {
                grpcContainer.shutdown();
            } catch (IllegalStateException expected) {
                return;
            }
            fail("Expected an exception.");
        }
    }

    @Nested
    @DisplayName("when service is not started, throw ISE on calling")
    class ThrowWhenNotStarted {

        @Test
        @DisplayName("`awaitTermination`")
        void onAwaitTermination() {
            assertThrows(IllegalStateException.class, () -> grpcContainer.awaitTermination());
        }

        @Test
        @DisplayName("`shutdown`")
        void onShutdown() {
            assertThrows(IllegalStateException.class, () -> grpcContainer.shutdown());
        }
    }

    @Nested
    @DisplayName("assure service is")
    class AssureServiceIs {

        @Test
        @DisplayName("shutdown")
        void shutdown() throws IOException {
            grpcContainer.start();
            grpcContainer.shutdown();

            assertTrue(grpcContainer.isShutdown());
        }

        @Test
        @DisplayName("not started")
        void notStarted() {
            assertTrue(grpcContainer.isShutdown());
        }

        @Test
        @DisplayName("not shutdown")
        void notShutdown() throws IOException {
            grpcContainer.start();

            assertFalse(grpcContainer.isShutdown());
        }
    }
}
