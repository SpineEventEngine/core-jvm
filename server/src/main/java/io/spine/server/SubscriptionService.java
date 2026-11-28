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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
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
 * <p>A target of an entity type is served by the Bounded Context that has the entities of
 * the type. A target of an event type is served by every Bounded Context of the application
 * whose tenancy fits the topic: by the multi-tenant contexts for a topic with a tenant, and by
 * the single-tenant contexts for a topic without one. Each such context streams the events
 * of the type posted in it. A topic with a target of an event type is refused if no context
 * fits it.
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
                context -> dictionary.putAll(context, c -> c.stand().exposedTypes())
        );
        return new SubscriptionService(dictionary.build(), application);
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
                    StreamObserver<Response> responseObserver = forwardErrorsOnly(safeObserver);
                    activation.serve(subscription, responseObserver, callback);
                },
                new GuardedObserver<>(observer), "Error activating the subscription."
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
         *
         * <p>A topic is served by one context only if all its targets are of entity types,
         * and that context serves them all.
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
         * <p>A target of an entity type is served by the context that serves the type.
         * A topic with a target of an entity type that no context serves is
         * {@linkplain #refuseNotServed(Object, TypeUrl, StreamObserver) refused}. A target of
         * an event type is served by each Bounded Context of the application whose tenancy
         * fits the topic. If no context fits, the topic is
         * {@linkplain #refuseMisfit(Object, StreamObserver) refused} with
         * {@link io.spine.core.ErrorCase#TENANT_ERROR TENANT_ERROR}.
         *
         * <p>The tenant of the topic must fit each context that serves the entity type of
         * a target. If the topic has a target of an event type, or a type that no context
         * serves, a topic with a tenant is also refused unless the tenant is registered in
         * the application.
         *
         * <p>A topic that violates the constraints of its Protobuf type, for example, one
         * without an ID, is refused with
         * {@link io.spine.core.ErrorCase#INVALID_REQUEST INVALID_REQUEST}. The constraints are
         * checked after the tenancy, so a topic that the tenancy checks above refuse gets
         * {@code TENANT_ERROR}, even if it also violates the constraints.
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
            var fitting = ImmutableSortedSet.<BoundedContext>of();
            if (routing.hasEvents()) {
                fitting = fittingContexts(topic);
                if (fitting.isEmpty()) {
                    refuseMisfit(topic, observer);
                    return;
                }
            }
            if (routing.anyNotServedByOne() && !admittedToAll(topic, observer)) {
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
            subscribeIn(routing.holders(fitting), topic, observer);
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
         * Serves the request in those Bounded Contexts of the application that have
         * the subscription.
         *
         * <p>The topic has a target of an event type, or the entity types of its targets are
         * served by different Bounded Contexts, or some of them by none. Upon subscribing, the
         * subscription
         * {@linkplain SubscriptionImpl#serveNoContext(Topic, StreamObserver, Object) was created}
         * in each context that serves an entity type of a target and, for the targets of event
         * types, in each context whose tenancy fits the topic. The contexts may still differ in
         * which of them hold the subscription, for example, when a subscription is created in
         * one context directly.
         *
         * <p>The request is refused with
         * {@link io.spine.core.ErrorCase#TENANT_ERROR TENANT_ERROR} if its tenant does not fit
         * a context that serves the entity type of a target, or if it has a target of an event
         * type and its tenancy fits no context. If the topic has a target of an event type, or
         * of a type that no context serves, a subscription whose topic has a tenant is also
         * refused unless the tenant is registered in the application.
         *
         * <p>A subscription that violates the constraints of its Protobuf type, for example,
         * one without an ID, is refused with
         * {@link io.spine.core.ErrorCase#INVALID_REQUEST INVALID_REQUEST}. The constraints are
         * checked after the tenancy, so a subscription that the tenancy checks above refuse
         * gets {@code TENANT_ERROR}, even if it also violates the constraints.
         *
         * <p>If no context has the subscription, the request is refused with
         * {@link io.spine.core.ErrorCase#UNSUPPORTED_REQUEST UNSUPPORTED_REQUEST}. A context
         * that serves an entity type of a target records the request. If there is no such
         * context, the request is {@linkplain #refuseNotServed(Object, StreamObserver) refused}
         * without recording it in a context.
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
            var routing = new TopicRouting(subscription.getTopic(), this::find);
            var served = routing.servingContexts();
            for (var context : served) {
                if (!admitted(context, subscription, observer)) {
                    return;
                }
            }
            if (routing.hasEvents() && fittingContexts(subscription).isEmpty()) {
                refuseMisfit(subscription, observer);
                return;
            }
            if (routing.anyNotServedByOne() && !admittedToAll(subscription, observer)) {
                return;
            }
            serveHolders(subscription, served, observer, params);
        }

        /**
         * Serves the request in the Bounded Contexts that have the subscription, finding them
         * by the ID of the subscription.
         *
         * @param served
         *         the contexts that serve the entity types of the topic targets, which have
         *         admitted the tenant of the subscription; they record the problems with
         *         the request
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
         * Tells whether the request is served only if each context that serves an entity type
         * of the topic targets has the subscription.
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
         * the entity types of its targets would miss the updates of the other targets.
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
     * <p>A target of an entity type is served by the context that serves the type. A target of
     * an event type is served by every context whose tenancy fits the topic.
     */
    private static final class TopicRouting {

        /**
         * The contexts that serve the entity types of targets, with the indexes of
         * those targets.
         */
        private final SortedMap<BoundedContext, Set<Integer>> served = new TreeMap<>();

        /**
         * The indexes of the targets of event types.
         */
        private final List<Integer> events = new ArrayList<>();

        /**
         * The first entity type of a target that no context serves, if any.
         */
        private @Nullable TypeUrl unservedEntity;

        /**
         * Tells if the topic has no targets.
         */
        private final boolean noTargets;

        /**
         * Finds the contexts that serve the targets of entity types of the given topic,
         * and the targets of event types.
         *
         * @param topic
         *         the topic whose target types are known to the server
         * @param find
         *         the function that finds the context serving an entity type
         */
        private TopicRouting(Topic topic, Function<TypeUrl, Optional<BoundedContext>> find) {
            var types = typesOf(topic);
            this.noTargets = types.isEmpty();
            for (var index = 0; index < types.size(); index++) {
                if (topic.getTarget(index).ofEvent()) {
                    events.add(index);
                    continue;
                }
                var type = types.get(index);
                var context = find.apply(type);
                if (context.isPresent()) {
                    served.computeIfAbsent(context.get(), c -> new TreeSet<>())
                          .add(index);
                } else if (unservedEntity == null) {
                    unservedEntity = type;
                }
            }
        }

        /**
         * Obtains the contexts that serve the entity types of targets, in their
         * natural order.
         */
        private ImmutableSortedSet<BoundedContext> servingContexts() {
            return ImmutableSortedSet.copyOf(served.keySet());
        }

        /**
         * Obtains the first of the contexts that serve the entity types of targets,
         * or {@code null} if no context serves such a type.
         */
        private @Nullable BoundedContext firstServingContext() {
            return served.isEmpty() ? null : served.firstKey();
        }

        /**
         * Tells if the topic has a target of an event type.
         */
        private boolean hasEvents() {
            return !events.isEmpty();
        }

        /**
         * Tells if the topic has no targets, or a target that no single context serves:
         * a target of an event type, or of an entity type that no context serves.
         */
        private boolean anyNotServedByOne() {
            return noTargets || unservedEntity != null || hasEvents();
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
         * @param fitting
         *         the contexts whose tenancy fits the topic, which serve the targets of
         *         event types
         */
        private SortedMap<BoundedContext, Set<Integer>> holders(Iterable<BoundedContext> fitting) {
            SortedMap<BoundedContext, Set<Integer>> result = new TreeMap<>();
            served.forEach((context, targets) -> result.put(context, new TreeSet<>(targets)));
            if (hasEvents()) {
                for (var context : fitting) {
                    result.computeIfAbsent(context, c -> new TreeSet<>())
                          .addAll(events);
                }
            }
            return result;
        }
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
