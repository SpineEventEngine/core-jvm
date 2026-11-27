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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Ordering;
import com.google.errorprone.annotations.CompileTimeConstant;
import com.google.protobuf.Message;
import io.grpc.BindableService;
import io.grpc.stub.ServerCallStreamObserver;
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
import io.spine.server.stand.Stand;
import io.spine.server.stand.SubscriptionCallback;
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
import io.spine.validation.ValidationError;
import io.spine.validation.ViolationText;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.IntStream;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.collect.ImmutableSortedSet.toImmutableSortedSet;
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
 * <p>The targets of a topic may be served by different Bounded Contexts. The service then
 * keeps the subscription in each of them, streams the updates of all of them to the client
 * that activates the subscription, and cancels the subscription in all of them.
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
        // Several contexts may deliver updates to the stream. The guard keeps an update that
        // one of them delivers while another one fails from coming after the error.
        runThreadSafe(
                (safeObserver) -> {
                    var callback = forwardingTo(safeObserver);
                    releaseOnCancel(observer, subscription, callback);
                    StreamObserver<Response> responseObserver = forwardErrorsOnly(safeObserver);
                    activation.serve(subscription, responseObserver, callback);
                },
                new GuardedObserver<>(observer), "Error activating the subscription."
        );
    }

    /**
     * Makes the cancellation of the gRPC call that activates the subscription cancel
     * the subscription.
     *
     * <p>The call is cancelled when its client cancels it or goes away without cancelling
     * the subscription, for example, because the client process stops or the connection
     * drops. The subscription is then cancelled in the Bounded Contexts where the given
     * callback serves it.
     *
     * <p>Does nothing if the observer does not belong to a gRPC call, or if the cancellation
     * handler of the call can no longer be set.
     */
    private void releaseOnCancel(StreamObserver<SubscriptionUpdate> observer,
                                 Subscription subscription,
                                 SubscriptionCallback callback) {
        if (!(observer instanceof ServerCallStreamObserver<SubscriptionUpdate> call)) {
            return;
        }
        try {
            call.setOnCancelHandler(() -> activation.release(subscription, callback));
        } catch (IllegalStateException e) {
            logger().atDebug().withCause(e).log(() -> format(
                    "Unable to watch the call that activates the subscription `%s`.",
                    subscription.getId().getValue()));
        }
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
        protected ImmutableList<TypeUrl> enclosedMessageTypes(Topic topic) {
            return typesOf(topic);
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

        /**
         * Creates a subscription in the Bounded Context that serves all the targets of
         * the topic.
         */
        @Override
        protected void serve(BoundedContext context,
                             Topic topic,
                             StreamObserver<Subscription> observer,
                             @Nullable Object params) {
            if (!validated(topic, context, observer)) {
                return;
            }
            var holders = new TreeMap<BoundedContext, Set<Integer>>();
            holders.put(context, allTargetsOf(topic));
            subscribeIn(holders, topic, observer);
        }

        /**
         * Creates a subscription in the Bounded Contexts that serve the targets of the topic.
         *
         * <p>A target is served by the context that serves its type. A target of an event
         * type that no context serves is served by each Bounded Context of the application.
         * A topic with a target of an entity type that no context serves is
         * {@linkplain #refuseNotServed(Object, TypeUrl, StreamObserver) refused}.
         *
         * <p>The tenant of the topic must fit each context that serves the type of a target.
         * If a type is not served, a topic with a tenant is also refused unless the tenant is
         * registered in the application. A topic that fails validation is refused with
         * {@link io.spine.core.ErrorCase#INVALID_REQUEST INVALID_REQUEST}.
         */
        @Override
        protected void serveNoContext(Topic topic,
                                      StreamObserver<Subscription> observer,
                                      @Nullable Object params) {
            var routing = new TopicRouting(topic, this::find);
            for (var context : routing.servingContexts()) {
                if (!admitted(context, topic, observer)) {
                    return;
                }
            }
            if (routing.anyUnserved() && !admittedToAll(topic, observer)) {
                return;
            }
            if (!validated(topic, routing.firstServingContext(), observer)) {
                return;
            }
            var unservedEntity = routing.unservedEntity();
            if (unservedEntity != null) {
                refuseNotServed(topic, unservedEntity, observer);
                return;
            }
            var contexts = ImmutableSortedSet.copyOf(contexts());
            if (routing.hasUnservedEvents()) {
                logger().atWarning().log(() -> format(
                        "Unable to find a Bounded Context for the event types of the targets %s" +
                                " of the topic `%s`. Creating a subscription in contexts: %s.",
                        routing.unservedEvents(), topic.getId().getValue(),
                        contextsAsString(contexts)));
            }
            subscribeIn(routing.holders(contexts), topic, observer);
        }

        /**
         * Checks that the topic satisfies its constraints.
         *
         * <p>Rejects the topic that does not, recording the violations in the given
         * Bounded Context, or in the log of the application if there is no such context.
         *
         * @return {@code true} if the topic is valid
         */
        private boolean validated(Topic topic,
                                  @Nullable BoundedContext recorder,
                                  StreamObserver<Subscription> observer) {
            var violations = Validate.violationsOf(topic);
            if (violations.isEmpty()) {
                return true;
            }
            if (recorder == null) {
                refuse(topic, INVALID_REQUEST, observer, ViolationText.ofAll(violations));
            } else {
                var error = ValidationError.newBuilder()
                        .addAllConstraintViolation(violations)
                        .build();
                var failed = TopicValidationFailed.newBuilder()
                        .setId(topic.getId())
                        .setValidationError(error)
                        .setActorContext(topic.getContext())
                        .build();
                reject(recorder, topic, failed, INVALID_REQUEST, observer);
            }
            return false;
        }

        /**
         * Creates the subscription to the topic, and registers it in each of the given
         * Bounded Contexts for the given targets.
         *
         * <p>The subscription is registered only if each context allows subscribing to its
         * targets. Otherwise, the first context that does not records the refusal.
         * If a context fails to register the subscription, the contexts that did
         * register it drop it.
         *
         * @param holders
         *         the contexts to register the subscription in, with the indexes of
         *         the targets that each of them serves
         */
        private void subscribeIn(SortedMap<BoundedContext, Set<Integer>> holders,
                                 Topic topic,
                                 StreamObserver<Subscription> observer) {
            for (var entry : holders.entrySet()) {
                var context = entry.getKey();
                var stand = context.stand();
                for (int index : entry.getValue()) {
                    var target = topic.getTarget(index);
                    if (!stand.allowsSubscriptionTo(target)) {
                        var unsupported = UnsupportedTopicReceived.newBuilder()
                                .setId(topic.getId())
                                .setTypeUrl(target.getType())
                                .setActorContext(topic.getContext())
                                .build();
                        reject(context, topic, unsupported, UNSUPPORTED_REQUEST, observer);
                        return;
                    }
                }
            }
            var subscription = Subscriptions.from(topic);
            List<BoundedContext> registered = new ArrayList<>();
            for (var entry : holders.entrySet()) {
                var context = entry.getKey();
                try {
                    context.stand()
                           .subscribe(subscription, entry.getValue());
                } catch (RuntimeException e) {
                    cancelIn(registered, subscription, this);
                    fail(context, topic, e, observer);
                    return;
                }
                registered.add(context);
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
        protected ImmutableList<TypeUrl> enclosedMessageTypes(Subscription subscription) {
            return typesOf(subscription.getTopic());
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

        /**
         * Serves the request in the Bounded Contexts that have the subscription.
         *
         * <p>The given context serves the types of all the topic targets, and has admitted
         * the tenant of the subscription. The topic in the request does not decide where
         * the request is served, though: a client may send the subscription back with
         * a modified topic. So, like {@link #serveNoContext(Subscription, StreamObserver, Object)
         * serveNoContext()}, this method finds the contexts that have the subscription by its ID.
         */
        @Override
        protected final void serve(BoundedContext context,
                                   Subscription subscription,
                                   StreamObserver<Response> observer,
                                   @Nullable Object params) {
            serveHolders(subscription, ImmutableSortedSet.of(context), observer, params);
        }

        /**
         * Serves the request in those Bounded Contexts known to this service that have
         * the subscription.
         *
         * <p>The types of the topic targets are served by different Bounded Contexts, or some
         * of them by none. Upon subscribing, the subscription
         * {@linkplain SubscriptionImpl#serveNoContext(Topic, StreamObserver, Object) was created}
         * in each context that serves a type, and, for an event type that no context serves,
         * in each context known to the service that handled the subscribing. Another service
         * may know other contexts, which do not have the subscription.
         *
         * <p>The tenant of the subscription must fit each context that serves the type of
         * a target. If a type is not served, a subscription whose topic has a tenant is also
         * refused unless the tenant is registered in the application. A subscription that fails
         * validation is refused with
         * {@link io.spine.core.ErrorCase#INVALID_REQUEST INVALID_REQUEST}. If no context has
         * the subscription, a context that serves a type records the request, and the request
         * is refused with {@link io.spine.core.ErrorCase#UNSUPPORTED_REQUEST
         * UNSUPPORTED_REQUEST}; if no context serves a type either, the request is
         * {@linkplain #refuseNotServed(Object, StreamObserver) refused} as not served.
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
            var types = typesOf(subscription.getTopic());
            var served = new TreeSet<BoundedContext>();
            var anyUnserved = types.isEmpty();
            for (var type : types) {
                var context = find(type);
                if (context.isPresent()) {
                    served.add(context.get());
                } else {
                    anyUnserved = true;
                }
            }
            for (var context : served) {
                if (!admitted(context, subscription, observer)) {
                    return;
                }
            }
            if (anyUnserved && !admittedToAll(subscription, observer)) {
                return;
            }
            serveHolders(subscription, served, observer, params);
        }

        /**
         * Serves the request in the Bounded Contexts that have the subscription, finding them
         * by the ID of the subscription.
         *
         * @param served
         *         the contexts that serve the types of the topic targets, which have admitted
         *         the tenant of the subscription; they record the problems with the request
         */
        private void serveHolders(Subscription subscription,
                                  SortedSet<BoundedContext> served,
                                  StreamObserver<Response> observer,
                                  @Nullable Object params) {
            if (!validated(subscription, served, observer)) {
                return;
            }
            var holders = contexts().stream()
                    .filter(context -> context.stand()
                                              .hasSubscription(subscription))
                    .collect(toImmutableSortedSet(Ordering.natural()));
            if (holders.isEmpty() && served.isEmpty()) {
                refuseNotServed(subscription, observer);
                return;
            }
            var missing = served.stream()
                    .filter(context -> !holders.contains(context))
                    .findFirst();
            if (missing.isPresent() && (holders.isEmpty() || servesOnlyComplete())) {
                var unknown = unknownSubscriptionEvent(subscription);
                reject(missing.get(), subscription, unknown, UNSUPPORTED_REQUEST, observer);
                return;
            }
            serveIn(holders, subscription, observer, params);
        }

        /**
         * Checks that the subscription satisfies its constraints.
         *
         * <p>Rejects the subscription that does not, recording the violations in the first of
         * the given Bounded Contexts, or in the log of the application if there are none.
         *
         * @return {@code true} if the subscription is valid
         */
        private boolean validated(Subscription subscription,
                                  SortedSet<BoundedContext> served,
                                  StreamObserver<Response> observer) {
            var violations = Validate.violationsOf(subscription);
            if (violations.isEmpty()) {
                return true;
            }
            if (served.isEmpty()) {
                refuse(subscription, INVALID_REQUEST, observer, ViolationText.ofAll(violations));
            } else {
                var error = ValidationError.newBuilder()
                        .addAllConstraintViolation(violations)
                        .build();
                var failed = SubscriptionValidationFailed.newBuilder()
                        .setId(subscription.getId())
                        .setValidationError(error)
                        .setActorContext(actorOf(subscription))
                        .build();
                reject(served.first(), subscription, failed, INVALID_REQUEST, observer);
            }
            return false;
        }

        /**
         * Tells whether the request is served only if each context that serves a type of
         * the topic targets has the subscription.
         */
        abstract boolean servesOnlyComplete();

        /**
         * Serves the request in the given Bounded Contexts, which have the subscription.
         */
        abstract void serveIn(ImmutableSortedSet<BoundedContext> holders,
                              Subscription subscription,
                              StreamObserver<Response> observer,
                              @Nullable Object params);

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

        /**
         * Returns {@code true}: a subscription activated in some of the contexts that serve
         * the types of its targets would miss the updates of the other targets.
         */
        @Override
        boolean servesOnlyComplete() {
            return true;
        }

        /**
         * Activates the subscription in each of the given Bounded Contexts.
         *
         * <p>If a context fails to activate the subscription, the subscription is cancelled
         * in all of them, so that no context sends updates to the stream that the failure
         * closes.
         */
        @Override
        void serveIn(ImmutableSortedSet<BoundedContext> holders,
                     Subscription subscription,
                     StreamObserver<Response> observer,
                     @Nullable Object params) {
            var gatheringObserver = StreamObservers.<Response>memoizingObserver();
            for (var context : holders) {
                try {
                    serveBy(context.stand(), subscription, gatheringObserver, params);
                } catch (RuntimeException e) {
                    cancelIn(holders, subscription, this);
                    fail(context, subscription, e, observer);
                    return;
                }
            }
            observer.onNext(gatheringObserver.firstResponse());
            observer.onCompleted();
        }

        /**
         * Cancels the subscription in the Bounded Contexts where the given callback serves it.
         *
         * <p>Reacts to the cancellation of the gRPC call that activated the subscription.
         * Unlike a cancellation request, it records nothing for the contexts that do not have
         * the subscription: the subscription may already be cancelled, or activated again by
         * another call.
         *
         * <p>A failure to cancel is logged, and does not stop cancelling the subscription in
         * other contexts.
         */
        private void release(Subscription subscription, SubscriptionCallback callback) {
            for (var context : contexts()) {
                try {
                    if (context.stand().release(subscription, callback)) {
                        logger().atDebug().log(() -> format(
                                "The call that activated the subscription `%s` is cancelled." +
                                        " The subscription is cancelled in the bounded context" +
                                        " `%s`.",
                                subscription.getId().getValue(), context.name().getValue()));
                    }
                } catch (RuntimeException e) {
                    logger().atError().withCause(e).log(() -> format(
                            "Unable to cancel the subscription `%s` in the bounded context `%s`" +
                                    " after the call that activated it is cancelled.",
                            subscription.getId().getValue(), context.name().getValue()));
                }
            }
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

        /**
         * Returns {@code false}: a cancellation that failed in some contexts may be requested
         * again, and then only the contexts that still have the subscription serve it.
         */
        @Override
        boolean servesOnlyComplete() {
            return false;
        }

        /**
         * Cancels the subscription in each of the given Bounded Contexts.
         *
         * <p>A failure in one context does not stop cancelling the subscription in the others.
         * The first failure is answered and recorded after that.
         */
        @Override
        void serveIn(ImmutableSortedSet<BoundedContext> holders,
                     Subscription subscription,
                     StreamObserver<Response> observer,
                     @Nullable Object params) {
            var gatheringObserver = StreamObservers.<Response>memoizingObserver();
            Map.Entry<BoundedContext, RuntimeException> failure = null;
            for (var context : holders) {
                try {
                    serveBy(context.stand(), subscription, gatheringObserver, params);
                } catch (RuntimeException e) {
                    if (failure == null) {
                        failure = Map.entry(context, e);
                    }
                }
            }
            if (failure != null) {
                fail(failure.getKey(), subscription, failure.getValue(), observer);
                return;
            }
            observer.onNext(gatheringObserver.firstResponse());
            observer.onCompleted();
        }

        @Override
        EventMessage unknownSubscriptionEvent(Subscription subscription) {
            return UnknownSubscriptionCancellationRequested.newBuilder()
                    .setId(subscription.getId())
                    .setActorContext(actorOf(subscription))
                    .build();
        }
    }

    /**
     * The Bounded Contexts that serve the targets of a topic.
     *
     * <p>A target is served by the context that serves its type. A target of an event type
     * that no context serves is served by every context.
     */
    private static final class TopicRouting {

        /**
         * The contexts that serve the types of targets, with the indexes of those targets.
         */
        private final SortedMap<BoundedContext, Set<Integer>> served = new TreeMap<>();

        /**
         * The indexes of the targets of event types that no context serves.
         */
        private final List<Integer> unservedEvents = new ArrayList<>();

        /**
         * The first entity type of a target that no context serves, if any.
         */
        private @Nullable TypeUrl unservedEntity;

        /**
         * Tells if the topic has no targets.
         */
        private final boolean noTargets;

        /**
         * Finds the contexts that serve the targets of the given topic.
         *
         * @param topic
         *         the topic whose target types are known to the server
         * @param find
         *         the function that finds the context serving a type
         */
        private TopicRouting(Topic topic, Function<TypeUrl, Optional<BoundedContext>> find) {
            var types = typesOf(topic);
            this.noTargets = types.isEmpty();
            for (var index = 0; index < types.size(); index++) {
                var type = types.get(index);
                var context = find.apply(type);
                if (context.isPresent()) {
                    served.computeIfAbsent(context.get(), c -> new TreeSet<>())
                          .add(index);
                } else if (topic.getTarget(index).ofEvent()) {
                    unservedEvents.add(index);
                } else if (unservedEntity == null) {
                    unservedEntity = type;
                }
            }
        }

        /**
         * Obtains the contexts that serve the types of targets, in their natural order.
         */
        private Set<BoundedContext> servingContexts() {
            return served.keySet();
        }

        /**
         * Obtains the first of the contexts that serve the types of targets, or {@code null}
         * if no context serves a type.
         */
        private @Nullable BoundedContext firstServingContext() {
            return served.isEmpty() ? null : served.firstKey();
        }

        /**
         * Tells if the topic has no targets, or a target of a type that no context serves.
         */
        private boolean anyUnserved() {
            return noTargets || unservedEntity != null || hasUnservedEvents();
        }

        /**
         * Tells if the topic has a target of an event type that no context serves.
         */
        private boolean hasUnservedEvents() {
            return !unservedEvents.isEmpty();
        }

        /**
         * Obtains the indexes of the targets of event types that no context serves.
         */
        private List<Integer> unservedEvents() {
            return unservedEvents;
        }

        /**
         * Obtains the first entity type of a target that no context serves.
         */
        private @Nullable TypeUrl unservedEntity() {
            return unservedEntity;
        }

        /**
         * Obtains the contexts that hold the subscription, with the indexes of
         * the targets that each of them serves.
         *
         * @param contexts
         *         all the contexts of the application, which serve the targets of
         *         the event types that no context serves
         */
        private SortedMap<BoundedContext, Set<Integer>> holders(Iterable<BoundedContext> contexts) {
            SortedMap<BoundedContext, Set<Integer>> result = new TreeMap<>();
            served.forEach((context, targets) -> result.put(context, new TreeSet<>(targets)));
            if (hasUnservedEvents()) {
                for (var context : contexts) {
                    result.computeIfAbsent(context, c -> new TreeSet<>())
                          .addAll(unservedEvents);
                }
            }
            return result;
        }
    }

    private static String contextsAsString(Iterable<BoundedContext> contexts) {
        return Joiner.on(", ")
                     .join(contexts);
    }

    /**
     * Obtains the types of the targets of the topic, in the order of the targets.
     */
    private static ImmutableList<TypeUrl> typesOf(Topic topic) {
        return topic.getTargetList()
                    .stream()
                    .map(Target::type)
                    .collect(toImmutableList());
    }

    /**
     * Obtains the indexes of all the targets of the topic.
     */
    private static Set<Integer> allTargetsOf(Topic topic) {
        return IntStream.range(0, topic.getTargetCount())
                        .boxed()
                        .collect(toImmutableSet());
    }

    /**
     * Cancels the subscription in the given Bounded Contexts.
     *
     * <p>A failure to cancel is logged, and does not stop cancelling the subscription in
     * other contexts.
     */
    private static void cancelIn(Iterable<BoundedContext> contexts,
                                 Subscription subscription,
                                 WithLogging logging) {
        for (var context : contexts) {
            try {
                context.stand()
                       .cancel(subscription, StreamObservers.noOpObserver());
            } catch (RuntimeException e) {
                logging.logger().atError().withCause(e).log(() -> format(
                        "Unable to cancel the subscription `%s` in the bounded context `%s`.",
                        subscription.getId().getValue(), context.name().getValue()));
            }
        }
    }
}
