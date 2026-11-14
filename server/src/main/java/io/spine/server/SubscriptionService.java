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

import com.google.common.base.Joiner;
import com.google.common.collect.Ordering;
import com.google.errorprone.annotations.CompileTimeConstant;
import com.google.protobuf.Message;
import io.grpc.BindableService;
import io.grpc.stub.StreamObserver;
import io.spine.annotation.VisibleForTesting;
import io.spine.base.Error;
import io.spine.base.EventMessage;
import io.spine.client.Subscription;
import io.spine.client.SubscriptionUpdate;
import io.spine.client.Subscriptions;
import io.spine.client.Target;
import io.spine.client.ThreadSafeObserver;
import io.spine.client.Topic;
import io.spine.client.grpc.SubscriptionServiceGrpc;
import io.spine.core.ActorContext;
import io.spine.core.Response;
import io.spine.core.TenantId;
import io.spine.grpc.StreamObservers;
import io.spine.logging.WithLogging;
import io.spine.server.app.RefusedRequest;
import io.spine.server.stand.InvalidRequestException;
import io.spine.server.stand.Stand;
import io.spine.server.stand.SubscriptionCallback;
import io.spine.server.stand.UnsupportedRequestException;
import io.spine.server.transport.Statuses;
import io.spine.system.server.event.SubscriptionServiceFailed;
import io.spine.system.server.event.SubscriptionTenantNotExpected;
import io.spine.system.server.event.SubscriptionTenantNotSpecified;
import io.spine.system.server.event.SubscriptionValidationFailed;
import io.spine.system.server.event.TopicTenantNotExpected;
import io.spine.system.server.event.TopicTenantNotSpecified;
import io.spine.system.server.event.TopicValidationFailed;
import io.spine.system.server.event.UnknownSubscriptionActivationRequested;
import io.spine.system.server.event.UnknownSubscriptionCancellationRequested;
import io.spine.system.server.event.UnsupportedTopicReceived;
import io.spine.type.TypeUrl;
import io.spine.validation.Validate;
import io.spine.validation.ViolationText;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.Sets.union;
import static io.spine.core.ErrorCase.INTERNAL_ERROR;
import static io.spine.core.ErrorCase.INVALID_REQUEST;
import static io.spine.core.ErrorCase.UNSUPPORTED_REQUEST;
import static io.spine.grpc.StreamObservers.forwardErrorsOnly;
import static io.spine.server.stand.SubscriptionCallback.forwardingTo;
import static java.lang.String.format;

/**
 * The {@code SubscriptionService} provides an asynchronous way to fetch read-side state
 * from the server.
 *
 * <p>A request that cannot be served fails the call with a status that carries only
 * the {@linkplain io.spine.core.ErrorCase kind} of the problem, which the client obtains with
 * {@link StreamObservers#errorCaseOf(Throwable) StreamObservers.errorCaseOf(Throwable)}.
 * The details of the problem stay on the server side.
 *
 * <p>The service serves the requests with the Bounded Contexts of an {@link Application}.
 * The topic of a request to a multi-tenant context must have a tenant registered in
 * the {@linkplain Application#tenantIndex() tenant index} of the application, and the topic of
 * a request to a single-tenant context must have no tenant.
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
    private SubscriptionService(TypeDictionary types, Application application) {
        super();
        this.types = types;
        this.subscriptions = new SubscriptionImpl(this, types, application);
        this.activation = new ActivationImpl(this, types, application);
        this.cancellation = new CancellationImpl(this, types, application);
    }

    /**
     * Creates the service that serves the subscriptions with the Bounded Contexts
     * of the given application.
     */
    public static SubscriptionService of(Application application) {
        checkNotNull(application);
        var dictionary = TypeDictionary.newBuilder();
        application.contexts().forEach(
                context -> dictionary.putAll(context, c -> exposedTypes(c.stand()))
        );
        return new SubscriptionService(dictionary.build(), application);
    }

    /**
     * Obtains the types of the entity states and of the events to which the given
     * {@code Stand} allows subscribing.
     */
    private static Set<TypeUrl> exposedTypes(Stand stand) {
        return union(stand.exposedTypes(), stand.exposedEventTypes());
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
            safeObserver.onError(Statuses.failure(INTERNAL_ERROR));
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
     * Searches for the Bounded Context that provides the messages of the target type.
     *
     * @param target
     *         the type which may be available through this subscription service
     * @return the context that exposes the target type,
     *         or {@code Optional.empty} if no known context does so
     */
    @VisibleForTesting  /* Test-only method. */
    Optional<BoundedContext> findContextOf(Target target) {
        var type = target.type();
        var result = types.find(type);
        return result;
    }

    private static final class SubscriptionImpl extends ServiceDelegate<Topic, Subscription> {

        private SubscriptionImpl(BindableService service,
                                 TypeDictionary types,
                                 Application application) {
            super(service, types, application);
        }

        @Override
        protected TypeUrl enclosedMessageType(Topic topic) {
            return topic.getTarget()
                        .type();
        }

        @Override
        protected TenantId tenantOf(Topic topic) {
            return topic.getContext()
                        .getTenantId();
        }

        @Override
        protected Message idOf(Topic topic) {
            return topic.getId();
        }

        @Override
        protected RefusedRequest refusedRequest(Topic topic) {
            return RefusedRequest.newBuilder()
                    .setTopic(topic)
                    .build();
        }

        @Override
        protected EventMessage tenantNotSpecified(Topic topic) {
            return TopicTenantNotSpecified.newBuilder()
                    .setId(topic.getId())
                    .setActorContext(topic.getContext())
                    .build();
        }

        @Override
        protected EventMessage tenantNotExpected(Topic topic, TenantId tenant) {
            return TopicTenantNotExpected.newBuilder()
                    .setId(topic.getId())
                    .setActorContext(topic.getContext())
                    .setTenant(tenant)
                    .build();
        }

        @Override
        protected void serve(BoundedContext context,
                             Topic topic,
                             StreamObserver<Subscription> observer,
                             @Nullable Object params) {
            try {
                context.stand()
                       .subscribe(topic, observer);
            } catch (InvalidRequestException e) {
                var failed = TopicValidationFailed.newBuilder()
                        .setId(topic.getId())
                        .setValidationError(e.validationError())
                        .setActorContext(topic.getContext())
                        .build();
                reject(context, topic, failed, INVALID_REQUEST, observer);
            } catch (UnsupportedRequestException e) {
                var unsupported = UnsupportedTopicReceived.newBuilder()
                        .setId(topic.getId())
                        .setTypeUrl(topic.getTarget()
                                         .getType())
                        .setActorContext(topic.getContext())
                        .build();
                reject(context, topic, unsupported, UNSUPPORTED_REQUEST, observer);
            }
        }

        /**
         * Creates a subscription in each Bounded Context of the application.
         *
         * <p>A topic with a tenant is refused unless the tenant is registered in
         * the application. A topic that fails validation is refused with
         * {@link io.spine.core.ErrorCase#INVALID_REQUEST INVALID_REQUEST}.
         */
        @Override
        protected void serveNoContext(Topic topic,
                                      StreamObserver<Subscription> observer,
                                      @Nullable Object params) {
            if (!admittedToAll(topic, observer)) {
                return;
            }
            var violations = Validate.violationsOf(topic);
            if (!violations.isEmpty()) {
                refuse(topic, INVALID_REQUEST, observer, ViolationText.ofAll(violations));
                return;
            }
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

        @Override
        protected EventMessage failureEvent(Topic topic, Error error) {
            return SubscriptionServiceFailed.newBuilder()
                    .setTopic(topic.getId())
                    .setError(error)
                    .setActorContext(topic.getContext())
                    .build();
        }
    }

    /**
     * An abstract base for the delegates that serve requests for an existing subscription.
     */
    private abstract static class SubscriptionDelegate
            extends ServiceDelegate<Subscription, Response> {

        SubscriptionDelegate(BindableService service,
                             TypeDictionary types,
                             Application application) {
            super(service, types, application);
        }

        @Override
        protected TypeUrl enclosedMessageType(Subscription subscription) {
            return subscription.targetType();
        }

        @Override
        protected TenantId tenantOf(Subscription subscription) {
            return subscription.getTopic()
                               .getContext()
                               .getTenantId();
        }

        @Override
        protected Message idOf(Subscription subscription) {
            return subscription.getId();
        }

        @Override
        protected EventMessage tenantNotSpecified(Subscription subscription) {
            return SubscriptionTenantNotSpecified.newBuilder()
                    .setId(subscription.getId())
                    .setActorContext(actorOf(subscription))
                    .build();
        }

        @Override
        protected EventMessage tenantNotExpected(Subscription subscription, TenantId tenant) {
            return SubscriptionTenantNotExpected.newBuilder()
                    .setId(subscription.getId())
                    .setActorContext(actorOf(subscription))
                    .setTenant(tenant)
                    .build();
        }

        @Override
        protected final void serve(BoundedContext context,
                                   Subscription subscription,
                                   StreamObserver<Response> observer,
                                   @Nullable Object params) {
            try {
                serveBy(context.stand(), subscription, observer, params);
            } catch (InvalidRequestException e) {
                var failed = SubscriptionValidationFailed.newBuilder()
                        .setId(subscription.getId())
                        .setValidationError(e.validationError())
                        .setActorContext(actorOf(subscription))
                        .build();
                reject(context, subscription, failed, INVALID_REQUEST, observer);
            } catch (UnsupportedRequestException e) {
                var unknown = unknownSubscriptionEvent(subscription);
                reject(context, subscription, unknown, UNSUPPORTED_REQUEST, observer);
            }
        }

        /**
         * Serves the request in those Bounded Contexts known to this service that have
         * the subscription.
         *
         * <p>No Bounded Context serves the message targeted in the subscription. Therefore,
         * upon subscribing, the subscription
         * {@linkplain SubscriptionImpl#serveNoContext(Topic, StreamObserver, Object) was created}
         * in each context known to the service that handled the subscribing. Another service
         * may know other contexts, which do not have the subscription.
         *
         * <p>A subscription whose topic has a tenant is refused unless the tenant is
         * registered in the application. A subscription that fails validation is refused with
         * {@link io.spine.core.ErrorCase#INVALID_REQUEST INVALID_REQUEST}. If no context has
         * the subscription, the request is {@linkplain #refuseNotServed refused}.
         *
         * @implNote The original {@code observer} is only fed with an acknowledgement once
         *         after the request is served in each Bounded Context.
         *         This is because it is not possible to call {@code onCompleted()}
         *         several times, which would happen should we pass
         *         the original {@code observer} to each Bounded Context.
         */
        @Override
        protected final void serveNoContext(Subscription subscription,
                                            StreamObserver<Response> observer,
                                            @Nullable Object params) {
            if (!admittedToAll(subscription, observer)) {
                return;
            }
            var violations = Validate.violationsOf(subscription);
            if (!violations.isEmpty()) {
                refuse(subscription, INVALID_REQUEST, observer, ViolationText.ofAll(violations));
                return;
            }
            var holders = contexts().stream()
                    .filter(context -> context.stand()
                                              .hasSubscription(subscription))
                    .collect(toImmutableList());
            if (holders.isEmpty()) {
                refuseNotServed(subscription, observer);
                return;
            }
            var gatheringObserver = StreamObservers.<Response>memoizingObserver();
            for (var context : holders) {
                serveBy(context.stand(), subscription, gatheringObserver, params);
            }
            observer.onNext(gatheringObserver.firstResponse());
            observer.onCompleted();
        }

        /**
         * Serves the request with the given {@code Stand}.
         */
        abstract void serveBy(Stand stand,
                              Subscription subscription,
                              StreamObserver<Response> observer,
                              @Nullable Object params);

        /**
         * Creates the event that records a request for a subscription that
         * the bounded context does not have.
         */
        abstract EventMessage unknownSubscriptionEvent(Subscription subscription);

        @Override
        protected EventMessage failureEvent(Subscription subscription, Error error) {
            return SubscriptionServiceFailed.newBuilder()
                    .setTopic(subscription.getTopic()
                                          .getId())
                    .setSubscription(subscription.getId())
                    .setError(error)
                    .setActorContext(actorOf(subscription))
                    .build();
        }

        /**
         * Obtains the actor context of the topic of the given subscription.
         */
        static ActorContext actorOf(Subscription subscription) {
            return subscription.getTopic()
                               .getContext();
        }
    }

    private static final class ActivationImpl extends SubscriptionDelegate {

        private ActivationImpl(BindableService service,
                               TypeDictionary types,
                               Application application) {
            super(service, types, application);
        }

        @Override
        protected RefusedRequest refusedRequest(Subscription subscription) {
            return RefusedRequest.newBuilder()
                    .setSubscriptionActivation(subscription)
                    .build();
        }

        @Override
        void serveBy(Stand stand,
                     Subscription subscription,
                     StreamObserver<Response> observer,
                     @Nullable Object params) {
            var callback = (SubscriptionCallback) checkNotNull(params);
            stand.activate(subscription, callback, observer);
        }

        @Override
        EventMessage unknownSubscriptionEvent(Subscription subscription) {
            return UnknownSubscriptionActivationRequested.newBuilder()
                    .setId(subscription.getId())
                    .setActorContext(actorOf(subscription))
                    .build();
        }
    }

    private static final class CancellationImpl extends SubscriptionDelegate {

        private CancellationImpl(BindableService service,
                                 TypeDictionary types,
                                 Application application) {
            super(service, types, application);
        }

        @Override
        protected RefusedRequest refusedRequest(Subscription subscription) {
            return RefusedRequest.newBuilder()
                    .setSubscriptionCancellation(subscription)
                    .build();
        }

        @Override
        void serveBy(Stand stand,
                     Subscription subscription,
                     StreamObserver<Response> observer,
                     @Nullable Object params) {
            stand.cancel(subscription, observer);
        }

        @Override
        EventMessage unknownSubscriptionEvent(Subscription subscription) {
            return UnknownSubscriptionCancellationRequested.newBuilder()
                    .setId(subscription.getId())
                    .setActorContext(actorOf(subscription))
                    .build();
        }
    }

    private static String contextsAsString(Iterable<BoundedContext> contexts) {
        return Joiner.on(", ")
                     .join(contexts);
    }
}
