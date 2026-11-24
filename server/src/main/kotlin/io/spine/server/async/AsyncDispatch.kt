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

import io.spine.server.BoundedContext
import io.spine.server.dispatch.DispatchOutcome
import io.spine.server.dispatch.DispatchOutcomes.sentToAsyncExecution
import io.spine.server.type.SignalEnvelope

/**
 * The part of an asynchronous handler that passes the signals dispatched to the handler
 * to the [AsyncExecution] of its Bounded Context.
 *
 * The four types of asynchronous handlers extend different base classes, so each of them
 * keeps an instance of this class instead of inheriting the behavior.
 */
internal class AsyncDispatch {

    /**
     * The execution of the context the handler is registered with.
     */
    @Volatile
    private var execution: AsyncExecution? = null

    /**
     * Takes the execution of the given context.
     *
     * A handler calls this method when it is registered with the context.
     */
    fun registerWith(context: BoundedContext) {
        execution = context.internalAccess().asyncExecution()
    }

    /**
     * Submits the handling of the signal to the execution, and tells the caller that
     * the signal is on its way to the handler.
     *
     * @param handler The handler the signal is dispatched to.
     * @param signal The signal to handle.
     * @param handling Passes the signal to the handler and posts the produced messages,
     *   as the synchronous counterpart of the handler does.
     * @throws IllegalStateException If the handler is not registered with a Bounded Context,
     *   or the execution is closed.
     */
    fun submit(
        handler: Any,
        signal: SignalEnvelope<*, *, *>,
        handling: Runnable
    ): DispatchOutcome {
        val execution = checkNotNull(execution) {
            "The handler `$handler` is not registered with a Bounded Context."
        }
        val invocation = AsyncInvocation(
            handlerClass = handler.javaClass,
            envelope = signal,
            handling = handling
        )
        execution.submit(invocation)
        return sentToAsyncExecution(signal)
    }
}
