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
import com.google.common.reflect.TypeToken;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.protobuf.Message;
import io.grpc.stub.StreamObserver;
import io.spine.base.MessageContext;
import io.spine.logging.WithLogging;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * A collection of message consumers that delivers messages to them.
 *
 * <p>This class is a bridge between gRPC to which it connects via exposed
 * {@link #toObserver() StreamObserver} and functional API of {@link MessageConsumer}s
 * collected via {@link Builder}.
 *
 * <p>There are two types of errors that may occur when delivering messages to the consumers:
 * <ol>
 *     <li><strong>Streaming errors</strong> occur on the
 *      {@linkplain StreamObserver#onError(Throwable) level of gRPC}. When such an error occur,
 *      no further messages will be delivered. To handle such an error, please
 *      supply a streaming {@link Builder#onStreamingError(ErrorHandler) ErrorHandler}.
 *      If no handler is configured, default implementation will log the error.
 *
 *     <li><strong>Consuming errors</strong> occur when one of the consumers throws. Such an error
 *     does not prevent delivery of a message to other consumers. To handle such an error, please
 *     supply a {@link Builder#onConsumingError(ConsumerErrorHandler) ConsumingErrorHandler}.
 *     If no handler is configured, default implementation will log the error.
 * </ol>
 *
 * @param <M>
 *         the type of consumed messages
 * @param <C>
 *         the type of context of consumed messages, or {@link io.spine.core.EmptyContext}
 *         if messages do not have a context
 * @param <W>
 *         the type of message that wraps the consumed message and its context,
 *         or the same type as {@code <M>} if the messages do not have a context
 */
abstract class Consumers<M extends Message, C extends MessageContext, W extends Message>
        implements WithLogging {

    private final ImmutableSet<MessageConsumer<M, C>> consumers;
    private final ErrorHandler streamingErrorHandler;
    private final ConsumerErrorHandler<M> consumingErrorHandler;

    Consumers(Builder<M, C, ?, ?> builder) {
        this.consumers = builder.consumers.build();
        checkState(!consumers.isEmpty(), "At least one message consumer must be supplied.");
        this.streamingErrorHandler = ifNullUse(
                builder.streamingErrorHandler,
                () -> ErrorHandler.logError(
                        this.logger(), "Error receiving a message of the type `%s`.",
                        messageType())
        );
        this.consumingErrorHandler = ifNullUse(
                builder.consumingErrorHandler,
                () -> ConsumerErrorHandler.logError(
                        this.logger(),
                        "The consumer `%s` could not handle the message of the type `%s`.",
                        messageType())
        );
    }

    private static <H>
    H ifNullUse(@Nullable H errorHandler, Supplier<? extends H> defaultHandler) {
        return errorHandler == null
               ? defaultHandler.get()
               : errorHandler;
    }

    @SuppressWarnings({
            "SerializableNonStaticInnerClassWithoutSerialVersionUID",
            "SerializableInnerClassWithNonSerializableOuterClass" /* for `TypeToken` work */,
            "unchecked" /* The returned type is a class of messages, which is protected
                           by generic params of this class. */})
    private Class<? extends Message> messageType() {
        var result = new TypeToken<M>(getClass()){}.getRawType();
        return (Class<? extends Message>) result;
    }

    /**
     * Creates the observer for obtaining messages via gRPC.
     */
    abstract StreamObserver<W> toObserver();

    /**
     * Delivers messages to all the consumers.
     *
     * <p>If a streaming error occurs, passes it to {@link Consumers#streamingErrorHandler}.
     */
    abstract class DeliveringObserver implements StreamObserver<W> {

        /**
         * Obtains the message from the wrapping message.
         */
        abstract M toMessage(W outer);

        /**
         * Obtains the message context from the wrapper message.
         */
        abstract C toContext(W outer);

        /**
         * Unwraps the message and its context from the wrapping object and delivers them
         * to consumers.
         */
        @Override
        public void onNext(W value) {
            var msg = toMessage(value);
            var ctx = toContext(value);
            deliver(msg, ctx);
        }

        /**
         * Delivers the message to consumers.
         *
         * <p>If a consumer method throws, the {@code Throwable} is passed
         * to {@link #consumingErrorHandler}, and the message is passed to remaining consumers.
         */
        private void deliver(M message, C context) {
            consumers.forEach(consumer -> {
                try {
                    consumer.accept(message, context);
                } catch (Throwable t) {
                    consumingErrorHandler.accept(consumer, t);
                }
            });
        }

        @Override
        public void onError(Throwable t) {
            streamingErrorHandler.accept(t);
        }

        @Override
        @SuppressWarnings("NoopMethodInAbstractClass")
        public void onCompleted() {
            // Do nothing.
        }
    }

    /**
     * The builder for the collection of consumers of messages of the specified type.
     *
     * @param <M>
     *         the type of the messages delivered to consumers
     * @param <C>
     *         the type of message context, or {@link io.spine.core.EmptyContext} if
     *         consumed messages do not have contexts
     * @param <W>
     *         the type of a message which wraps the message and its context; or
     *         the same type as {@code <M>} if messages do not have contexts
     * @param <B>
     *         the type of this builder for return type covariance
     */
    @SuppressWarnings("rawtypes")
    abstract static class Builder<M extends Message,
                                  C extends MessageContext,
                                  W extends Message,
                                  B extends Builder> {

        private final ImmutableSet.Builder<MessageConsumer<M, C>> consumers =
                ImmutableSet.builder();
        private @Nullable ErrorHandler streamingErrorHandler;
        private @Nullable ConsumerErrorHandler<M> consumingErrorHandler;

        abstract B self();

        abstract Consumers<M, C, W> build();

        @CanIgnoreReturnValue
        B add(MessageConsumer<M, C> consumer) {
            consumers.add(checkNotNull(consumer));
            return self();
        }

        /**
         * Assigns a handler for the error reported to
         * {@link StreamObserver#onError(Throwable)} of
         * the {@link StreamObserver} responsible for delivering messages
         * to the consumers.
         *
         * <p>Once this handler is called, no more messages will be delivered to consumers.
         *
         * @see #onConsumingError(ConsumerErrorHandler)
         */
        @CanIgnoreReturnValue
        B onStreamingError(ErrorHandler handler) {
            streamingErrorHandler = checkNotNull(handler);
            return self();
        }

        /**
         * Assigns a handler for an error that may occur in the code of one of the consumers.
         *
         * <p>After this handler called, remaining consumers will get the message as usually.
         *
         * @see #onStreamingError(ErrorHandler)
         */
        @CanIgnoreReturnValue
        B onConsumingError(ConsumerErrorHandler<M> handler) {
            consumingErrorHandler = checkNotNull(handler);
            return self();
        }
    }
}
