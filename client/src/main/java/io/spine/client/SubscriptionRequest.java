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
import io.grpc.stub.StreamObserver;
import io.spine.base.EntityState;
import io.spine.core.EmptyContext;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.spine.client.Filters.extractFilters;

/**
 * Allows subscribing to updates of entity states using filtering conditions.
 *
 * @param <S>
 *         the type of entity state to subscribe
 */
public final class SubscriptionRequest<S extends EntityState<?>>
        extends SubscribingRequest<S, EmptyContext, S, SubscriptionRequest<S>> {

    /**
     * The builder of consumers of entity state.
     */
    private final StateConsumers.Builder<S> consumers;

    /**
     * Optional consumer of entity IDs, which no longer match the subscription criteria.
     */
    private @Nullable NoLongerMatchingConsumer<?> nlmConsumer;

    SubscriptionRequest(ClientRequest parent, Class<S> type) {
        super(parent, type);
        this.consumers = StateConsumers.newBuilder();
    }

    /**
     * Configures the request to return results matching all the passed filters.
     */
    @CanIgnoreReturnValue
    public SubscriptionRequest<S> where(EntityStateFilter... filter) {
        builder().where(extractFilters(filter));
        return self();
    }

    /**
     * Configures the request to return results matching all the passed filters.
     */
    @CanIgnoreReturnValue
    public SubscriptionRequest<S> where(CompositeEntityStateFilter... filter) {
        builder().where(extractFilters(filter));
        return self();
    }

    /**
     * Adds a consumer observing the entities which previously matched the subscription criteria,
     * but stopped to do so.
     *
     * <p>The consumer is fed with the ID of the entity in one of the following cases:
     *
     * <ul>
     *     <li>the entity no longer passes the subscription filter because
     *         one of its fields changed;
     *     <li>the entity has been deleted;
     *     <li>the entity has been archived.</li>
     * </ul>
     *
     * <p>It is the responsibility of callee to provide a correct type of entity identifiers.
     *
     * @param consumer
     *         the consumer to notify
     * @param idType
     *         the type of entity identifiers
     * @param <I>
     *         type of entity identifiers, for covariance
     * @return this instance of {@code SubscriptionRequest}, for call chaining
     */
    public <I> SubscriptionRequest<S> whenNoLongerMatching(Class<I> idType, Consumer<I> consumer) {
        checkNotNull(idType);
        checkNotNull(consumer);
        nlmConsumer = new NoLongerMatchingConsumer<>(idType, consumer);
        return self();
    }

    @Override
    protected Optional<StreamObserver<SubscriptionUpdate>> chain() {
        if (null == nlmConsumer) {
            return Optional.empty();
        }
        return Optional.of(new NoLongerMatchingFilter(nlmConsumer));
    }

    @Override
    StateConsumers.Builder<S> consumers() {
        return consumers;
    }

    @Override
    StateConsumer<S> toMessageConsumer(Consumer<S> consumer) {
        return StateConsumer.from(consumer);
    }

    @Override
    SubscriptionRequest<S> self() {
        return this;
    }

    @Override
    Function<ActorRequestFactory, TopicBuilder> builderFn() {
        return (factory) -> factory.topic().select(messageType());
    }
}
