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

package io.spine.client;

import com.google.common.testing.NullPointerTester;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.spine.core.TenantId;
import io.spine.core.UserId;
import io.spine.testing.TestValues;
import io.spine.testing.core.given.GivenTenantId;
import io.spine.testing.core.given.GivenUserId;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;
import static io.spine.client.Client.connectTo;
import static io.spine.client.Client.usingChannel;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("`ClientBuilder` should")
class ClientBuilderTest {

    private static final String host = "localhost";
    private int port;
    private Client.Builder builder;
    private @Nullable Client client;

    @BeforeEach
    void createBuilder() {
        port = TestValues.random(64000, 65000);
        builder = connectTo(host, port);
    }

    @AfterEach
    void shutdownClient() {
        if (client != null) {
            client.shutdown();
        }
    }

    @Test
    @DisplayName("reject nulls in factory methods of `Client`")
    void nullCheck() {
        new NullPointerTester()
                .testAllPublicStaticMethods(Client.class);
    }

    @Nested
    @DisplayName("be created with host/port combination")
    class HostPort {

        @Test
        @DisplayName("creating a `ManagedChannel` by these values ")
        void creating() {
            assertThat(builder.host())
                    .isEqualTo(host);
            assertThat(builder.port())
                    .isEqualTo(port);
            assertThat(builder.channel())
                    .isNull();

            client = builder.build();
            assertThat(client.channel())
                    .isNotNull();
        }

        @Test
        @DisplayName("rejecting empty or blank host name")
        void arguments() {
            assertRejects("");
            assertRejects(" ");
            assertRejects("  ");
        }

        private void assertRejects(String illegalHostName) {
            assertThrows(IllegalArgumentException.class,
                         () -> connectTo(illegalHostName, 404));
        }
    }

    @Nested
    @DisplayName("be created using `ManagedChannel")
    class Channel {

        private ManagedChannel channel;

        @BeforeEach
        void createChannel() {
            channel = ManagedChannelBuilder
                    .forAddress(host, 64000)
                    .build();
        }

        @AfterEach
        void shutdownChannel() throws InterruptedException {
            channel.shutdownNow();
            channel.awaitTermination(200, TimeUnit.MILLISECONDS);
        }

        @Test
        @DisplayName("passing it to the created `Client`")
        void value() {
            var builder = usingChannel(channel);

            assertThat(builder.host())
                    .isNull();
            assertThat(builder.port())
                    .isEqualTo(0);
            assertThat(builder.channel())
                    .isEqualTo(channel);

            client = builder.build();
            assertThat(client.channel())
                    .isEqualTo(channel);
        }
    }

    @Nested
    @DisplayName("configure shutdown timeout")
    class ShutdownTimeout {

        @Test
        @DisplayName("via value and unit")
        void valueAndUnit() {
            var value = 100;
            var unit = TimeUnit.MILLISECONDS;
            builder.shutdownTimeout(value, unit);

            client = builder.build();
            assertThat(client.shutdownTimeout())
                    .isEqualTo(Timeout.of(value, unit));
        }

        @Test
        @DisplayName("supply default value if not specified")
        void defaultValue() {
            client = builder.build();

            assertThat(client.shutdownTimeout())
                    .isEqualTo(Client.DEFAULT_SHUTDOWN_TIMEOUT);
        }
    }

    @Nested
    @DisplayName("allow setting tenant")
    class Tenant {

        @Test
        @DisplayName("assuming single-tenant context if not set")
        void singleTenant() {
            client = builder.build();
            assertThat(client.tenant()).isEmpty();
        }

        @Test
        @DisplayName("which is non-null and not default")
        void correctValue() {
            var expected = GivenTenantId.generate();
            client = builder.forTenant(expected)
                            .build();
            assertThat(client.tenant()).hasValue(expected);
        }

        @Test
        @DisplayName("rejecting `null` value")
        void nullValue() {
            assertThrows(NullPointerException.class,
                         () -> builder.forTenant(null));
        }

        @Test
        @DisplayName("rejecting default value")
        void defaultValue() {
            assertThrows(IllegalArgumentException.class,
                         () -> builder.forTenant(TenantId.getDefaultInstance()));
        }
    }

    @Nested
    @DisplayName("allow setting guest user ID")
    class Guest {

        @Test
        @DisplayName("which is non-null and not default")
        void correctValue() {
            var expected = GivenUserId.generated();
            client = builder.withGuestId(expected)
                            .build();

            assertThat(client.asGuest()
                             .user()).isEqualTo(expected);
        }

        @Test
        @DisplayName("which is not empty or a blank string")
        void correctStringValue() {
            var expected = GivenUserId.generated();

            client = builder.withGuestId(expected.getValue())
                            .build();

            assertThat(client.asGuest()
                             .user()).isEqualTo(expected);
        }

        @Test
        @DisplayName("use default value if not set directly")
        void defaultValue() {
            client = builder.build();

            assertThat(client.asGuest()
                             .user())
                    .isEqualTo(Client.DEFAULT_GUEST_ID);
        }

        @Test
        @DisplayName("rejecting illegal arguments")
        void illegalArguments() {
            assertThrows(NullPointerException.class,
                         () -> builder.withGuestId((UserId) null));
            assertThrows(IllegalArgumentException.class,
                         () -> builder.withGuestId(UserId.getDefaultInstance()));

            assertThrows(NullPointerException.class,
                         () -> builder.withGuestId((String) null));
            assertThrows(IllegalArgumentException.class,
                         () -> builder.withGuestId(""));
            assertThrows(IllegalArgumentException.class,
                         () -> builder.withGuestId(" "));
        }
    }
}
