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
import io.spine.base.EventMessage;
import io.spine.core.Event;
import io.spine.core.EventContext;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Allows subscribing to events using filtering conditions.
 *
 * <p>Similarly to subscriptions to entity states, event subscriptions may use filtering by
 * values of the proto types of subscribed messages:
 * <pre>{@code
 * clientRequest.subscribeToEvent(MyEventMessage.class)
 *              .where(eq(MyEventMessage.Field.myProtoField(), fieldValue))
 *              .observe((event, context) -> {...})
 *              .post();
 * }</pre>
 *
 * <p>In addition to regular filtering conditions, event subscription requests may also reference
 * fields of {@code spine.core.EventContext}. For example, in order to filter events that originate
 * from commands of the given user, please use the following code:
 * <pre>{@code
 * clientRequest.subscribeToEvent(MyEventMessage.class)
 *              .where(eq(EventContext.Field.pastMessage().actorContext().actor(), userId))
 *              .observe((event, context) -> {...})
 *              .post();
 * }</pre>
 *
 * @param <E>
 *         the type of the event messages
 */
public final class EventSubscriptionRequest<E extends EventMessage>
        extends SubscribingRequest<E, EventContext, Event, EventSubscriptionRequest<E>> {

    /**
     * The target of the request, which collects the consumers of events.
     */
    private final EventTarget<E> target;

    EventSubscriptionRequest(ClientRequest parent, Class<E> type) {
        super(parent, type);
        this.target = new EventTarget<>(type, builder());
    }

    /**
     * Configures the request to return results matching all the passed filters.
     *
     * <p>Please note that the {@link EventFilter} instances may target both event message and
     * event context fields. See {@link EventFilter} for details.
     */
    @CanIgnoreReturnValue
    public EventSubscriptionRequest<E> where(EventFilter... filter) {
        target.where(filter);
        return self();
    }

    /**
     * Configures the request to return results matching all the passed filters.
     *
     * <p>Please note that the {@link CompositeEventFilter} instances may target both event message
     * and event context fields. See {@link CompositeEventFilter} for details.
     */
    @CanIgnoreReturnValue
    public EventSubscriptionRequest<E> where(CompositeEventFilter... filter) {
        target.where(filter);
        return self();
    }

    /**
     * Subscribes the passed consumer to receive the event messages and their contexts.
     */
    @CanIgnoreReturnValue
    public EventSubscriptionRequest<E> observe(EventConsumer<E> consumer) {
        checkNotNull(consumer);
        target.observe(consumer);
        return self();
    }

    @Override
    void addConsumer(Consumer<E> consumer) {
        target.observe(consumer);
    }

    @Override
    TargetObserver toObserver(@Nullable ConsumerErrorHandler<E> consumingErrorHandler) {
        return target.toObserver(consumingErrorHandler);
    }

    @Override
    Function<ActorRequestFactory, TopicBuilder> builderFn() {
        return (factory) -> factory.topic().select(messageType());
    }

    @Override
    EventSubscriptionRequest<E> self() {
        return this;
    }
}
