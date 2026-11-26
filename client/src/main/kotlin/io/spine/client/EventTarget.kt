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
import io.spine.base.EventMessage
import io.spine.client.Filters.extractFilters
import io.spine.type.requirePublished
import java.util.function.Consumer

/**
 * Configures one target of events in a subscription: the events that match it,
 * and the consumers of these events.
 *
 * A subscription may have several targets. The events that match a target go to
 * the consumers of this target.
 *
 * @param E The type of the events.
 * @see TopicRequest.event
 */
public class EventTarget<E : EventMessage> internal constructor(
    private val type: Class<E>,
    private val builder: TopicBuilder
) {

    private val consumers = EventConsumers.newBuilder<E>()
    private var observed = false

    init {
        requirePublished(type)
    }

    /**
     * Makes the target match only the events that pass all the given filters.
     *
     * The filters may refer to the fields of the event message and of the event context.
     * See [EventFilter] for details.
     *
     * Replaces the filters given before.
     */
    @CanIgnoreReturnValue
    @Suppress("SpreadOperator") // The Java builder takes varargs; there are few filters.
    public fun where(vararg filter: EventFilter): EventTarget<E> = apply {
        builder.where(*extractFilters(filter))
    }

    /**
     * Makes the target match only the events that pass all the given filters.
     *
     * The filters may refer to the fields of the event message and of the event context.
     * See [CompositeEventFilter] for details.
     *
     * Replaces the filters given before.
     */
    @CanIgnoreReturnValue
    @Suppress("SpreadOperator") // The Java builder takes varargs; there are few filters.
    public fun where(vararg filter: CompositeEventFilter): EventTarget<E> = apply {
        builder.where(*extractFilters(filter))
    }

    /**
     * Makes the target match only the events with the given IDs.
     *
     * If there are no IDs, the target matches events whatever their IDs.
     */
    @CanIgnoreReturnValue
    public fun byId(ids: Iterable<*>): EventTarget<E> = apply {
        builder.byId(ids)
    }

    /**
     * Makes the target match only the events with the given IDs.
     */
    @CanIgnoreReturnValue
    public fun byId(vararg ids: Message): EventTarget<E> = byId(ids.asList())

    /**
     * Makes the target match only the events with the given IDs.
     */
    @CanIgnoreReturnValue
    public fun byId(vararg ids: String): EventTarget<E> = byId(ids.asList())

    /**
     * Makes the target match only the events with the given IDs.
     */
    @CanIgnoreReturnValue
    public fun byId(vararg ids: Int): EventTarget<E> = byId(ids.asList())

    /**
     * Makes the target match only the events with the given IDs.
     */
    @CanIgnoreReturnValue
    public fun byId(vararg ids: Long): EventTarget<E> = byId(ids.asList())

    /**
     * Adds a consumer of the messages of the events that match the target.
     */
    @CanIgnoreReturnValue
    public fun observe(consumer: Consumer<E>): EventTarget<E> =
        observe(EventConsumer.from(consumer))

    /**
     * Adds a consumer of the messages and the contexts of the events that match the target.
     */
    @CanIgnoreReturnValue
    public fun observe(consumer: EventConsumer<E>): EventTarget<E> = apply {
        consumers.add(consumer)
        observed = true
    }

    /**
     * Builds the target of the topic, and the delivery of its events to the consumers.
     *
     * @param consumingErrors The handler of errors thrown by the consumers,
     *   or `null` to log such errors.
     * @throws IllegalStateException If no consumer was added.
     * @throws IllegalArgumentException If the filters of the target are invalid.
     */
    @JvmName("delivery")
    internal fun delivery(consumingErrors: ConsumerErrorHandler<E>?): TargetDelivery {
        check(observed) {
            "The target of the type `${type.name}` has no consumers." +
                    " Please call `observe()` for the target."
        }
        consumingErrors?.let { consumers.onConsumingError(it) }
        val target = builder.buildTarget()
        target.checkValid()
        return EventDelivery(target, consumers.build().toObserver())
    }
}
