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

package io.spine.server;

import io.spine.base.Identifier;
import io.spine.environment.Environment;
import io.spine.environment.EnvironmentType;
import io.spine.environment.DefaultMode;
import io.spine.environment.Tests;
import io.spine.server.delivery.Delivery;
import io.spine.server.delivery.UniformAcrossAllShards;
import io.spine.server.given.environment.Local;
import io.spine.server.storage.memory.InMemoryStorageFactory;
import io.spine.server.storage.system.given.MemoizingStorageFactory;
import io.spine.server.trace.given.MemoizingTracerFactory;
import io.spine.server.transport.memory.InMemoryTransportFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests non-configuration aspects of {@link ServerEnvironment}.
 *
 * @see ServerEnvironmentConfigTest
 */
@DisplayName("`ServerEnvironment` should")
class ServerEnvironmentTest {

    @Test
    @DisplayName("allow to customize delivery mechanism")
    void allowToCustomizeDeliveryStrategy() {
        var storageFactory = InMemoryStorageFactory.newInstance();
        var nodeId = NodeId.newBuilder()
                .setValue(Identifier.newUuid())
                .build();
        var newDelivery = Delivery.newBuilder(nodeId, storageFactory)
                .setStrategy(UniformAcrossAllShards.forNumber(42))
                .build();
        var environment = ServerEnvironment.newBuilder()
                .under(Tests.class, env -> env.use(newDelivery))
                .build();

        assertEquals(newDelivery, environment.delivery());
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

        @Test
        @DisplayName("close the production transport, tracer and storage factories")
        void productionCloses() {
            testClosesEnv(DefaultMode.class);
        }

        @Test
        @DisplayName("close the testing transport, tracer and storage factories")
        void testCloses() {
            testClosesEnv(Tests.class);
        }

        @Test
        @DisplayName("close the custom env transport, tracer and storage factories")
        void customEnvCloses() {
            testClosesEnv(Local.class);
        }

        private void testClosesEnv(Class<? extends EnvironmentType<?>> envType) {
            var environment = ServerEnvironment.newBuilder()
                    .under(envType, env -> env.use(storageFactory)
                                              .use(transportFactory)
                                              .use(tracerFactory))
                    .build();

            environment.close();

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
        var environment = ServerEnvironment.newBuilder()
                .build();

        assertThat(environment.type())
                .isSameInstanceAs(Environment.instance()
                                             .type());
    }
}
