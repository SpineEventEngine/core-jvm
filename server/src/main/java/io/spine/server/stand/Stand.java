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
package io.spine.server.stand;

import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.grpc.stub.StreamObserver;
import io.spine.annotation.Internal;
import io.spine.annotation.VisibleForTesting;
import io.spine.client.EntityStateWithVersion;
import io.spine.client.Query;
import io.spine.client.QueryResponse;
import io.spine.client.Subscription;
import io.spine.client.SubscriptionId;
import io.spine.client.Topic;
import io.spine.core.Origin;
import io.spine.core.Response;
import io.spine.core.Responses;
import io.spine.server.Closeable;
import io.spine.server.EventProducer;
import io.spine.server.Identity;
import io.spine.server.bus.Listener;
import io.spine.server.entity.Entity;
import io.spine.server.entity.EntityLifecycle;
import io.spine.server.entity.EntityRecordChange;
import io.spine.server.entity.Repository;
import io.spine.server.entity.StorageConverter;
import io.spine.server.tenant.QueryOperation;
import io.spine.server.tenant.SubscriptionOperation;
import io.spine.server.tenant.TenantAwareOperation;
import io.spine.server.type.EventEnvelope;
import io.spine.system.server.event.EntityStateChanged;
import io.spine.type.TypeUrl;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.jspecify.annotations.Nullable;

import java.util.Collection;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.grpc.StreamObservers.ack;

/**
 * A bridge which connects {@link io.spine.server.QueryService QueryService} and
 * {@link io.spine.server.SubscriptionService SubscriptionService} with a Read-side of
 * a Bounded Context.
 *
 * <p>Each {@link io.spine.server.BoundedContext BoundedContext} contains only one
 * instance of {@code Stand}.
 *
 * <p>The {@code Stand} is responsible for obtaining results of
 * {@linkplain #execute(Query, StreamObserver) queries} sent by
 * the {@link io.spine.server.QueryService QueryService}.
 *
 * <p>The {@code Stand} also manages {@linkplain #subscribe(Topic, StreamObserver)
 * creation of subscriptions}, their
 * {@linkplain #activate(Subscription, SubscriptionCallback, StreamObserver) activation}, and
 * delivering updates to the subscribers when requested by
 * the {@linkplain io.spine.server.SubscriptionService SubscriptionService}.
 *
 * @see <a href="https://spine.io/docs/concepts/diagrams/spine-architecture-diagram-full-screen.html">
 *     Spine Architecture Diagram</a>
 */
@SuppressWarnings("OverlyCoupledClass")
public class Stand implements Closeable {

    /**
     * Used to return an empty result collection for {@link Query}.
     */
    private static final QueryProcessor NO_OP_PROCESSOR = new NoOpQueryProcessor();

    /**
     * Manages the subscriptions for this instance of {@code Stand}.
     *
     * <p>The registry is shared between a domain Bounded Context and its system counterpart.
     */
    private final SubscriptionRegistry subscriptionRegistry;

    /**
     * Manages the entity {@linkplain TypeUrl types} exposed via this instance of {@code Stand}.
     */
    private final TypeRegistry typeRegistry;

    /**
     * Manages the events produced by the associated repositories.
     */
    private final EventRegistry eventRegistry;

    private final boolean multitenant;

    private final TopicValidator topicValidator;
    private final QueryValidator queryValidator;
    private final SubscriptionValidator subscriptionValidator;

    private final EventTap eventTap;

    private Stand(Builder builder) {
        super();
        this.multitenant = builder.isMultitenant();
        this.subscriptionRegistry = builder.subscriptionRegistry();
        this.typeRegistry = builder.typeRegistry();
        this.eventRegistry = builder.eventRegistry();
        this.topicValidator = builder.topicValidator();
        this.queryValidator = builder.queryValidator();
        this.subscriptionValidator = builder.subscriptionValidator();
        this.eventTap = new EventTap(subscriptionRegistry);
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Test-only method that posts the state of an entity to this stand.
     *
     * @param entity
     *         the entity whose state to post
     * @param lifecycle
     *         the lifecycle of the entity
     * @implNote The only purpose of this method is to deliver the new entity state to the
     *         subscribers through the artificially created {@link EntityStateChanged} event.
     *         It doesn't do any proper lifecycle management ignoring "archived"/"deleted" actions,
     *         IDs of applied messages, etc.
     */
    @VisibleForTesting
    void post(Entity<?, ?> entity, EntityLifecycle lifecycle) {
        var record = StorageConverter.toEntityRecord(entity)
                                     .build();
        var change = EntityRecordChange.newBuilder()
                .setNewValue(record)
                .build();
        var origin = Identity.byString("Stand-received-entity-update");
        lifecycle.onStateChanged(change,
                                 ImmutableSet.of(origin),
                                 Origin.getDefaultInstance());
    }

    @Internal
    @VisibleForTesting
    public boolean isMultitenant() {
        return multitenant;
    }

    /**
     * Creates a subscription for the passed topic.
     *
     * @param topic
     *         defines the subscription target
     * @param responseObserver
     *         the observer for obtaining created subscription
     */
    public void subscribe(Topic topic, StreamObserver<Subscription> responseObserver)
            throws InvalidRequestException {
        topicValidator.validate(topic);

        var tenantId = topic.getContext()
                            .getTenantId();
        var op = new TenantAwareOperation(tenantId) {
            @Override
            public void run() {
                var subscription = subscriptionRegistry.add(topic);
                responseObserver.onNext(subscription);
                responseObserver.onCompleted();
            }
        };
        op.execute();
    }

    /**
     * Registers this inactive subscription is this {@code Stand}.
     *
     * <p>The given subscription is shared between different Bounded Contexts.
     *
     * @param subscription
     *         an existing subscription
     */
    public void subscribe(Subscription subscription) throws InvalidRequestException {
        var topic = subscription.getTopic();
        topicValidator.validate(topic);

        var tenantId = topic.getContext()
                            .getTenantId();
        var op = new TenantAwareOperation(tenantId) {
            @Override
            public void run() {
                subscriptionRegistry.add(subscription);
            }
        };
        op.execute();
    }

    /**
     * Activates the subscription created via {@link #subscribe(Topic, StreamObserver)
     * subscribe()} method.
     *
     * <p>After the activation, the clients will start receiving the updates via the passed
     * {@code SubscriptionCallback}.
     *
     * @param subscription
     *         the subscription to activate
     * @param callback
     *         the action which notifies the subscribers about an update
     * @param responseObserver
     *         an observer to notify of a successful acknowledgement of the subscription activation
     * @see #subscribe(Topic, StreamObserver)
     */
    public void activate(Subscription subscription,
                         SubscriptionCallback callback,
                         StreamObserver<Response> responseObserver)
            throws InvalidRequestException {
        checkNotNull(subscription);
        checkNotNull(callback);

        subscriptionValidator.validate(subscription);
        var op = new SubscriptionOperation(subscription) {
            @Override
            public void run() {
                subscriptionRegistry.activate(subscription, callback);
                ack(responseObserver);
            }
        };
        op.execute();
    }

    /**
     * Cancels the {@link Subscription}.
     *
     * <p>Typically invoked to cancel the previous
     * {@link #activate(Subscription, SubscriptionCallback, StreamObserver) activate()} call.
     *
     * <p>After this method is called, the subscribers stop receiving the updates,
     * related to the given {@code Subscription}.
     *
     * @param subscription
     *         the subscription to cancel
     * @param responseObserver
     *         an observer to notify of a successful acknowledgement of the subscription
     *         cancellation.
     */
    public void cancel(Subscription subscription, StreamObserver<Response> responseObserver)
            throws InvalidRequestException {
        subscriptionValidator.validate(subscription);

        var op = new SubscriptionOperation(subscription) {
            @Override
            public void run() {
                if(subscriptionRegistry.containsId(subscription.getId())) {
                    subscriptionRegistry.remove(subscription);
                    ack(responseObserver);
                }
            }
        };
        op.execute();
    }

    /**
     * Obtains the bus listener which propagates all events and state updates to subscriptions.
     */
    public Listener<EventEnvelope> eventListener() {
        return eventTap;
    }

    /**
     * Reads all {@link Entity} types exposed for reading by this instance of {@code Stand}.
     *
     * <p>In order to expose the type, use {@link Stand#registerTypeSupplier(Repository)}.
     *
     * <p>The result includes all values from {@link #exposedAggregateTypes()} as well.
     *
     * @return the set of types as {@link TypeUrl} instances
     */
    public ImmutableSet<TypeUrl> exposedTypes() {
        return typeRegistry.allTypes();
    }

    /**
     * Reads all event types produced by repositories and event producers
     * associated with this {@code Stand}.
     *
     * @return the set of types as {@link TypeUrl} instances
     */
    public ImmutableSet<TypeUrl> exposedEventTypes() {
        return eventRegistry.typeSet();
    }

    /**
     * Reads all {@link io.spine.server.aggregate.Aggregate Aggregate} entity types
     * exposed for reading by this instance of {@code Stand}.
     *
     * <p>Use {@link Stand#registerTypeSupplier(Repository)} to expose an {@code Aggregate} type.
     *
     * @return the set of types as {@link TypeUrl} instances
     */
    public ImmutableSet<TypeUrl> exposedAggregateTypes() {
        return typeRegistry.aggregateTypes();
    }

    /**
     * Reads a particular set of items from the read-side of the application and
     * feed the result into an instance.
     *
     * <p>{@link Query} defines the query target and the expected detail level for response.
     *
     * <p>The query results are fed to an instance
     * of {@link StreamObserver}&lt;{@link QueryResponse}&gt;.
     *
     * @param query
     *         the instance of query
     * @param responseObserver
     *         the observer to feed the query results to
     */
    public void execute(Query query, StreamObserver<QueryResponse> responseObserver)
            throws InvalidRequestException {
        queryValidator.validate(query);

        var type = query.targetType();
        var queryProcessor = processorFor(type);

        var op = new QueryOperation(query) {
            @Override
            public void run() {
                Collection<EntityStateWithVersion> readResult = queryProcessor.process(query());
                var response = QueryResponse.newBuilder()
                        .addAllMessage(readResult)
                        .setResponse(Responses.ok())
                        .build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();
            }
        };
        op.execute();
    }

    /**
     * Registers the passed {@code Repository} as an entity/event type supplier.
     */
    public void registerTypeSupplier(Repository<?, ?> repository) {
        typeRegistry.register(repository);
        eventRegistry.register(repository);
    }

    /**
     * Registers the passed {@code EventProducer} as the event type supplier.
     */
    public void registerTypeSupplier(EventProducer producer) {
        eventRegistry.register(producer);
    }

    @Override
    public boolean isOpen() {
        return typeRegistry.isOpen();
    }

    /**
     * Closes the {@code Stand} performing necessary cleanups.
     */
    @Override
    public void close() {
        typeRegistry.close();
        eventRegistry.close();
    }

    /**
     * Factory method which determines a proper {@link QueryProcessor} implementation
     * depending on {@link TypeUrl} of the incoming {@link Query#getTarget()}.
     *
     * <p>As {@code Stand} accumulates the read-side updates from various repositories,
     * the {@code Query} processing varies a lot. The target type of the incoming {@code Query}
     * tells the {@code Stand} about the essence of the object queried. Thus making it possible
     * to pick a proper strategy for data fetch.
     *
     * @param type
     *         the target type of the {@code Query}
     * @return suitable implementation of {@code QueryProcessor}
     */
    private QueryProcessor processorFor(TypeUrl type) {
        var maybeRepo = typeRegistry.recordRepositoryOf(type);
        if (maybeRepo.isPresent()) {
            var recordRepo = maybeRepo.get();
            return new EntityQueryProcessor(recordRepo);
        }
        return NO_OP_PROCESSOR;
    }

    /**
     * Tells whether this context has a subscription with the given ID.
     */
    @Internal
    public boolean hasSubscription(SubscriptionId id) {
        return subscriptionRegistry.containsId(id);
    }

    public static class Builder {

        /**
         * The multi-tenancy flag for the {@code Stand} to build.
         *
         * <p>The value of this field should be equal to that of corresponding
         * {@linkplain io.spine.server.BoundedContextBuilder BoundedContextBuilder} and is not
         * supposed to be {@linkplain #setMultitenant(boolean) set directly}.
         *
         * <p>If set directly, the value would be matched to the multi-tenancy flag of aggregating
         * {@code BoundedContext}.
         */
        private @Nullable Boolean multitenant;

        private final TypeRegistry typeRegistry = InMemoryTypeRegistry.newInstance();
        private final EventRegistry eventRegistry = InMemoryEventRegistry.newInstance();

        private @MonotonicNonNull SubscriptionRegistry subscriptionRegistry;
        private @MonotonicNonNull TopicValidator topicValidator;
        private @MonotonicNonNull QueryValidator queryValidator;
        private @MonotonicNonNull SubscriptionValidator subscriptionValidator;

        @CanIgnoreReturnValue
        @Internal
        public Builder setMultitenant(boolean multitenant) {
            this.multitenant = multitenant;
            return this;
        }

        @CanIgnoreReturnValue
        @Internal
        public Builder withSubscriptionRegistryFrom(Stand other) {
            checkNotNull(other);
            this.subscriptionRegistry = checkNotNull(other.subscriptionRegistry);
            return this;
        }

        private boolean isMultitenant() {
            return this.multitenant != null && this.multitenant;
        }

        private SubscriptionRegistry subscriptionRegistry() {
            return subscriptionRegistry;
        }

        private TopicValidator topicValidator() {
            return topicValidator;
        }

        private QueryValidator queryValidator() {
            return queryValidator;
        }

        private SubscriptionValidator subscriptionValidator() {
            return subscriptionValidator;
        }

        private TypeRegistry typeRegistry() {
            return typeRegistry;
        }

        private EventRegistry eventRegistry() {
            return eventRegistry;
        }

        /**
         * Builds an instance of {@code Stand}.
         *
         * <p>This method is supposed to be called internally when building aggregating
         * {@code BoundedContext}.
         *
         * @return a new instance of {@code Stand}
         */
        @Internal
        public Stand build() {
            if (subscriptionRegistry == null) {
                subscriptionRegistry = MultitenantSubscriptionRegistry.newInstance(isMultitenant());
            }
            topicValidator = new TopicValidator(typeRegistry);
            queryValidator = new QueryValidator(typeRegistry);
            subscriptionValidator = new SubscriptionValidator(subscriptionRegistry);

            var result = new Stand(this);
            return result;
        }
    }
}
