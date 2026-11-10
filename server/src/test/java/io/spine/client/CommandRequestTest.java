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

package io.spine.client;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.Message;
import io.spine.base.EventMessage;
import io.spine.core.Command;
import io.spine.core.ErrorCase;
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
import static io.spine.core.ErrorCase.UNSUPPORTED_REQUEST;
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
            private @Nullable ErrorCase returnedErrorCase;

            @BeforeEach
            void setup() {
                postedMessage = null;
                returnedErrorCase = null;
            }

            @Test
            @DisplayName("rejecting `null`")
            void rejectingNull() {
                assertThrows(NullPointerException.class, () -> commandRequest.onServerError(null));
            }

            @Test
            @DisplayName("invoking handler when an invalid command posted")
            void invocation() {
                ServerErrorHandler handler = (message, errorCase) -> {
                    postedMessage = message;
                    returnedErrorCase = errorCase;
                };

                var commandMessage = UnsupportedCommand.newBuilder()
                        .setUser(GivenUserId.generated())
                        .build();
                var request =
                        client().asGuest()
                                .command(commandMessage)
                                .onServerError(handler);
                request.postAndForget();

                assertThat(returnedErrorCase)
                        .isEqualTo(UNSUPPORTED_REQUEST);
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
