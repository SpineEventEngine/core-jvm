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

import com.google.protobuf.Message
import io.spine.base.EntityState
import io.spine.base.EventMessage
import io.spine.core.Command
import io.spine.core.EventContext

/**
 * Subscribes to the outcome of a command: the events that originate from the command,
 * and the observed entity states.
 *
 * The subscription has one topic. Its targets are the [events], one target for each event
 * type, followed by the [states], in their order.
 *
 * @param command The command whose outcome to observe.
 * @param events The targets of the events. The subscription matches only the events
 *   that originate from the [command].
 * @param states The targets of the entity states.
 * @param consumingErrors The handler of errors thrown by the consumers of the targets,
 *   or `null` to log such errors.
 */
internal class EventsAfterCommand(
    private val command: Command,
    private val events: Collection<EventTarget<*>>,
    private val states: Collection<StateTarget<*>>,
    private val consumingErrors: ConsumerErrorHandler<Message>?
) {

    /**
     * Subscribes to the outcome of the command.
     *
     * @param client The client to subscribe with.
     * @param streamingErrors The handler of an error of the stream of updates,
     *   or `null` to log such an error.
     * @return The subscription to all the targets.
     * @throws io.grpc.StatusRuntimeException If the server refuses the subscription.
     */
    fun subscribe(client: Client, streamingErrors: ErrorHandler?): Subscription {
        val topics = client.requestOf(command.actor()).topic()
        val origin = EventFilter.eq(EventContext.Field.pastMessage(), command.asMessageOrigin())
        val observers = events.map { observerOf(it, origin) } + states.map { observerOf(it) }
        return client.subscriptions().subscribeTo(topics, observers, streamingErrors)
    }

    /**
     * Builds the observer of the events that match the given target and originate from
     * the command.
     */
    private fun <E : EventMessage> observerOf(
        target: EventTarget<E>,
        origin: EventFilter
    ): TargetObserver = target.where(origin).toObserver(consumingErrors?.narrow())

    /**
     * Builds the observer of the entity states that match the given target.
     */
    private fun <S : EntityState<*>> observerOf(target: StateTarget<S>): TargetObserver =
        target.toObserver(consumingErrors?.narrow())
}
