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
        var newDelivery = Delivery.newBuilder(ServerEnvironment.instance())
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
