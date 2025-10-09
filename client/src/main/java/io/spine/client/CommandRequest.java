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

import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.OverridingMethodsMustInvokeSuper;
import io.spine.annotation.VisibleForTesting;
import io.spine.base.CommandMessage;
import io.spine.base.EventMessage;
import io.spine.core.Command;
import io.spine.core.Status;
import io.spine.logging.WithLogging;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.client.EventsAfterCommand.subscribe;
import static io.spine.type.ProtoTexts.shortDebugString;
import static io.spine.util.Exceptions.newIllegalStateException;

/**
 * Allows to post a command optionally subscribing to events that are immediate results
 * of handling this command.
 *
 * <p>Usage example:
 * <pre>{@code
 * Subscription loginSubscription =
 *     client.asGuest()
 *           .command(logInUser)
 *           .observe(UserLoggedIn.class, (event, context) -> { ... })
 *           .observe(UserAlreadyLoggedIn.class, (rejection, context) -> { ... })
 *           .onStreamingError((throwable) -> { ... })
 *           .post();
 * }</pre>
 *
 * <p>The subscription obtained from the {@link #post()} should be cancelled
 * to preserve both client-side and server-side resources. The moment of cancelling
 * the subscriptions depends on the nature of the posted command and the outcome
 * expected by the client application.
 */
public final class CommandRequest extends ClientRequestBase implements WithLogging {

    private final CommandMessage message;
    private final MultiEventConsumers.Builder eventConsumers;

    CommandRequest(ClientRequest parent, CommandMessage c) {
        super(parent);
        this.message = c;
        this.eventConsumers = MultiEventConsumers.newBuilder();
    }

    /**
     * Adds the passed consumer to the subscribers of the event of the passed type.
     *
     * @param type
     *         the type of the event message to be received by the consumer
     * @param consumer
     *         the consumer
     * @param <E>
     *         the type of the event
     */
    @CanIgnoreReturnValue
    public <E extends EventMessage> CommandRequest
    observe(Class<E> type, Consumer<E> consumer) {
        checkNotNull(consumer);
        eventConsumers.observe(type, consumer);
        return this;
    }

    /**
     * Adds the passed event consumer to the subscribers of the event of the passed type.
     *
     * @param type
     *         the type of the event message to be received by the consumer
     * @param consumer
     *         the consumer of the event message and its context
     * @param <E>
     *         the type of the event
     */
    @CanIgnoreReturnValue
    public <E extends EventMessage> CommandRequest
    observe(Class<E> type, EventConsumer<E> consumer) {
        checkNotNull(consumer);
        eventConsumers.observe(type, consumer);
        return this;
    }

    /**
     * Assigns a handler for errors occurred when delivering events.
     *
     * <p>If such an error occurs, no more events resulting from the posted command will be
     * delivered to the consumers.
     */
    @Override
    @CanIgnoreReturnValue
    public CommandRequest onStreamingError(ErrorHandler handler) {
        super.onStreamingError(handler);
        eventConsumers.onStreamingError(handler);
        return this;
    }

    /**
     * Assigns a handler for errors occurred in consumers of events.
     *
     * <p>After the passed handler is called, remaining event consumers will get the messages
     * as usually. If not specified, the default implementation simply logs the error.
     */
    @CanIgnoreReturnValue
    public CommandRequest onConsumingError(ConsumerErrorHandler<EventMessage> handler) {
        checkNotNull(handler);
        eventConsumers.onConsumingError(handler);
        return this;
    }

    /**
     * Assigns a handler for an error occurred on the server-side (such as validation error)
     * in response to posting a command.
     */
    @OverridingMethodsMustInvokeSuper
    @CanIgnoreReturnValue
    @Override
    public CommandRequest onServerError(ServerErrorHandler handler) {
        super.onServerError(handler);
        return this;
    }

    /**
     * Subscribes the consumers to events to receive events resulting from the command as
     * they happen, then sends the command to the server.
     *
     * <p>The returned {@code Subscription} instances should be
     * {@linkplain Subscriptions#cancel(Subscription) canceled} after the requesting code receives
     * expected events, or after a reasonable timeout.
     *
     * <p>The method returns subscriptions to {@linkplain #observe(Class, EventConsumer) events}
     * that the handling of the command <em>may</em> produce. A command <em>may</em> not be accepted
     * for processing by the server, e.g. because of a validation error. In such a case, the
     * method would report the error to the configured {@linkplain #onStreamingError(ErrorHandler)
     * error handler}, and return an empty set.
     *
     * @return subscription to the {@linkplain #observe(Class, EventConsumer) observed events}
     *         if the command was successfully posted, or
     *         an empty set if posting caused an error
     * @apiNote Subscriptions should be cancelled to free up client and server resources
     *         required for their maintenance. It is not possible to cancel the returned
     *         subscription in an automatic way because of the following.
     *         Subscriptions by nature are asynchronous and infinite requests.
     *         Even that we know expected types of the events produced by the command, only the
     *         client code "knows" how many of them it expects. Also, some events may not arrive
     *         because of communication or business logic reasons. That's why the returned
     *         subscriptions should be cancelled by the client code when it no longer needs it.
     * @see #postAndForget()
     */
    public ImmutableSet<Subscription> post() {
        var op = new PostOperation();
        return op.perform();
    }

    /**
     * Posts the command without subscribing to events that may be generated during
     * the command handling.
     *
     * @throws IllegalStateException
     *         if {@link #observe(Class, EventConsumer)} or {@link #observe(Class, Consumer)} were
     *         called in the command request configuration chain before calling this method
     * @see #post()
     */
    public void postAndForget() throws IllegalStateException {
        var op = new PostOperation();
        op.performWithoutSubscriptions();
    }

    @VisibleForTesting
    CommandMessage message() {
        return message;
    }

    /**
     * Method object for posting a command.
     */
    private final class PostOperation {

        private final Command command;
        private final MultiEventConsumers consumers;
        private @Nullable ImmutableSet<Subscription> subscriptions;

        private PostOperation() {
            this.command =
                    client().requestOf(user())
                            .command()
                            .create(message);
            this.consumers = eventConsumers.build();
        }

        private ImmutableSet<Subscription> perform() {
            if (consumers.isEmpty()) {
                throw newIllegalStateException(
                        "No event subscriptions were requested prior to calling `post()`." +
                        " If you intend to receive events please call `observe()`" +
                                " before `post()`." +
                        " Or, if you subscribe to events or projections elsewhere," +
                                " please use `postAndForget()`."
                );
            }
            subscribeToEvents();
            var ack = client().post(command);
            var status = ack.getStatus();
            return handleStatus(status);
        }

        private void performWithoutSubscriptions() {
            if (!consumers.isEmpty()) {
                throw newIllegalStateException(
                        "Subscriptions to events were requested. Please call `post()` instead."
                );
            }
            var ack = client().post(command);
            var status = ack.getStatus();
            handleStatus(status);
        }

        private void subscribeToEvents() {
            var client = client();
            this.subscriptions = subscribe(client, command, consumers, streamingErrorHandler());
            client.subscriptions()
                  .addAll(subscriptions);
        }

        @CanIgnoreReturnValue
        private ImmutableSet<Subscription> handleStatus(Status status) {
            switch (status.getStatusCase()) {
                case OK:
                    return Optional.ofNullable(subscriptions)
                                   .orElse(ImmutableSet.of());
                case ERROR:
                    cancelVoidSubscriptions();
                    reportErrorWhenPosting(status);
                    return ImmutableSet.of();
                case REJECTION:
                    /* This should not happen as a rejection can be raised when the command is
                       already dispatched. We include this case for the sake of completeness. */
                case STATUS_NOT_SET:
                    /* The server sent an ack with invalid status. */
                default:
                    throw newIllegalStateException(
                            "Cannot handle ack status `%s` when posting the command `%s`.",
                            shortDebugString(command));
            }
        }

        /**
         * Cancels the passed subscriptions to events because the command could not be posted.
         *
         */
        private void cancelVoidSubscriptions() {
            if (subscriptions != null) {
                var activeSubscriptions = client().subscriptions();
                subscriptions.forEach(activeSubscriptions::cancel);
            }
        }

        private void reportErrorWhenPosting(Status status) {
            errorHandler().accept(command, status.getError());
        }

        private ServerErrorHandler errorHandler() {
            return Optional.ofNullable(serverErrorHandler())
                           .orElse(new LoggingServerErrorHandler(
                                   CommandRequest.this.logger(),
                                   "Unable to post the command `%s`. Returned error: `%s`.")
                           );
        }
    }
}
