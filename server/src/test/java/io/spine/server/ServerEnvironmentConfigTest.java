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

import com.google.common.testing.NullPointerTester;
import io.spine.environment.DefaultMode;
import io.spine.environment.Environment;
import io.spine.environment.EnvironmentType;
import io.spine.environment.Tests;
import io.spine.server.delivery.Delivery;
import io.spine.server.delivery.UniformAcrossAllShards;
import io.spine.server.given.environment.Local;
import io.spine.server.storage.StorageFactory;
import io.spine.server.storage.memory.InMemoryStorageFactory;
import io.spine.server.storage.system.SystemAwareStorageFactory;
import io.spine.server.storage.system.given.MemoizingStorageFactory;
import io.spine.server.trace.TracerFactory;
import io.spine.server.trace.given.MemoizingTracerFactory;
import io.spine.server.transport.ChannelId;
import io.spine.server.transport.Publisher;
import io.spine.server.transport.Subscriber;
import io.spine.server.transport.TransportFactory;
import io.spine.server.transport.memory.InMemoryTransportFactory;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.server.ServerEnvironment.when;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests configuration of “features” of {@link ServerEnvironment}.
 *
 * @see ServerEnvironmentTest
 */
@DisplayName("`ServerEnvironment` should allow configuration of")
class ServerEnvironmentConfigTest {

    private static final Environment environment = Environment.instance();
    private static final ServerEnvironment serverEnvironment = ServerEnvironment.instance();

    @AfterEach
    void resetAndBackToTests() {
        serverEnvironment.reset();
        environment.reset();
        environment.setTo(Tests.class);
        Local.disable();
    }

    @Test
    @DisplayName("its parts rejecting `null` arguments")
    void nullCheck() {
        var configurator = when(Tests.class);
        new NullPointerTester().testAllPublicInstanceMethods(configurator);
    }

    @Nested
    @DisplayName("`TransportFactory` for")
    class OfTransportFactory {

        private void assertValue(TransportFactory factory) {
            assertThat(serverEnvironment.transportFactory())
                    .isSameInstanceAs(factory);
        }

        @Nested
        @DisplayName("`Production`")
        class ForProd {

            @BeforeEach
            void turnToProduction() {
                environment.setTo(DefaultMode.class);
            }

            @Test
            @DisplayName("throw an `IllegalStateException` if not configured")
            void throwsIfNotConfigured() {
                assertThrows(IllegalStateException.class, serverEnvironment::transportFactory);
            }
            @Test
            @DisplayName("return configured instance in Production")
            void productionValue() {
                TransportFactory factory = new StubTransportFactory();
                when(DefaultMode.class).use(factory);
                assertValue(factory);
            }
        }

        @Nested
        @DisplayName("`Tests`")
        class ForTests {

            @Test
            @DisplayName("returning explicitly set value")
            void setExplicitly() {
                TransportFactory factory = new StubTransportFactory();
                when(Tests.class).use(factory);
                assertValue(factory);
            }
            @Test
            @DisplayName("returning an `InMemoryTransportFactory` when not set")
            void notSet() {
                environment.setTo(Tests.class);
                assertThat(serverEnvironment.transportFactory())
                        .isInstanceOf(InMemoryTransportFactory.class);
            }
        }

        @Nested
        @DisplayName("custom environment")
        class ForCustom {

            @BeforeEach
            void setCustom() {
                environment.setTo(Local.class);
                Local.enable();
            }

            @Test
            @DisplayName("throwing an `IllegalStateException` if not set")
            void illegalState() {
                assertThrows(IllegalStateException.class, serverEnvironment::transportFactory);
            }

            @Test
            @DisplayName("returning a configured instance")
            void ok() {
                var factory = InMemoryTransportFactory.newInstance();
                when(Local.class).use(factory);
                assertValue(factory);
            }
        }

        @Test
        @DisplayName("via function")
        void viaFn() {
            TransportFactory factory = new StubTransportFactory();
            var fn = new ReturnValue<>(factory);

            when(Tests.class).useTransportFactory(fn);

            assertValue(factory);
            assertThat(fn.typePassed())
                    .isEqualTo(Tests.class);
        }
    }

    @Nested
    @DisplayName("`Delivery`")
    class OfDelivery {

        private Class<? extends EnvironmentType<?>> currentType;
        private Delivery currentDelivery;

        private void assertValue(Delivery delivery) {
            assertThat(serverEnvironment.delivery())
                    .isSameInstanceAs(delivery);
        }

        @BeforeEach
        void rememberCurrentDelivery() {
            currentType = environment.type();
            currentDelivery = serverEnvironment.delivery();
        }

        @AfterEach
        void restoreDelivery() {
            when(currentType).use(currentDelivery);
        }

        @Test
        @DisplayName("to default back to `Local` if no delivery is set")
        void defaultsToLocal() {
            var delivery = serverEnvironment.delivery();
            assertThat(delivery).isNotNull();
        }

        @Test
        @DisplayName("to a custom mechanism")
        void customValue() {
            var customDelivery = customDelivery();
            when(currentType).use(customDelivery);

            assertValue(customDelivery);
        }

        @Test
        @DisplayName("via a function")
        void viaFn() {
            var valueFromFunction = customDelivery();
            var fn = new ReturnValue<>(valueFromFunction);
            when(currentType).useDelivery(fn);

            assertValue(valueFromFunction);
            assertThat(fn.typePassed())
                    .isEqualTo(currentType);
        }

        private Delivery customDelivery() {
            return Delivery.newBuilder()
                    .setStrategy(UniformAcrossAllShards.forNumber(42))
                    .build();
        }
    }

    /**
     * Test fixture implementing {@link ServerEnvironment.Fn} which remembers a type
     * passed to {@link #apply(Class)} and returns the configured value.
     *
     * @param <R>
     *         the type of the configuration parameter
     */
    static final class ReturnValue<R> implements ServerEnvironment.Fn<R> {

        private @Nullable Class<? extends EnvironmentType<?>> typePassed;

        private final R value;

        ReturnValue(R value) {
            this.value = value;
        }

        @Override
        public R apply(Class<? extends EnvironmentType<?>> type) {
            typePassed = type;
            return value;
        }

        @Nullable Class<? extends EnvironmentType<?>> typePassed() {
            return typePassed;
        }
    }

    @Nested
    @DisplayName("`TracerFactory`")
    class OfTracerFactory {

        @Test
        @DisplayName("returning an instance for the production environment")
        void forProduction() {
            environment.setTo(DefaultMode.class);

            TracerFactory factory = new MemoizingTracerFactory();
            when(DefaultMode.class).use(factory);

            assertTracer(factory);
        }

        @Test
        @DisplayName("for the testing environment")
        void forTesting() {
            TracerFactory factory = new MemoizingTracerFactory();
            when(Tests.class).use(factory);

            assertTracer(factory);
        }

        @Test
        @DisplayName("for a custom environment")
        void forCustom() {
            environment.setTo(Local.class);

            TracerFactory factory = new MemoizingTracerFactory();
            when(Local.class).use(factory);

            assertTracer(factory);
        }

        private static void assertTracer(TracerFactory expected) {
            assertThat(serverEnvironment.tracing())
                  .hasValue(expected);
        }

        @Test
        @DisplayName("for a custom environment, returning empty if it's disabled")
        void forCustomEmpty() {
            Local.disable();

            TracerFactory factory = new MemoizingTracerFactory();
            when(Local.class).use(factory);

            assertThat(serverEnvironment.tracing())
                  .isEmpty();
        }

        @Test
        @DisplayName("via a function")
        void viaFn() {
            TracerFactory factory = new MemoizingTracerFactory();
            var fn = new ReturnValue<>(factory);

            when(Tests.class).useTracerFactory(fn);
            assertTracer(factory);
            assertThat(fn.typePassed())
                    .isEqualTo(Tests.class);
        }
    }

    @Nested
    @DisplayName("`StorageFactory` for")
    class OfStorageFactory {

        private SystemAwareStorageFactory systemAwareFactory() {
            return (SystemAwareStorageFactory) serverEnvironment.storageFactory();
        }

        private void assertDelegateIs(StorageFactory factory) {
            var delegate = systemAwareFactory().delegate();
            assertThat(delegate)
                    .isEqualTo(factory);
        }

        @Nested
        @DisplayName("`Production`")
        class ForProduction {

            @BeforeEach
            void turnToProduction() {
                environment.setTo(DefaultMode.class);
            }

            @Test
            @DisplayName("throwing an `IllegalStateException` if not configured")
            void throwsIfNotConfigured() {
                assertThrows(IllegalStateException.class, serverEnvironment::storageFactory);
            }

            @Test
            @DisplayName("return configured `StorageFactory`")
            void productionFactory() {
                StorageFactory factory = InMemoryStorageFactory.newInstance();
                when(DefaultMode.class).use(factory);
                assertDelegateIs(factory);
            }

            @Test
            @DisplayName("return `InMemoryStorageFactory` under Tests")
            void testsFactory() {
                environment.setTo(Tests.class);

                var factory = serverEnvironment.storageFactory();
                assertThat(factory)
                        .isInstanceOf(SystemAwareStorageFactory.class);
                var delegate = systemAwareFactory().delegate();
                assertThat(delegate)
                        .isInstanceOf(InMemoryStorageFactory.class);
            }
        }

        @Nested
        @DisplayName("`Tests`")
        class ForTests {

            @Test
            @DisplayName("wrapping `SystemAwareStorageFactory` over the passed instance")
            void getSet() {
                StorageFactory factory = new MemoizingStorageFactory();
                when(Tests.class).use(factory);
                assertDelegateIs(factory);
            }
        }

        @Nested
        @DisplayName("a custom environment")
        class ForCustom {

            @BeforeEach
            void setToLocal() {
                environment.setTo(Local.class);
                Local.enable();
            }

            @Test
            @DisplayName("throwing an `IllegalStateException` if not set")
            void illegalState() {
                assertThrows(IllegalStateException.class, serverEnvironment::storageFactory);
            }

            @Test
            @DisplayName("returning a configured wrapped instance")
            void returnConfiguredStorageFactory() {
                var inMemory = InMemoryStorageFactory.newInstance();
                when(Local.class).use(inMemory);

                assertDelegateIs(inMemory);
            }
        }

        @Test
        @DisplayName("via a function")
        void viaFn() {
            StorageFactory factory = new MemoizingStorageFactory();
            var fn = new ReturnValue<>(factory);

            when(Tests.class).useStorageFactory(fn);

            assertDelegateIs(factory);
            assertThat(fn.typePassed())
                    .isEqualTo(Tests.class);
        }
    }

    /**
     * Stub implementation of {@code TransportFactory} which delegates all the calls
     * to {@code InMemoryTransportFactory}.
     */
    static class StubTransportFactory implements TransportFactory {

        private final TransportFactory delegate = InMemoryTransportFactory.newInstance();

        @Override
        public Publisher createPublisher(ChannelId id) {
            return delegate.createPublisher(id);
        }

        @Override
        public Subscriber createSubscriber(ChannelId id) {
            return delegate.createSubscriber(id);
        }

        @Override
        public boolean isOpen() {
            return delegate.isOpen();
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}
