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

import io.spine.environment.Environment;
import io.spine.environment.EnvironmentType;
import io.spine.environment.DefaultMode;
import io.spine.environment.Tests;
import io.spine.server.delivery.Delivery;
import io.spine.server.delivery.UniformAcrossAllShards;
import io.spine.server.given.environment.Local;
import io.spine.server.storage.system.given.MemoizingStorageFactory;
import io.spine.server.trace.given.MemoizingTracerFactory;
import io.spine.server.transport.memory.InMemoryTransportFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.testing.Assertions.assertHasPrivateParameterlessCtor;
import static io.spine.testing.DisplayNames.HAVE_PARAMETERLESS_CTOR;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests non-configuration aspects of {@link ServerEnvironment}.
 *
 * @see ServerEnvironmentConfigTest
 */
@DisplayName("`ServerEnvironment` should")
class ServerEnvironmentTest {

    private static final ServerEnvironment serverEnvironment = ServerEnvironment.instance();

    @Test
    @DisplayName(HAVE_PARAMETERLESS_CTOR)
    void haveUtilityConstructor() {
        assertHasPrivateParameterlessCtor(ServerEnvironment.class);
    }

    @Test
    @DisplayName("allow to customize delivery mechanism")
    void allowToCustomizeDeliveryStrategy() {
        var newDelivery = Delivery.newBuilder()
                .setStrategy(UniformAcrossAllShards.forNumber(42))
                .build();
        var environment = serverEnvironment;
        var defaultValue = environment.delivery();
        ServerEnvironment.under(Tests.class)
                         .use(newDelivery);
        assertEquals(newDelivery, environment.delivery());

        // Restore the default value.
        ServerEnvironment.under(Tests.class)
                         .use(defaultValue);
    }

    @Nested
    @DisplayName("while closing resources")
    class TestClosesResources {

        private InMemoryTransportFactory transportFactory;
        private MemoizingStorageFactory storageFactory;
        private MemoizingTracerFactory tracerFactory;

        @BeforeEach
        void setup() {
            transportFactory = InMemoryTransportFactory.newInstance();
            storageFactory = new MemoizingStorageFactory();
            tracerFactory = new MemoizingTracerFactory();
        }

        @AfterEach
        void resetEnvironment() {
            serverEnvironment.reset();
        }

        @Test
        @DisplayName("close the production transport, tracer and storage factories")
        void productionCloses() throws Exception {
            testClosesEnv(DefaultMode.class);
        }

        @Test
        @DisplayName("close the testing transport, tracer and storage factories")
        void testCloses() throws Exception {
            testClosesEnv(Tests.class);
        }

        @Test
        @DisplayName("close the custom env transport, tracer and storage factories")
        void customEnvCloses() throws Exception {
            testClosesEnv(Local.class);
        }

        private void testClosesEnv(Class<? extends EnvironmentType<?>> envType) throws Exception {
            ServerEnvironment.under(envType)
                             .use(storageFactory)
                             .use(transportFactory)
                             .use(tracerFactory);

            ServerEnvironment.instance()
                             .close();

            assertThat(transportFactory.isOpen())
                    .isFalse();
            assertThat(storageFactory.isClosed())
                    .isTrue();
            assertThat(tracerFactory.closed())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("obtain the current environment type")
    void gettingType() {
        assertThat(serverEnvironment.type())
                .isSameInstanceAs(Environment.instance()
                                             .type());
    }

}
