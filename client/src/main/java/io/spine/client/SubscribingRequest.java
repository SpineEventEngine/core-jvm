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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.OverridingMethodsMustInvokeSuper;
import com.google.protobuf.Message;
import io.grpc.stub.StreamObserver;
import io.spine.base.MessageContext;

import java.util.Optional;
import java.util.function.Consumer;

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

    SubscribingRequest(ClientRequest parent, Class<M> type) {
        super(parent, type);
    }

    /**
     * Obtains the builder for collecting consumers of the subscribed messages.
     */
    abstract Consumers.Builder<M, C, W, ?> consumers();

    /**
     * Adapts the passed instance to the specific type of {@link MessageConsumer}.
     */
    abstract MessageConsumer<M, C> toMessageConsumer(Consumer<M> consumer);

    /**
     * Subscribes the passed consumer to receive messages of the subscribed type.
     */
    @CanIgnoreReturnValue
    public B observe(Consumer<M> consumer) {
        consumers().add(toMessageConsumer(consumer));
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
    @Override
    @CanIgnoreReturnValue
    @OverridingMethodsMustInvokeSuper
    public B onStreamingError(ErrorHandler handler) {
        super.onStreamingError(handler);
        consumers().onStreamingError(handler);
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
    public B onConsumingError(ConsumerErrorHandler<M> handler) {
        consumers().onConsumingError(handler);
        return self();
    }

    /**
     * Creates and posts the subscription request to the server.
     */
    public Subscription post() {
        var topic = builder().build();
        var observer = createObserver();
        var subscription = chain().map(c -> subscribe(topic, observer, c))
                                  .orElseGet(() -> subscribe(topic, observer));
        return subscription;
    }

    private StreamObserver<W> createObserver() {
        return consumers().build()
                          .toObserver();
    }

    /**
     * Returns an observer of raw {@code SubscriptionUpdate}s, which will be called
     * in addition to notifying the {@linkplain #consumers() consumers}.
     *
     * <p>Descendants may choose to override this method in order to specify their
     * observer chaining policy.
     *
     * @return {@code StreamObserver} to call in chain (wrapped into {@code Optional}),
     *         or {@code Optional.empty()} if no such chaining is configured
     */
    protected Optional<StreamObserver<SubscriptionUpdate>> chain() {
        return Optional.empty();
    }

    private Subscription subscribe(Topic topic, StreamObserver<W> observer) {
        var subscription =
                client().subscriptions()
                        .subscribeTo(topic, observer);
        return subscription;
    }

    private Subscription subscribe(Topic topic,
                                   StreamObserver<W> observer,
                                   StreamObserver<SubscriptionUpdate> chain) {
        var subscription =
                client().subscriptions()
                        .subscribeTo(topic, observer, chain);
        return subscription;
    }
}
