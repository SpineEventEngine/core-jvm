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
import io.spine.client.Filters.extractFilters
import io.spine.type.requirePublished
import java.util.function.Consumer

/**
 * Configures one target of entity states in a subscription: the entities that match it,
 * and the consumers of their states.
 *
 * A subscription may have several targets, and one entity type may appear in
 * several of them, for example, with different filters. The states that match a target
 * go to the consumers of this target.
 *
 * @param S The type of the entity states.
 * @see TopicRequest.state
 */
public class StateTarget<S : EntityState<*>> internal constructor(
    private val type: Class<S>,
    private val builder: TopicBuilder
) {

    private val consumers = StateConsumers.newBuilder<S>()
    private val noLongerMatching = mutableListOf<NoLongerMatchingConsumer<*>>()
    private var observed = false

    init {
        requirePublished(type)
    }

    /**
     * Makes the target match only the entities whose states pass all the given filters.
     *
     * Replaces the filters given before.
     */
    @CanIgnoreReturnValue
    @Suppress("SpreadOperator") // The Java builder takes varargs; there are few filters.
    public fun where(vararg filter: EntityStateFilter): StateTarget<S> = apply {
        builder.where(*extractFilters(filter))
    }

    /**
     * Makes the target match only the entities whose states pass all the given filters.
     *
     * Replaces the filters given before.
     */
    @CanIgnoreReturnValue
    @Suppress("SpreadOperator") // The Java builder takes varargs; there are few filters.
    public fun where(vararg filter: CompositeEntityStateFilter): StateTarget<S> = apply {
        builder.where(*extractFilters(filter))
    }

    /**
     * Makes the target match only the entities with the given IDs.
     *
     * The IDs must be of the ID type of the entities.
     *
     * If there are no IDs, the target matches entities whatever their IDs.
     */
    @CanIgnoreReturnValue
    public fun byId(ids: Iterable<*>): StateTarget<S> = apply {
        builder.byId(ids)
    }

    /**
     * Makes the target match only the entities with the given IDs.
     */
    @CanIgnoreReturnValue
    public fun byId(vararg ids: Message): StateTarget<S> = byId(ids.asList())

    /**
     * Makes the target match only the entities with the given IDs.
     */
    @CanIgnoreReturnValue
    public fun byId(vararg ids: String): StateTarget<S> = byId(ids.asList())

    /**
     * Makes the target match only the entities with the given IDs.
     */
    @CanIgnoreReturnValue
    public fun byId(vararg ids: Int): StateTarget<S> = byId(ids.asList())

    /**
     * Makes the target match only the entities with the given IDs.
     */
    @CanIgnoreReturnValue
    public fun byId(vararg ids: Long): StateTarget<S> = byId(ids.asList())

    /**
     * Adds a consumer of the entity states that match the target.
     */
    @CanIgnoreReturnValue
    public fun observe(consumer: Consumer<S>): StateTarget<S> = apply {
        consumers.add(StateConsumer.from(consumer))
        observed = true
    }

    /**
     * Adds a consumer of the IDs of the entities that matched the target, and then
     * stopped matching it.
     *
     * The consumer gets the ID of an entity when:
     *  - the entity no longer passes the filters of the target, because its state changed;
     *  - the entity is archived;
     *  - the entity is deleted.
     *
     * The entity may still match another target of the subscription.
     *
     * If the consumer throws, the error is logged, and the updates of the subscription
     * go on.
     *
     * @param I The type of the entity IDs.
     * @param idType The class of the entity IDs. It must be the ID type of the entities.
     * @param consumer The consumer of the IDs.
     */
    @CanIgnoreReturnValue
    public fun <I : Any> whenNoLongerMatching(
        idType: Class<I>,
        consumer: Consumer<I>
    ): StateTarget<S> = apply {
        noLongerMatching += NoLongerMatchingConsumer(idType, consumer)
    }

    /**
     * Builds the observer of the target: the target of the topic, with the consumers
     * of its states.
     *
     * @param consumingErrors The handler of errors thrown by the consumers of states,
     *   or `null` to log such errors.
     * @throws IllegalStateException If no consumer of states was added.
     * @throws IllegalArgumentException If the filters of the target are invalid.
     */
    @JvmName("toObserver")
    internal fun toObserver(consumingErrors: ConsumerErrorHandler<S>?): TargetObserver {
        check(observed) {
            "The target of the type `${type.name}` has no consumers of states." +
                    " Please call `observe()` for the target."
        }
        consumingErrors?.let { consumers.onConsumingError(it) }
        val target = builder.buildTarget()
        target.checkValid()
        return StateTargetObserver(
            target = target,
            type = type,
            observer = consumers.build().toObserver(),
            noLongerMatching = noLongerMatching.toList()
        )
    }
}
