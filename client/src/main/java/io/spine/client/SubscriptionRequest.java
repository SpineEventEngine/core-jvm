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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.spine.base.EntityState;
import io.spine.core.EmptyContext;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Allows subscribing to updates of entity states using filtering conditions.
 *
 * @param <S>
 *         the type of entity state to subscribe
 */
public final class SubscriptionRequest<S extends EntityState<?>>
        extends SubscribingRequest<S, EmptyContext, S, SubscriptionRequest<S>> {

    /**
     * The target of the request, which collects the consumers of entity states.
     */
    private final StateTarget<S> target;

    SubscriptionRequest(ClientRequest parent, Class<S> type) {
        super(parent, type);
        this.target = new StateTarget<>(type, builder());
    }

    /**
     * Configures the request to return results matching all the passed filters.
     */
    @CanIgnoreReturnValue
    public SubscriptionRequest<S> where(EntityStateFilter... filter) {
        target.where(filter);
        return self();
    }

    /**
     * Configures the request to return results matching all the passed filters.
     */
    @CanIgnoreReturnValue
    public SubscriptionRequest<S> where(CompositeEntityStateFilter... filter) {
        target.where(filter);
        return self();
    }

    /**
     * Adds a consumer observing the entities that previously matched the subscription criteria,
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
     * <p>It is the responsibility of the callee to provide a correct type of entity identifiers.
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
        target.whenNoLongerMatching(idType, consumer);
        return self();
    }

    @Override
    void addConsumer(Consumer<S> consumer) {
        target.observe(consumer);
    }

    @Override
    TargetDelivery delivery(@Nullable ConsumerErrorHandler<S> consumingErrorHandler) {
        return target.delivery(consumingErrorHandler);
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
