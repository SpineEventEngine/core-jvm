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

package io.spine.client

import com.google.errorprone.annotations.CanIgnoreReturnValue
import com.google.protobuf.Message
import io.spine.base.EntityState
import io.spine.base.EventMessage
import java.util.function.Consumer

/**
 * Allows subscribing to several targets at once: events and entity states,
 * of one or more types.
 *
 * The request makes one subscription. The updates of all its targets come through
 * this subscription, and one [cancellation][Subscriptions.cancel] stops them all.
 *
 * Each call of [event] or [state] adds one target, so the targets come in the order of
 * the calls. One type may appear in several targets, for example, with different filters.
 * A message that matches several targets goes to the consumers of each of them.
 *
 * In Kotlin:
 * ```kotlin
 * val subscription = client.onBehalfOf(user).subscribe {
 *     event<TaskAssigned> { it.observe { e -> show(e) } }
 *     state<Task> {
 *         it.byId(taskId)
 *             .observe { task -> render(task) }
 *             .whenNoLongerMatching(TaskId::class.java) { id -> hide(id) }
 *     }
 * }
 * ```
 *
 * In Java:
 * ```java
 * Subscription subscription = client.onBehalfOf(user)
 *         .subscribe()
 *         .event(TaskAssigned.class, t -> t.observe(this::show))
 *         .state(Task.class, t -> t.byId(taskId)
 *                                  .observe(this::render)
 *                                  .whenNoLongerMatching(TaskId.class, this::hide))
 *         .post();
 * ```
 *
 * @see ClientRequest.subscribe
 */
public class TopicRequest internal constructor(parent: ClientRequest) : ClientRequestBase(parent) {

    /**
     * The factory of the topic of the request.
     */
    private val topics: TopicFactory = client().requestOf(user()).topic()

    /**
     * Builds the deliveries of the targets, in the order of the targets.
     */
    private val targets = mutableListOf<() -> TargetDelivery>()

    /**
     * The handler of errors thrown by the consumers of all the targets.
     */
    private var consumingErrors: ConsumerErrorHandler<Message>? = null

    /**
     * Adds a target of the events of the given type.
     *
     * @param E The type of the events.
     * @param type The type of the events.
     * @param configure Configures the target: the events that match it, and their consumers.
     *   The target must have at least one consumer.
     * @return This request, for call chaining.
     * @throws io.spine.type.UnpublishedLanguageException If the type is not a part of
     *   the published language.
     */
    @CanIgnoreReturnValue
    public fun <E : EventMessage> event(
        type: Class<E>,
        configure: Consumer<EventTarget<E>>
    ): TopicRequest {
        val target = EventTarget(type, topics.select(type))
        configure.accept(target)
        targets += { target.delivery(consumingErrors?.narrow()) }
        return this
    }

    /**
     * Adds a target of the states of the entities of the given type.
     *
     * @param S The type of the entity states.
     * @param type The type of the entity states.
     * @param configure Configures the target: the entities that match it, and the consumers
     *   of their states. The target must have at least one consumer of states.
     * @return This request, for call chaining.
     * @throws io.spine.type.UnpublishedLanguageException If the type is not a part of
     *   the published language.
     */
    @CanIgnoreReturnValue
    public fun <S : EntityState<*>> state(
        type: Class<S>,
        configure: Consumer<StateTarget<S>>
    ): TopicRequest {
        val target = StateTarget(type, topics.select(type))
        configure.accept(target)
        targets += { target.delivery(consumingErrors?.narrow()) }
        return this
    }

    /**
     * Assigns a handler for the error of the stream of updates.
     *
     * Once this handler is called, no more updates come to the consumers of any target.
     * If not specified, the error is logged.
     *
     * @return This request, for call chaining.
     * @see onConsumingError
     */
    @CanIgnoreReturnValue
    override fun onStreamingError(handler: ErrorHandler): TopicRequest {
        super.onStreamingError(handler)
        return this
    }

    /**
     * Assigns a handler for an error that may occur in the code of a consumer of any target.
     *
     * After this handler is called, the remaining consumers get the message as usual.
     * If not specified, the error is logged.
     *
     * @return This request, for call chaining.
     * @see onStreamingError
     */
    @CanIgnoreReturnValue
    public fun onConsumingError(handler: ConsumerErrorHandler<Message>): TopicRequest {
        consumingErrors = handler
        return this
    }

    /**
     * Subscribes to the targets of the request.
     *
     * The subscription should be [cancelled][Subscriptions.cancel] when the client
     * no longer needs its updates.
     *
     * @return The subscription to all the targets of the request.
     * @throws IllegalStateException If the request has no targets, or a target has
     *   no consumers.
     * @throws io.grpc.StatusRuntimeException If the server refuses the subscription.
     */
    public fun post(): Subscription {
        check(targets.isNotEmpty()) {
            "The subscription has no targets. Please call `event()` or `state()` before `post()`."
        }
        val deliveries = targets.map { it() }
        return client().subscriptions()
            .subscribeTo(topics, deliveries, streamingErrorHandler())
    }
}

/**
 * Adapts this handler of errors of all the consumers of a request to the consumers of
 * the messages of one type.
 */
internal fun <M : Message> ConsumerErrorHandler<Message>.narrow(): ConsumerErrorHandler<M> =
    ConsumerErrorHandler { consumer, error ->
        @Suppress("UNCHECKED_CAST") // The handler accepts the consumers of any messages.
        accept(consumer as MessageConsumer<Message, *>, error)
    }
