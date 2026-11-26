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
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.OverridingMethodsMustInvokeSuper;
import com.google.protobuf.Message;
import io.spine.base.MessageContext;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Abstract base for client requests that subscribe to messages.
 *
 * @param <M>
 *         the type of the subscribed messages
 * @param <C>
 *         the type of the context of messages or {@link io.spine.core.EmptyContext} if
 *         messages do not have a context
 * @param <W>
 *         the type of the message that wraps a message and its context
 *         (e.g. {@link io.spine.core.Event}); if subscribed message type does not have a context,
 *         this parameter is likely to be the same as {@code M}
 * @param <B>
 *         the type of this request, for returning type covariance
 */
public abstract class
SubscribingRequest<M extends Message,
        C extends MessageContext,
        W extends Message,
        B extends SubscribingRequest<M, C, W, B>>
        extends FilteringRequest<M, Topic, TopicBuilder, B> {

    private @Nullable ConsumerErrorHandler<M> consumingErrorHandler;

    SubscribingRequest(ClientRequest parent, Class<M> type) {
        super(parent, type);
    }

    /**
     * Adds the passed consumer of the subscribed messages to the target of the request.
     */
    abstract void addConsumer(Consumer<M> consumer);

    /**
     * Builds the observer of the target of the request: the target, with the consumers
     * of its messages.
     *
     * @param consumingErrorHandler
     *         the handler of errors thrown by the consumers, or {@code null} to log such errors
     */
    abstract TargetObserver toObserver(@Nullable ConsumerErrorHandler<M> consumingErrorHandler);

    /**
     * Subscribes the passed consumer to receive messages of the subscribed type.
     */
    @CanIgnoreReturnValue
    public B observe(Consumer<M> consumer) {
        addConsumer(checkNotNull(consumer));
        return self();
    }

    /**
     * Assigns a handler for the error reported to
     * {@link io.grpc.stub.StreamObserver#onError(Throwable) StreamObserver.onError(Throwable)} of
     * the {@link io.grpc.stub.StreamObserver StreamObserver} responsible for delivering messages
     * to the consumers.
     *
     * <p>Once this handler is called, no more messages will be delivered to consumers.
     *
     * @see #onConsumingError(ConsumerErrorHandler)
     */
    @Override
    @CanIgnoreReturnValue
    @OverridingMethodsMustInvokeSuper
    public B onStreamingError(ErrorHandler handler) {
        super.onStreamingError(handler);
        return self();
    }

    /**
     * Assigns a handler for an error that may occur in the code of one of the consumers.
     *
     * <p>After this handler is called, remaining consumers will get the message as usual.
     *
     * @see #onStreamingError(ErrorHandler)
     */
    @CanIgnoreReturnValue
    public B onConsumingError(ConsumerErrorHandler<M> handler) {
        this.consumingErrorHandler = checkNotNull(handler);
        return self();
    }

    /**
     * Creates and posts the subscription request to the server.
     */
    public Subscription post() {
        var observer = toObserver(consumingErrorHandler);
        var topics = client().requestOf(user())
                             .topic();
        var subscription =
                client().subscriptions()
                        .subscribeTo(topics, ImmutableList.of(observer), streamingErrorHandler());
        return subscription;
    }
}
