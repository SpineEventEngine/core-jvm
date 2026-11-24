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

package io.spine.server.async

import com.google.errorprone.annotations.CanIgnoreReturnValue
import com.google.protobuf.Duration
import com.google.protobuf.util.Durations
import io.spine.server.ServerDsl

/**
 * Runs the work of the asynchronous handlers of a Bounded Context.
 *
 * When a signal is dispatched to an asynchronous handler, such as
 * an [AsyncAssignee][io.spine.server.command.AsyncAssignee], the handler does not handle it
 * right away. It [submits][submit] an [AsyncInvocation] to the execution of its Bounded Context,
 * and the dispatching returns. The execution then calls [AsyncInvocation.run] on a thread
 * of its choice.
 *
 * ## Configuring
 *
 * A Bounded Context has one execution. It is set with
 * [setAsyncExecution][io.spine.server.BoundedContextBuilder.setAsyncExecution].
 * If none is set, the builder of the context gives it the one built by [newBuilder] with
 * its default settings. Under the [Tests][io.spine.environment.Tests] environment,
 * the builder gives the [onCallingThread] execution instead, so that a test sees the results
 * of a handler as soon as the signal is posted.
 *
 * A context [closes][close] its execution when the context is closed, so two contexts
 * cannot use one instance.
 *
 * The execution built by [newBuilder] limits the number of invocations that are ongoing
 * at once for a handler class. In Kotlin, the [asyncExecution] function builds it:
 *
 * ```kotlin
 * val execution = asyncExecution {
 *     limit<AcmeRpcHandler>(2)
 * }
 * ```
 *
 * In Java:
 *
 * ```java
 * var execution = AsyncExecution.newBuilder()
 *         .setLimit(AcmeRpcHandler.class, 2)
 *         .build();
 * ```
 *
 * ## Implementing
 *
 * An application may give a context an execution of its own, which runs the invocations on
 * its threads or coroutines. In Kotlin, with coroutines, where a semaphore allows
 * two ongoing invocations:
 *
 * ```kotlin
 * val permits = Semaphore(2)
 * val execution = AsyncExecution { invocation ->
 *     scope.launch(Dispatchers.IO) {
 *         permits.withPermit { invocation.run() }
 *     }
 * }
 * ```
 *
 * In Java, with an executor:
 *
 * ```java
 * AsyncExecution execution = invocation -> executor.execute(invocation::run);
 * ```
 *
 * An execution written as a lambda does not wait for its invocations when the context is
 * closed. To wait for them, implement the interface in a class and override [close].
 *
 * [AsyncInvocation.run] handles the signal on the thread that calls it and returns when
 * the handling is over. See [AsyncInvocation] for how an execution counts
 * the ongoing invocations.
 *
 * ## What is not guaranteed
 *
 * The submitted invocations are kept in memory. If the JVM stops before an invocation
 * finishes, its signal is not handled, and nothing passes the signal to the handler again.
 * An application that needs more can keep the [signal][AsyncInvocation.signal] of each
 * submitted invocation in its own execution.
 */
public fun interface AsyncExecution : AutoCloseable {

    /**
     * Accepts the invocation to [run][AsyncInvocation.run] it outside the calling thread.
     *
     * The method is called on the thread that dispatches the signal, so it must return
     * promptly. An invocation that cannot be run yet waits within the execution.
     *
     * @throws IllegalStateException If [close] has been called.
     */
    public fun submit(invocation: AsyncInvocation)

    /**
     * Stops accepting invocations, and waits for the submitted ones to finish.
     *
     * A Bounded Context calls this method before it closes its buses, so that the handlers
     * that are still running can post their results.
     *
     * The default implementation does nothing. An execution that keeps threads or
     * invocations overrides it.
     */
    override fun close() {
        // Nothing to close by default.
    }

    /**
     * The builder of the execution that limits the number of ongoing invocations per
     * handler class.
     *
     * The execution runs the invocations on its own daemon threads. An invocation that would
     * exceed the limit of its handler class waits until another invocation of that class
     * finishes. The waiting invocations of a class are run in the order of submission, so
     * a class with the limit of one handles its signals one by one.
     *
     * When the execution is closed, it stops accepting invocations, and waits for
     * the submitted ones to finish. While it waits, it also refuses the invocations caused by
     * the handlers that finish. If the submitted invocations do not finish within
     * the [close timeout][setCloseTimeout], the execution interrupts the running ones, drops
     * the waiting ones, and logs a warning that tells how many invocations of each handler
     * class were lost.
     *
     * In Kotlin, the [asyncExecution] function creates a builder, passes it to a block,
     * and builds the execution.
     */
    @ServerDsl
    public class Builder internal constructor() {

        /**
         * The limits of the handler classes that have a limit of their own.
         */
        private val limits = mutableMapOf<Class<*>, Int>()

        /**
         * The number of invocations that can be ongoing at once for a handler class
         * that has no limit of its own.
         *
         * The default value is 8. The assigned value must be positive.
         *
         * This property is for the [asyncExecution] DSL. Java code calls [setLimit].
         */
        @get:JvmSynthetic
        @set:JvmSynthetic
        @set:JvmName("assignLimit")
        public var limit: Int = defaultLimit
            set(value) {
                requirePositive(value)
                field = value
            }

        /**
         * How long the execution waits, when it is closed, for the submitted invocations
         * to finish.
         *
         * The default value is 30 seconds. The assigned value cannot be negative.
         *
         * This property is for the [asyncExecution] DSL. Java code calls [setCloseTimeout].
         */
        @get:JvmSynthetic
        @set:JvmSynthetic
        @set:JvmName("assignCloseTimeout")
        public var closeTimeout: Duration = Durations.fromSeconds(defaultCloseTimeoutSeconds)
            set(value) {
                require(!Durations.isNegative(value)) {
                    "The close timeout cannot be negative." +
                            " Encountered: `${Durations.toString(value)}`."
                }
                field = value
            }

        /**
         * Sets the number of invocations that can be ongoing at once for the handlers of
         * the class [H].
         *
         * All the handlers of the class registered in the Bounded Context share the limit.
         *
         * This function is for the [asyncExecution] DSL. Java code calls [setLimit].
         *
         * @param H The class of asynchronous handlers. The limit is for the handlers of
         *   exactly this class.
         * @param max The number of invocations, which must be positive.
         */
        public inline fun <reified H : Any> limit(max: Int) {
            setLimit(H::class.java, max)
        }

        /**
         * Sets the number of invocations that can be ongoing at once for a handler class
         * that has no [limit of its own][setLimit].
         *
         * The default value is 8.
         *
         * @param max The number of invocations, which must be positive.
         * @return This builder, for call chaining.
         */
        @CanIgnoreReturnValue
        public fun setLimit(max: Int): Builder {
            limit = max
            return this
        }

        /**
         * Sets the number of invocations that can be ongoing at once for the given
         * handler class.
         *
         * All the handlers of the class registered in the Bounded Context share the limit.
         *
         * @param handlerClass The class of asynchronous handlers. The limit is for
         *   the handlers of exactly this class.
         * @param max The number of invocations, which must be positive.
         * @return This builder, for call chaining.
         */
        @CanIgnoreReturnValue
        public fun setLimit(handlerClass: Class<*>, max: Int): Builder {
            requirePositive(max)
            limits[handlerClass] = max
            return this
        }

        /**
         * Sets how long the execution waits, when it is closed, for the submitted
         * invocations to finish.
         *
         * The default value is 30 seconds.
         *
         * @param timeout The time to wait, which cannot be negative.
         * @return This builder, for call chaining.
         */
        @CanIgnoreReturnValue
        public fun setCloseTimeout(timeout: Duration): Builder {
            closeTimeout = timeout
            return this
        }

        /**
         * Creates the execution.
         */
        public fun build(): AsyncExecution =
            LimitingExecution(
                defaultLimit = limit,
                limits = limits.toMap(),
                closeTimeout = closeTimeout
            )

        private fun requirePositive(max: Int) {
            require(max > 0) {
                "The limit of ongoing invocations must be positive. Encountered: `$max`."
            }
        }

        private companion object {

            /**
             * The number of invocations that can be ongoing at once for a handler class,
             * unless the builder is told otherwise.
             */
            const val defaultLimit = 8

            /**
             * How many seconds an execution waits for its invocations when it is closed,
             * unless the builder is told otherwise.
             */
            const val defaultCloseTimeoutSeconds = 30L
        }
    }

    public companion object {

        /**
         * Creates a builder of the execution that runs the invocations on its own threads
         * and limits the number of ongoing invocations per handler class.
         *
         * @see asyncExecution
         */
        @JvmStatic
        public fun newBuilder(): Builder = Builder()

        /**
         * Obtains the execution that runs an invocation on the thread that submits it,
         * before [submit] returns.
         *
         * With this execution, an asynchronous handler works as a synchronous one.
         * A Bounded Context uses it under the [Tests][io.spine.environment.Tests] environment,
         * unless another execution is set for the context.
         */
        @JvmStatic
        public fun onCallingThread(): AsyncExecution = CallingThreadExecution
    }
}

/**
 * Runs an invocation on the thread that submits it.
 */
private object CallingThreadExecution : AsyncExecution {

    override fun submit(invocation: AsyncInvocation) {
        invocation.run()
    }
}
