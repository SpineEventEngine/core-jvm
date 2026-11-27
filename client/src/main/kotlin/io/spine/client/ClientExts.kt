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
import io.spine.base.CommandMessage
import io.spine.base.EntityState
import io.spine.base.EventMessage
import io.spine.core.UserId
import java.util.function.Consumer

/**
 * Creates a new instance of [ActorRequestFactory] with the passed actor.
 */
public fun actorRequestFactory(actor: UserId): ActorRequestFactory =
    ActorRequestFactory.newBuilder()
        .setActor(actor)
        .build()

/**
 * Posts the given command, observing its outcome as the [configure] block requests.
 *
 * ```kotlin
 * val subscription = client.onBehalfOf(user).command(assignTask) {
 *     observe<TaskAssigned> { e -> show(e) }
 *     observe<Task> { it.byId(taskId).observe { task -> render(task) } }
 * }
 * ```
 *
 * @param message The command to post.
 * @param configure Configures the request: what to observe, and the error handlers.
 * @return The subscription to the outcome of the command, or `null` if the server
 *   refused the subscription or the command with an error case.
 * @throws IllegalStateException If the block requests nothing to observe.
 * @throws io.grpc.StatusRuntimeException If the subscription fails without an error case,
 *   or the command cannot be sent.
 * @see CommandRequest.post
 */
@JvmSynthetic
public fun ClientRequest.command(
    message: CommandMessage,
    configure: CommandRequest.() -> Unit
): Subscription? = command(message).apply(configure).post()

/**
 * Subscribes to the targets that the [configure] block adds.
 *
 * ```kotlin
 * val subscription = client.onBehalfOf(user).subscribe {
 *     event<TaskAssigned> { it.observe { e -> show(e) } }
 *     state<Task> { it.byId(taskId).observe { task -> render(task) } }
 * }
 * ```
 *
 * @param configure Configures the request: its targets, and the error handlers.
 * @return The subscription to all the targets.
 * @throws IllegalStateException If the block adds no targets, or a target has no consumers.
 * @throws io.grpc.StatusRuntimeException If the server refuses the subscription.
 * @see TopicRequest.post
 */
@JvmSynthetic
public fun ClientRequest.subscribe(configure: TopicRequest.() -> Unit): Subscription =
    subscribe().apply(configure).post()

/**
 * Adds the passed consumer to the subscribers of the events of the type [E].
 *
 * @see CommandRequest.observe
 */
@JvmSynthetic
@JvmName("observeEvent")
@CanIgnoreReturnValue
public inline fun <reified E : EventMessage> CommandRequest.observe(
    consumer: Consumer<E>
): CommandRequest = observe(E::class.java, consumer)

/**
 * Adds the passed consumer of event messages and their contexts to the subscribers of
 * the events of the type [E].
 *
 * @see CommandRequest.observe
 */
@JvmSynthetic
@JvmName("observeEventWithContext")
@CanIgnoreReturnValue
public inline fun <reified E : EventMessage> CommandRequest.observe(
    consumer: EventConsumer<E>
): CommandRequest = observe(E::class.java, consumer)

/**
 * Adds a target of the states of the entities of the type [S].
 *
 * @see CommandRequest.observe
 */
@JvmSynthetic
@JvmName("observeState")
@CanIgnoreReturnValue
public inline fun <reified S : EntityState<*>> CommandRequest.observe(
    configure: Consumer<StateTarget<S>>
): CommandRequest = observe(S::class.java, configure)

/**
 * Adds a target of the events of the type [E].
 *
 * @see TopicRequest.event
 */
@JvmSynthetic
@CanIgnoreReturnValue
public inline fun <reified E : EventMessage> TopicRequest.event(
    configure: Consumer<EventTarget<E>>
): TopicRequest = event(E::class.java, configure)

/**
 * Adds a target of the states of the entities of the type [S].
 *
 * @see TopicRequest.state
 */
@JvmSynthetic
@CanIgnoreReturnValue
public inline fun <reified S : EntityState<*>> TopicRequest.state(
    configure: Consumer<StateTarget<S>>
): TopicRequest = state(S::class.java, configure)
