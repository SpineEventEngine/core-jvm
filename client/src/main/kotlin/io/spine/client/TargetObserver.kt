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

import io.grpc.stub.StreamObserver
import io.spine.base.EntityState
import io.spine.client.EntityStateUpdate.KindCase.KIND_NOT_SET
import io.spine.client.EntityStateUpdate.KindCase.NO_LONGER_MATCHING
import io.spine.client.EntityStateUpdate.KindCase.STATE
import io.spine.core.Event
import io.spine.logging.WithLogging
import io.spine.protobuf.AnyPacker

/**
 * Observes the updates of one target of a subscription topic, and passes their messages
 * to the consumers of the target.
 *
 * [RoutingObserver] passes each update of a subscription to the observer of
 * the target that the update names.
 */
internal sealed class TargetObserver(

    /**
     * The target of the topic.
     */
    val target: Target
) {

    /**
     * Passes the messages of the given update to the consumers of the target.
     *
     * @throws IllegalStateException If the update carries messages of another kind than
     *   the target, events instead of entity states, or vice versa.
     */
    abstract fun onUpdate(update: SubscriptionUpdate)

    /**
     * Ensures that the given update carries the kind of messages that the target expects.
     */
    protected fun checkCase(update: SubscriptionUpdate, expected: SubscriptionUpdate.UpdateCase) {
        check(update.updateCase == expected) {
            "The target of the type `${target.type}` expects `$expected`," +
                    " but the update carries `${update.updateCase}`."
        }
    }
}

/**
 * Passes the events that match an event target to its consumers.
 */
internal class EventTargetObserver(
    target: Target,
    private val observer: StreamObserver<Event>
) : TargetObserver(target) {

    override fun onUpdate(update: SubscriptionUpdate) {
        checkCase(update, SubscriptionUpdate.UpdateCase.EVENT_UPDATES)
        update.eventUpdates.eventList.forEach(observer::onNext)
    }
}

/**
 * Passes the entity states that match an entity target to its consumers.
 *
 * The IDs of the entities that no longer match the target go to
 * the [noLongerMatching] consumers. If such a consumer throws, the observer logs the error,
 * and goes on, so that one consumer does not stop the updates of the whole subscription.
 *
 * @param S The type of the entity states.
 */
internal class StateTargetObserver<S : EntityState<*>>(
    target: Target,
    private val type: Class<S>,
    private val observer: StreamObserver<S>,
    private val noLongerMatching: List<NoLongerMatchingConsumer<*>>
) : TargetObserver(target), WithLogging {

    override fun onUpdate(update: SubscriptionUpdate) {
        checkCase(update, SubscriptionUpdate.UpdateCase.ENTITY_UPDATES)
        update.entityUpdates.updateList.forEach { entityUpdate ->
            when (entityUpdate.kindCase) {
                STATE -> observer.onNext(AnyPacker.unpack(entityUpdate.state, type))
                NO_LONGER_MATCHING -> noLongerMatching.forEach { reportTo(it, entityUpdate) }
                KIND_NOT_SET -> Unit
            }
        }
    }

    /**
     * Passes the ID of the entity that no longer matches the target to the given consumer,
     * logging an error that the consumer throws.
     *
     * As the consumers of states do, it logs any `Throwable` of
     * the consumer, such as an `AssertionError`. A `VirtualMachineError` propagates.
     */
    @Suppress("TooGenericExceptionCaught") // The consumer is the code of the client.
    private fun reportTo(consumer: NoLongerMatchingConsumer<*>, update: EntityStateUpdate) {
        try {
            consumer.accept(update.id)
        } catch (e: VirtualMachineError) {
            throw e
        } catch (e: Throwable) {
            logger.atError().withCause(e).log {
                "The consumer of the entities of the type `${target.type}` that no longer" +
                        " match the target could not handle the entity update."
            }
        }
    }
}
