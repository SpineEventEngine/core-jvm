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

import com.google.common.collect.ImmutableList;
import com.google.protobuf.Message;
import io.spine.base.Error;
import io.spine.base.EventMessage;
import io.spine.core.Command;
import io.spine.protobuf.AnyPacker;
import io.spine.server.BoundedContextBuilder;
import io.spine.test.client.ClientTestContext;
import io.spine.test.client.users.command.LogInUser;
import io.spine.test.client.users.command.UnsupportedCommand;
import io.spine.test.client.users.event.UserAccountCreated;
import io.spine.test.client.users.event.UserLoggedIn;
import io.spine.test.client.users.rejection.Rejections.UserAlreadyLoggedIn;
import io.spine.testing.core.given.GivenUserId;
import io.spine.testing.logging.mute.MuteLogging;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.extensions.proto.ProtoTruth.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MuteLogging
@DisplayName("`CommandRequest` should")
class CommandRequestTest extends AbstractClientTest {

    /** The object under the test. */
    private CommandRequest commandRequest;

    /** Registers which event consumers were called. */
    private final ConsumerCallCounter counter = new ConsumerCallCounter();

    @BeforeEach
    void createCommandRequest() {
        counter.clear();
        var cmd = LogInUser.newBuilder()
                .setUser(GivenUserId.generated())
                .build();
        commandRequest = client().asGuest()
                                 .command(cmd);
    }

    @AfterEach
    void clearCounter() {
        counter.clear();
    }

    @Override
    protected ImmutableList<BoundedContextBuilder> contexts() {
        return ImmutableList.of(ClientTestContext.users());
    }

    @Nested
    @DisplayName("allow posting without subscriptions")
    class NoSubscriptions {

        @Test
        @DisplayName("Rejecting when a subscription was made")
        void illegalUse() {
            commandRequest.observe(UserLoggedIn.class, counter::add);
            assertThrows(
                    IllegalStateException.class,
                    () -> commandRequest.postAndForget()
            );
        }

        @Test
        @DisplayName("Delivering no events")
        void noEvents() {
            assertDoesNotThrow(() -> commandRequest.postAndForget());
        }
    }

    @Nested
    @DisplayName("deliver")
    class OfDelivery {

        @Test
        @DisplayName("an event to a consumer")
        void eventConsumer() {
            commandRequest.observe(UserLoggedIn.class, counter::add)
                          .observe(UserAccountCreated.class, counter::add)
                          .post();
            assertDelivered(UserLoggedIn.class, UserAccountCreated.class);
        }

        @Test
        @DisplayName("an event and its context to a consumer")
        void eventAndContextConsumer() {
            commandRequest.observe(UserLoggedIn.class, (e, c) -> counter.add(e))
                          .observe(UserAccountCreated.class, (e, c) -> counter.add(e))
                          .post();
            assertDelivered(UserLoggedIn.class, UserAccountCreated.class);
        }

        @Test
        @DisplayName("a rejection to its consumers")
        void rejections() {
            // Post the command so that the user is logged in. We are not interested in events here.
            commandRequest.postAndForget();
            // Now post the command again, expecting the rejection.
            commandRequest.observe(UserAlreadyLoggedIn.class, counter::add)
                          .post();

            assertDelivered(UserAlreadyLoggedIn.class);
        }

        @SafeVarargs
        private void assertDelivered(Class<? extends EventMessage>... classes) {
            assertThat(counter.containsAll(classes))
                    .isTrue();
        }
    }

    @Test
    @DisplayName("suggest `postAndForget()` call if no subscriptions were made")
    void noSubscriptions() {
        assertThrows(
                IllegalStateException.class,
                () -> commandRequest.post()
        );
    }

    @Nested
    @DisplayName("support custom error handler for")
    class OfErrorHandler {

        @Nested
        @DisplayName("streaming error")
        class CustomStreamingErrorHandler {

            @Test
            @DisplayName("rejecting `null`")
            void rejectingNull() {
                assertThrows(NullPointerException.class, () -> commandRequest.onStreamingError(null));
            }
        }

        @Nested
        @DisplayName("consumer error")
        class CustomConsumerErrorHandler {

            private boolean handlerInvoked;
            private @Nullable Throwable passedThrowable;

            @BeforeEach
            void setup() {
                handlerInvoked = false;
                passedThrowable = null;
            }

            @Test
            @DisplayName("rejecting `null`")
            void rejectingNull() {
                assertThrows(NullPointerException.class, () -> commandRequest.onConsumingError(null));
            }

            @Test
            @DisplayName("invoking the handler when a consumer fails")
            void invocation() {
                ConsumerErrorHandler<EventMessage> handler = (c, th) -> {
                    handlerInvoked = true;
                    passedThrowable = th;
                };
                var exception = new RuntimeException("Consumer-generated error.");

                commandRequest.onConsumingError(handler)
                              .observe(UserLoggedIn.class, e -> {
                                  throw exception;
                              })
                              .post();

                assertThat(handlerInvoked)
                        .isTrue();
                assertThat(passedThrowable)
                        .isEqualTo(exception);
            }
        }

        @Nested
        @DisplayName("posting error")
        class CustomServerErrorHandler {

            private @Nullable Message postedMessage;
            private @Nullable Error returnedError;

            @BeforeEach
            void setup() {
                postedMessage = null;
                returnedError = null;
            }

            @Test
            @DisplayName("rejecting `null`")
            void rejectingNull() {
                assertThrows(NullPointerException.class, () -> commandRequest.onServerError(null));
            }

            @Test
            @DisplayName("invoking handler when an invalid command posted")
            void invocation() {
                ServerErrorHandler handler = (message, error) -> {
                    postedMessage = message;
                    returnedError = error;
                };

                var commandMessage = UnsupportedCommand.newBuilder()
                        .setUser(GivenUserId.generated())
                        .build();
                var request =
                        client().asGuest()
                                .command(commandMessage)
                                .onServerError(handler);
                request.postAndForget();

                assertThat(returnedError)
                        .isNotNull();
                assertThat(postedMessage)
                        .isInstanceOf(Command.class);
                var expected = Command.newBuilder()
                        .setMessage(AnyPacker.pack(commandMessage))
                        .buildPartial();
                assertThat(postedMessage)
                        .comparingExpectedFieldsOnly()
                        .isEqualTo(expected);
            }
        }
    }
}
