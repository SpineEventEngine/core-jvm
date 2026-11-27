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
import io.grpc.StatusRuntimeException
import io.spine.annotation.VisibleForTesting
import io.spine.base.CommandMessage
import io.spine.base.EntityState
import io.spine.base.EventMessage
import io.spine.core.Command
import io.spine.core.ErrorCase
import io.spine.core.Status
import io.spine.core.Status.StatusCase.ERROR_CASE
import io.spine.core.Status.StatusCase.OK
import io.spine.core.Status.StatusCase.STATUS_NOT_SET
import io.spine.grpc.StreamObservers.errorCaseOf
import io.spine.logging.WithLogging
import io.spine.type.shortDebugString
import java.util.function.Consumer

/**
 * Allows posting a command, and observing its outcome: the events that originate from
 * the command, and the states of the entities that the request names.
 *
 * In Kotlin:
 * ```kotlin
 * val subscription = client.onBehalfOf(user).command(assignTask) {
 *     observe<TaskAssigned> { e -> show(e) }
 *     observe<TaskAlreadyAssigned> { rejection, context -> report(rejection, context) }
 *     observe<Task> { it.byId(taskId).observe { task -> render(task) } }
 * }
 * ```
 *
 * In Java:
 * ```java
 * Subscription subscription = client.onBehalfOf(user)
 *         .command(assignTask)
 *         .observe(TaskAssigned.class, e -> show(e))
 *         .observe(TaskAlreadyAssigned.class, (rejection, context) -> report(rejection, context))
 *         .observeState(Task.class, t -> t.byId(taskId).observe(this::render))
 *         .post();
 * ```
 *
 * The request makes one subscription for the whole outcome. The subscription obtained from
 * [post] should be [cancelled][Subscriptions.cancel] to preserve both client-side and
 * server-side resources. The moment of cancelling the subscription depends on the nature of
 * the posted command and the outcome expected by the client application.
 */
public class CommandRequest internal constructor(
    parent: ClientRequest,

    /**
     * The message of the command to post.
     */
    @get:VisibleForTesting
    @get:JvmName("message")
    internal val message: CommandMessage
) : ClientRequestBase(parent), WithLogging {

    /**
     * The factory of the topics of the observed entity states.
     */
    private val topics: TopicFactory = client().requestOf(user()).topic()

    /**
     * The targets of the observed events, one for each event type.
     */
    private val events = linkedMapOf<Class<out EventMessage>, EventTarget<*>>()

    /**
     * The targets of the observed entity states, in the order of the requests.
     */
    private val states = mutableListOf<StateTarget<*>>()

    /**
     * The handler of errors thrown by the consumers of events and entity states.
     */
    private var consumingErrors: ConsumerErrorHandler<Message>? = null

    /**
     * Adds the passed consumer to the subscribers of the event of the passed type.
     *
     * The consumers of one event type share the delivery of these events, so each event
     * comes to each consumer once.
     *
     * @param E The type of the event.
     * @param type The class of the event messages to be received by the consumer.
     * @param consumer The consumer.
     * @return This request, for call chaining.
     * @throws io.spine.type.UnpublishedLanguageException If the event type is not a part of
     *   the published language.
     */
    @CanIgnoreReturnValue
    public fun <E : EventMessage> observe(type: Class<E>, consumer: Consumer<E>): CommandRequest {
        eventTarget(type).observe(consumer)
        return this
    }

    /**
     * Adds the passed event consumer to the subscribers of the event of the passed type.
     *
     * The consumers of one event type share the delivery of these events, so each event
     * comes to each consumer once.
     *
     * @param E The type of the event.
     * @param type The class of the event messages to be received by the consumer.
     * @param consumer The consumer of the event message and its context.
     * @return This request, for call chaining.
     * @throws io.spine.type.UnpublishedLanguageException If the event type is not a part of
     *   the published language.
     */
    @CanIgnoreReturnValue
    public fun <E : EventMessage> observe(
        type: Class<E>,
        consumer: EventConsumer<E>
    ): CommandRequest {
        eventTarget(type).observe(consumer)
        return this
    }

    /**
     * Adds a target of the states of the entities of the passed type.
     *
     * An entity state does not refer to the command that changed it, so a target without
     * [IDs][StateTarget.byId] or [filters][StateTarget.where] gets every change of
     * the entities of this type. To observe the entities that the command changes,
     * name their IDs.
     *
     * In Java, this method is called `observeState()`.
     *
     * @param S The type of the entity states.
     * @param type The class of the entity states.
     * @param configure Configures the target: the entities that match it, and the consumers
     *   of their states. The target must have at least one consumer of states.
     * @return This request, for call chaining.
     * @throws io.spine.type.UnpublishedLanguageException If the type is not a part of
     *   the published language.
     */
    @JvmName("observeState")
    @CanIgnoreReturnValue
    public fun <S : EntityState<*>> observe(
        type: Class<S>,
        configure: Consumer<StateTarget<S>>
    ): CommandRequest {
        val target = StateTarget(type, topics.select(type))
        // The block may add targets too. This one goes before them, in the order of the requests.
        val position = states.size
        configure.accept(target)
        states.add(position, target)
        return this
    }

    /**
     * Assigns a handler for errors that occur when delivering the outcome of the command.
     *
     * If such an error occurs, no more events or entity states will be delivered to
     * the consumers.
     *
     * @return This request, for call chaining.
     */
    @CanIgnoreReturnValue
    override fun onStreamingError(handler: ErrorHandler): CommandRequest {
        super.onStreamingError(handler)
        return this
    }

    /**
     * Assigns a handler for errors that occur in the consumers of events and entity states.
     *
     * After the passed handler is called, the remaining consumers will get the messages
     * as usual. If not specified, the default implementation simply logs the error.
     *
     * @return This request, for call chaining.
     */
    @CanIgnoreReturnValue
    public fun onConsumingError(handler: ConsumerErrorHandler<Message>): CommandRequest {
        consumingErrors = handler
        return this
    }

    /**
     * Assigns a handler for the error case that the server returns in response to posting
     * a command, such as `INVALID_REQUEST` for an invalid command, or in response to
     * subscribing to the outcome of the command.
     *
     * @return This request, for call chaining.
     */
    @CanIgnoreReturnValue
    override fun onServerError(handler: ServerErrorHandler): CommandRequest {
        super.onServerError(handler)
        return this
    }

    /**
     * Subscribes the consumers to the outcome of the command, and then sends the command
     * to the server.
     *
     * The method makes one subscription to all the [observed][observe] events and
     * entity states. The returned subscription should be [cancelled][Subscriptions.cancel]
     * after the requesting code receives the expected outcome, or after a reasonable timeout.
     *
     * If the server refuses the subscription with an error case, the method reports the error
     * case to the [server error handler][onServerError], does not post the command, and
     * returns `null`. If the subscription fails without an error case, for example, because
     * the server cannot be reached, the method does not post the command, and rethrows
     * the error.
     *
     * The server may also refuse the command, for example, because of a validation error.
     * Then the method cancels the subscription, reports the error case to the server error
     * handler, and returns `null`. If the command cannot be sent at all, the method cancels
     * the subscription, and rethrows the error.
     *
     * Subscriptions should be cancelled to free up client and server resources required for
     * their maintenance. The returned subscription cannot be cancelled automatically, because
     * subscriptions are asynchronous and infinite by nature. Even though the client knows
     * the types of the expected outcome, only the client code "knows" how many messages it
     * expects. Also, some messages may not arrive because of communication or business logic
     * reasons. That's why the client code should cancel the returned subscription when it
     * no longer needs it.
     *
     * @return The subscription to the outcome of the command if the command was posted,
     *   or `null` if the server refused the subscription or the command with an error case.
     * @throws IllegalStateException If nothing was requested to observe, or if a target of
     *   entity states has no consumers.
     * @throws io.grpc.StatusRuntimeException If the subscription fails without an error case,
     *   or the command cannot be sent.
     * @see postAndForget
     */
    public fun post(): Subscription? = PostOperation().perform()

    /**
     * Posts the command without subscribing to its outcome.
     *
     * @throws IllegalStateException If events or entity states were requested to
     *   [observe] in the configuration chain of the request.
     * @see post
     */
    public fun postAndForget() {
        PostOperation().performWithoutSubscription()
    }

    /**
     * Obtains the target of the events of the given type, creating it on the first request.
     */
    private fun <E : EventMessage> eventTarget(type: Class<E>): EventTarget<E> {
        val target = events.getOrPut(type) { EventTarget(type, topics.select(type)) }
        @Suppress("UNCHECKED_CAST") // The map keeps the target of each type by its type.
        return target as EventTarget<E>
    }

    /**
     * Tells if events or entity states were requested to observe.
     */
    private fun observesOutcome(): Boolean = events.isNotEmpty() || states.isNotEmpty()

    /**
     * Method object for posting a command.
     */
    private inner class PostOperation {

        private val command: Command = client().requestOf(user()).command().create(message)
        private var subscription: Subscription? = null

        fun perform(): Subscription? {
            check(observesOutcome()) {
                "Nothing to observe was requested prior to calling `post()`." +
                        " To receive the outcome of the command, please call `observe()`" +
                        " before `post()`. Or, if you subscribe to events or entity states" +
                        " elsewhere, please use `postAndForget()`."
            }
            val outcome = EventsAfterCommand(
                command = command,
                events = events.values,
                states = states,
                consumingErrors = consumingErrors
            )
            try {
                subscription = outcome.subscribe(client(), streamingErrorHandler())
            } catch (e: StatusRuntimeException) {
                val errorCase = errorCaseOf(e).orElseThrow { e }
                report(errorCase)
                return null
            }
            val ack = try {
                client().post(command)
            } catch (e: StatusRuntimeException) {
                cancelSubscription()
                throw e
            }
            return handle(ack.status)
        }

        fun performWithoutSubscription() {
            check(!observesOutcome()) {
                "Subscriptions to the outcome of the command were requested." +
                        " Please call `post()` instead."
            }
            val ack = client().post(command)
            handle(ack.status)
        }

        private fun handle(status: Status): Subscription? = when (status.statusCase) {
            OK -> subscription
            ERROR_CASE -> {
                cancelSubscription()
                report(status.errorCase)
                null
            }
            STATUS_NOT_SET -> {
                cancelSubscription()
                error(
                    "Cannot handle ack status `${status.statusCase}`" +
                            " when posting the command `${command.shortDebugString()}`."
                )
            }
        }

        /**
         * Cancels the subscription to the outcome of the command, if it was made,
         * because the command was not posted.
         */
        private fun cancelSubscription() {
            subscription?.let { client().subscriptions().cancel(it) }
        }

        private fun report(errorCase: ErrorCase) {
            val handler = serverErrorHandler() ?: LoggingServerErrorHandler(
                logger,
                "Unable to post the command `%s`. Returned error case: `%s`."
            )
            handler.accept(command, errorCase)
        }
    }
}
