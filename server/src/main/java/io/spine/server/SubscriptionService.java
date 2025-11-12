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

import com.google.common.base.Joiner;
import com.google.common.collect.Ordering;
import com.google.errorprone.annotations.CompileTimeConstant;
import io.grpc.BindableService;
import io.grpc.stub.StreamObserver;
import io.spine.annotation.VisibleForTesting;
import io.spine.client.Subscription;
import io.spine.client.SubscriptionUpdate;
import io.spine.client.Subscriptions;
import io.spine.client.Target;
import io.spine.client.ThreadSafeObserver;
import io.spine.client.Topic;
import io.spine.client.grpc.SubscriptionServiceGrpc;
import io.spine.core.Response;
import io.spine.grpc.MemoizingObserver;
import io.spine.grpc.StreamObservers;
import io.spine.logging.WithLogging;
import io.spine.server.stand.SubscriptionCallback;
import io.spine.type.TypeUrl;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Sets.union;
import static io.spine.grpc.StreamObservers.forwardErrorsOnly;
import static io.spine.server.stand.SubscriptionCallback.forwardingTo;
import static java.lang.String.format;

/**
 * The {@code SubscriptionService} provides an asynchronous way to fetch read-side state
 * from the server.
 *
 * <p>For synchronous read-side updates please see {@link QueryService}.
 */
public final class SubscriptionService
        extends SubscriptionServiceGrpc.SubscriptionServiceImplBase
        implements WithLogging {

    private final TypeDictionary types;
    private final SubscriptionImpl subscriptions;
    private final ActivationImpl activation;
    private final CancellationImpl cancellation;

    @SuppressWarnings("ThisEscapedInObjectConstruction" /* To simplify the implementation. */)
    private SubscriptionService(TypeDictionary types) {
        super();
        this.types = types;
        this.subscriptions = new SubscriptionImpl(this, types);
        this.activation = new ActivationImpl(this, types);
        this.cancellation = new CancellationImpl(this, types);
    }

    /**
     * Creates a new builder for the service.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Builds the service with a single Bounded Context.
     */
    public static SubscriptionService withSingle(BoundedContext context) {
        checkNotNull(context);
        var result = newBuilder()
                .add(context)
                .build();
        return result;
    }

    /**
     * Executes the given consumer using a {@link ThreadSafeObserver} over the given one.
     *
     * @param consumer
     *         the code to execute
     * @param observer
     *         an observer for handling the request
     * @param errorMessage
     *         the error message to be put into a log if an exception occurs when
     *         running the consumer
     * @param <S>
     *         the type of objects accepted by the observers
     */
    private <S> void runThreadSafe(Consumer<ThreadSafeObserver<S>> consumer,
                                   StreamObserver<S> observer,
                                   @CompileTimeConstant final String errorMessage) {
        var safeObserver = new ThreadSafeObserver<>(observer);
        try {
            consumer.accept(safeObserver);
        } catch (@SuppressWarnings("OverlyBroadCatchBlock") Exception e) {
            logger().atError().withCause(e).log(() -> errorMessage);
            safeObserver.onError(e);
        }
    }

    @Override
    public void subscribe(Topic topic, StreamObserver<Subscription> observer) {
        logger().atDebug().log(() -> format(
                "Creating the subscription to the topic: `%s`.", topic));
        runThreadSafe(
                (safeObserver) -> subscriptions.serve(topic, safeObserver, null),
                observer, "Error processing subscription request."
        );
    }

    @Override
    public void activate(Subscription subscription, StreamObserver<SubscriptionUpdate> observer) {
        logger().atDebug().log(() -> format("Activating the subscription: `%s`.", subscription));
        runThreadSafe(
                (safeObserver) -> {
                    var callback = forwardingTo(safeObserver);
                    StreamObserver<Response> responseObserver = forwardErrorsOnly(safeObserver);
                    activation.serve(subscription, responseObserver, callback);
                },
                observer, "Error activating the subscription."
        );
    }

    @Override
    public void cancel(Subscription subscription, StreamObserver<Response> observer) {
        logger().atDebug().log(() -> format(
                "Incoming cancel request for the subscription topic: `%s`.", subscription));
        runThreadSafe(
                (safeObserver) -> cancellation.serve(subscription, safeObserver, null),
                observer, "Error processing cancel subscription request."
        );
    }

    /**
     * Searches for the Bounded Context which provides the messages of the target type.
     *
     * @param target
     *         the type which may be available through this subscription service
     * @return the context which exposes the target type,
     *         or {@code Optional.empty} if no known context does so
     */
    @VisibleForTesting  /* Test-only method. */
    Optional<BoundedContext> findContextOf(Target target) {
        var type = target.type();
        var result = types.find(type);
        return result;
    }

    private static final class SubscriptionImpl extends ServiceDelegate<Topic, Subscription> {

        private SubscriptionImpl(BindableService service, TypeDictionary types) {
            super(service, types);
        }

        @Override
        protected TypeUrl enclosedMessageType(Topic topic) {
            return topic.getTarget()
                        .type();
        }

        @Override
        protected void serve(BoundedContext context,
                             Topic topic,
                             StreamObserver<Subscription> observer,
                             @Nullable Object params) {
            var stand = context.stand();
            stand.subscribe(topic, observer);
        }

        /**
         * Creates a subscription in each Bounded Context known to {@code SubscriptionService}.
         */
        @Override
        protected void serveNoContext(Topic topic,
                                      StreamObserver<Subscription> observer,
                                      @Nullable Object params) {
            List<BoundedContext> contexts = new ArrayList<>(contexts());
            contexts.sort(Ordering.natural());
            logger().atWarning().log(() -> format(
                    "Unable to find a Bounded Context for type `%s`." +
                            " Creating a subscription in contexts: %s.",
                    topic.getTarget().type(),
                    contextsAsString(contexts)));
            var subscription = Subscriptions.from(topic);
            for (var context : contexts) {
                var stand = context.stand();
                stand.subscribe(subscription);
            }
            observer.onNext(subscription);
            observer.onCompleted();
        }
    }

    private abstract static class SubscriptionDelegate
            extends ServiceDelegate<Subscription, Response> {

        SubscriptionDelegate(BindableService service, TypeDictionary types) {
            super(service, types);
        }

        @Override
        protected TypeUrl enclosedMessageType(Subscription subscription) {
            return subscription.targetType();
        }
    }

    private static final class ActivationImpl extends SubscriptionDelegate {

        private ActivationImpl(BindableService service, TypeDictionary types) {
            super(service, types);
        }

        @Override
        protected void serve(BoundedContext context,
                             Subscription subscription,
                             StreamObserver<Response> observer,
                             @Nullable Object params) {
            var callback = (SubscriptionCallback) checkNotNull(params);
            var stand = context.stand();
            stand.activate(subscription, callback, observer);
        }

        @Override
        protected void serveNoContext(Subscription subscription,
                                      StreamObserver<Response> observer,
                                      @Nullable Object params) {
            contexts().forEach(context -> serve(context, subscription, observer, params));
        }
    }

    private static final class CancellationImpl extends SubscriptionDelegate {

        private CancellationImpl(BindableService service, TypeDictionary types) {
            super(service, types);
        }

        @Override
        protected void serve(BoundedContext context,
                             Subscription subscription,
                             StreamObserver<Response> observer,
                             @Nullable Object params) {
            var stand = context.stand();
            stand.cancel(subscription, observer);
        }

        /**
         * Performs the cancellation of the subscription.
         *
         * <p>Such a use case means that it was not possible to detect a Bounded Context
         * serving the message targeted in the subscription. Thus, upon subscribing,
         * the subscription
         * {@linkplain SubscriptionImpl#serveNoContext(Topic, StreamObserver, Object) was created}
         * in each known Bounded Context.
         *
         * <p>Therefore, the cancellation is also performed in each Bounded Context known
         * to this service.
         *
         * @implNote The original {@code observer} is only fed with an acknowledgement once
         *         after the subscription in cancelled in each Bounded Context.
         *         This is because it is not possible to call {@code onCompleted()}
         *         for several times, which would happen should we run
         *         the original {@code observer} through a default cancellation procedure.
         */
        @Override
        protected void serveNoContext(Subscription subscription,
                                      StreamObserver<Response> observer,
                                      @Nullable Object params) {
            var contexts = contexts();
            logger().atWarning().log(() -> format(
                    "Trying to cancel a subscription `%s` which could not be found. " +
                            "Cancelling it in all known contexts, where it may reside: %s.",
                    subscription.toShortString(),
                    contextsAsString(contexts))
            );
            var gatheringObserver = StreamObservers.<Response>memoizingObserver();
            for (var context : contexts) {
                if(context.stand().hasSubscription(subscription.getId())) {
                    serve(context, subscription, gatheringObserver, params);
                }
            }
            summarizeResponses(gatheringObserver, observer);
            observer.onCompleted();
        }

        private static void
        summarizeResponses(MemoizingObserver<Response> summarizer,
                           StreamObserver<Response> destination) {
            var observedError = summarizer.getError();
            if(observedError != null) {
                destination.onError(observedError);
                return;
            }
            var responses = summarizer.responses();
            if(!responses.isEmpty()) {
                var response = responses.get(0);
                destination.onNext(response);
            }
        }
    }

    private static String contextsAsString(Iterable<BoundedContext> contexts) {
        return Joiner.on(", ")
                     .join(contexts);
    }

    /**
     * The builder for the {@link SubscriptionService}.
     */
    public static class Builder extends AbstractServiceBuilder<SubscriptionService, Builder> {

        /**
         * Builds the {@link SubscriptionService}.
         *
         * @throws IllegalStateException
         *         if no Bounded Contexts were added.
         */
        @Override
        public SubscriptionService build() throws IllegalStateException {
            var dictionary = TypeDictionary.newBuilder();
            contexts().forEach(
                    context -> dictionary.putAll(context, (c) ->
                            union(c.stand().exposedTypes(), c.stand().exposedEventTypes())
                    )
            );
            var result = new SubscriptionService(dictionary.build());
            warnIfEmpty(result);
            return result;
        }

        @Override
        Builder self() {
            return this;
        }
    }
}
